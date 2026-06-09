package com.higlyph.app.relay

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.gson.Gson
import com.higlyph.app.MainActivitySimple
import com.higlyph.app.R
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import com.higlyph.app.api.HardwareApiHandler
import com.higlyph.app.toys.LiveGlyphPreview
import com.higlyph.app.toys.LiveGlyphFrame
import com.higlyph.app.serialization.GlyphImageSerializer

/**
 * Service that maintains WebSocket connection to relay server.
 * Receives messages from relay and forwards to local hardware API.
 */
class RelayClientService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private val gson = Gson()
    private lateinit var apiHandler: HardwareApiHandler

    private var webSocketJob: Job? = null
    private var reconnectJob: Job? = null
    private var toyJob: Job? = null

    @Volatile private var relaySession: WebSocketSession? = null

    private val glyphListener = LiveGlyphPreview.Listener { frame: LiveGlyphFrame? ->
        if (frame == null) return@Listener
        val pixels = GlyphImageSerializer.pixelGridToBinary(frame.grid) ?: return@Listener
        val session = relaySession ?: return@Listener
        serviceScope.launch {
            try {
                session.send(Frame.Text("""{"type":"frame","pixels":"$pixels"}"""))
            } catch (e: Exception) { /* session closed */ }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "RelayClient service created")
        apiHandler = HardwareApiHandler(applicationContext)
        createNotificationChannel()
        startWebSocketConnection()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "RelayClient service started")
        if (webSocketJob == null || webSocketJob?.isActive != true) {
            startWebSocketConnection()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "RelayClient service destroyed")
        stopWebSocketConnection()
        serviceJob.cancel()
        setStatus(STATUS_DISCONNECTED)
    }

    private fun setStatus(status: String) {
        getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
            .edit().putString(PREF_STATUS, status).apply()
        Log.d(TAG, "Status: $status")
    }

    private fun startWebSocketConnection() {
        val prefs = getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
        val serverUrl = prefs.getString(PREF_SERVER_URL, null)
        val enabled = prefs.getBoolean(PREF_ENABLED, false)

        Log.d(TAG, "startWebSocketConnection: enabled=$enabled, serverUrl=$serverUrl")

        if (!enabled || serverUrl.isNullOrBlank()) {
            Log.d(TAG, "Relay client disabled or no server URL configured")
            setStatus(STATUS_DISCONNECTED)
            return
        }

        stopWebSocketConnection()
        setStatus(STATUS_CONNECTING)

        Log.d(TAG, "Launching WebSocket connection to: $serverUrl")
        webSocketJob = serviceScope.launch {
            connectToRelay(serverUrl)
        }
    }

    private suspend fun connectToRelay(serverUrl: String) {
        Log.d(TAG, "connectToRelay called with: $serverUrl")

        val client = HttpClient(OkHttp) {
            install(WebSockets)
        }

        try {
            val wsUrl = serverUrl.replace("http://", "ws://").replace("https://", "wss://")
            val prefs = getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
            val phoneId = prefs.getString(PREF_PHONE_ID, "")
            val wsUrlWithId = if (!phoneId.isNullOrBlank()) "$wsUrl?id=$phoneId" else wsUrl
            Log.d(TAG, "Attempting WebSocket connection to: $wsUrlWithId")

            client.webSocket(wsUrlWithId) {
                relaySession = this
                LiveGlyphPreview.addListener(glyphListener)
                try {
                    Log.i(TAG, "Connected to relay server")
                    setStatus(STATUS_CONNECTED)

                    // Send AI config immediately after connecting
                    val aiEnabled = prefs.getBoolean(PREF_AI_ENABLED, false)
                    val aiProvider = prefs.getString(PREF_AI_PROVIDER, "openai") ?: "openai"
                    val aiKey = (prefs.getString(PREF_AI_KEY, "") ?: "")
                        .replace("\\", "\\\\").replace("\"", "\\\"")
                    send(Frame.Text("""{"type":"ai_config","enabled":$aiEnabled,"provider":"$aiProvider","key":"$aiKey"}"""))

                    // Receive messages
                    for (frame in incoming) {
                        if (frame is Frame.Text) handleMessage(frame.readText())
                    }
                } finally {
                    relaySession = null
                    LiveGlyphPreview.removeListener(glyphListener)
                    stopToy()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "WebSocket error: ${e.message}", e)
        } finally {
            client.close()
            val prefs = getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
            if (prefs.getBoolean(PREF_ENABLED, false)) {
                setStatus(STATUS_FAILED)
                scheduleReconnect()
            } else {
                setStatus(STATUS_DISCONNECTED)
            }
        }
    }

    private val recentAcks = ArrayDeque<String>(20)

    private fun alreadyProcessed(id: String?): Boolean {
        if (id == null) return false
        return id in recentAcks
    }

    private fun markProcessed(id: String?) {
        if (id == null) return
        if (recentAcks.size >= 20) recentAcks.removeFirst()
        recentAcks.addLast(id)
    }

    private fun sendAck(id: String?) {
        if (id == null) return
        val session = relaySession ?: return
        serviceScope.launch {
            try { session.send(Frame.Text("""{"type":"ack","id":"$id"}""")) } catch (_: Exception) {}
        }
    }

    private fun handleMessage(messageJson: String) {
        try {
            val message = gson.fromJson(messageJson, RelayMessage::class.java)
            Log.d(TAG, "Received message: ${message.type}")

            when (message.type) {
                "message" -> {
                    if (alreadyProcessed(message.id)) { sendAck(message.id); return }
                    markProcessed(message.id)
                    forwardToHardwareApi(message.text ?: "", message.speed ?: 20)
                    showMessageNotification(message.text ?: "")
                    sendAck(message.id)
                }
                "draw" -> {
                    if (alreadyProcessed(message.id)) { sendAck(message.id); return }
                    markProcessed(message.id)
                    message.pixels?.let { forwardFrameToHardwareApi(it) }
                    showMessageNotification("✦ drawing received")
                    sendAck(message.id)
                }
                "toy" -> {
                    if (message.action == "start") {
                        showMessageNotification("▶ toy started: ${message.toyId ?: "unknown"}")
                        startToy(message.toyId ?: "")
                    } else {
                        stopToy()
                    }
                }
                "pong" -> { }
                else -> Log.w(TAG, "Unknown message type: ${message.type}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling message: ${e.message}", e)
        }
    }

    private fun forwardToHardwareApi(text: String, speed: Int) {
        serviceScope.launch { apiHandler.startScrollingText(text, speed, 0, 0) }
    }

    private fun forwardFrameToHardwareApi(pixels: String) {
        serviceScope.launch { apiHandler.displayFrame(pixels, 255) }
    }

    private fun forwardToyFrameToHardwareApi(pixels: String) {
        apiHandler.displayFrame(pixels, 255)
    }

    private fun stopToy() {
        toyJob?.cancel()
        toyJob = null
    }

    private fun startToy(id: String) {
        stopToy()
        toyJob = serviceScope.launch {
            when (id) {
                "gol"      -> runGoL()
                "rain"     -> runRain()
                "brain"   -> runBrainsBrain()
                "cyclic"  -> runCyclic()
                "ising"    -> runIsing()
                "ants"     -> runAnts()
                "daynight" -> runDayNight()
                "wator"    -> runWator()
            }
        }
    }

    private suspend fun runGoL() {
        val rng = java.util.Random()
        fun newGrid() = BooleanArray(169) { i -> !masked(i / 13, i % 13) && rng.nextDouble() > 0.62 }
        var grid = newGrid()
        var prevGrid = BooleanArray(169)
        while (true) {
            val next = BooleanArray(169)
            for (r in 0..12) for (c in 0..12) {
                if (masked(r, c)) continue
                var n = 0
                for (dr in -1..1) for (dc in -1..1) {
                    if (dr == 0 && dc == 0) continue
                    val nr = r + dr; val nc = c + dc
                    if (nr < 0 || nr > 12 || nc < 0 || nc > 12 || masked(nr, nc)) continue
                    if (grid[nr * 13 + nc]) n++
                }
                val alive = grid[r * 13 + c]
                next[r * 13 + c] = if (alive) n == 2 || n == 3 else n == 3
            }
            val aliveCount = next.count { it }
            if (aliveCount < 4 || next.contentEquals(grid) || next.contentEquals(prevGrid)) {
                prevGrid = BooleanArray(169)
                grid = newGrid()
                delay(300)
                continue
            }
            prevGrid = grid
            grid = next
            forwardToyFrameToHardwareApi(grid.joinToString("") { if (it) "1" else "0" })
            delay(300)
        }
    }

    private suspend fun runRain() {
        val rng = java.util.Random()
        data class Drop(var pos: Int, val tail: Int, var tick: Int, val speed: Int)
        val drops = Array(13) { Drop(rng.nextInt(13), rng.nextInt(3) + 2, 0, if (rng.nextDouble() > 0.4) 1 else 2) }
        while (true) {
            val grid = BooleanArray(169)
            for (c in 0..12) {
                val d = drops[c]
                if (++d.tick >= d.speed) { d.tick = 0; d.pos = (d.pos + 1) % 13 }
                for (t in 0 until d.tail) {
                    val r = (d.pos - t + 13) % 13
                    if (!masked(r, c)) grid[r * 13 + c] = true
                }
            }
            forwardToyFrameToHardwareApi(grid.joinToString("") { if (it) "1" else "0" })
            delay(100)
        }
    }

    private suspend fun runBrainsBrain() {
        val rng = java.util.Random()
        fun newGrid() = IntArray(169) { i ->
            if (masked(i / 13, i % 13)) 0 else if (rng.nextDouble() < 0.22) 1 else 0
        }
        var grid = newGrid()
        while (true) {
            val next = IntArray(169)
            for (r in 0..12) for (c in 0..12) {
                if (masked(r, c)) continue
                when (grid[r * 13 + c]) {
                    1 -> next[r * 13 + c] = 2
                    2 -> next[r * 13 + c] = 0
                    0 -> {
                        var on = 0
                        for (dr in -1..1) for (dc in -1..1) {
                            if (dr == 0 && dc == 0) continue
                            val nr = r + dr; val nc = c + dc
                            if (nr < 0 || nr > 12 || nc < 0 || nc > 12 || masked(nr, nc)) continue
                            if (grid[nr * 13 + nc] == 1) on++
                        }
                        if (on == 2) next[r * 13 + c] = 1
                    }
                }
            }
            if (next.none { it == 1 }) { grid = newGrid(); delay(200); continue }
            grid = next
            forwardToyFrameToHardwareApi(grid.joinToString("") { if (it > 0) "1" else "0" })
            delay(150)
        }
    }

    private suspend fun runCyclic() {
        val rng = java.util.Random()
        val states = 5
        fun newGrid() = IntArray(169) { i ->
            if (masked(i / 13, i % 13)) 0 else rng.nextInt(states)
        }
        var grid = newGrid()
        while (true) {
            val next = grid.copyOf()
            for (r in 0..12) for (c in 0..12) {
                if (masked(r, c)) continue
                val ns = (grid[r * 13 + c] + 1) % states
                for (dr in -1..1) for (dc in -1..1) {
                    if (dr == 0 && dc == 0) continue
                    val nr = r + dr; val nc = c + dc
                    if (nr < 0 || nr > 12 || nc < 0 || nc > 12 || masked(nr, nc)) continue
                    if (grid[nr * 13 + nc] == ns) { next[r * 13 + c] = ns; break }
                }
            }
            if (next.contentEquals(grid) || next.filterIndexed { i, _ -> !masked(i / 13, i % 13) }.toSet().size <= 1) {
                grid = newGrid(); delay(200); continue
            }
            grid = next
            forwardToyFrameToHardwareApi(grid.joinToString("") { if (it == 0) "1" else "0" })
            delay(180)
        }
    }

    private suspend fun runIsing() {
        val rng = java.util.Random()
        val spins = IntArray(169) { i ->
            if (masked(i / 13, i % 13)) 0 else if (rng.nextBoolean()) 1 else -1
        }
        var t = 0.0
        while (true) {
            val temp = 2.5 + 1.0 * kotlin.math.sin(t)
            t += 0.025
            repeat(300) {
                val idx = rng.nextInt(169)
                val r = idx / 13; val c = idx % 13
                if (masked(r, c)) return@repeat
                var nb = 0
                if (r > 0  && !masked(r - 1, c)) nb += spins[(r - 1) * 13 + c]
                if (r < 12 && !masked(r + 1, c)) nb += spins[(r + 1) * 13 + c]
                if (c > 0  && !masked(r, c - 1)) nb += spins[r * 13 + c - 1]
                if (c < 12 && !masked(r, c + 1)) nb += spins[r * 13 + c + 1]
                val dE = 2.0 * spins[idx] * nb
                if (dE <= 0.0 || rng.nextDouble() < kotlin.math.exp(-dE / temp)) {
                    spins[idx] = -spins[idx]
                }
            }
            forwardToyFrameToHardwareApi(spins.joinToString("") { if (it > 0) "1" else "0" })
            delay(80)
        }
    }

    private suspend fun runAnts() {
        val rng = java.util.Random()
        fun dx(d: Int) = when (d) { 1 -> 1; 3 -> -1; else -> 0 }
        fun dy(d: Int) = when (d) { 2 -> 1; 0 -> -1; else -> 0 }
        while (true) {
            val grid = IntArray(169)
            val startA = VALID_CELLS[rng.nextInt(VALID_CELLS.size)]
            var ax = startA % 13; var ay = startA / 13; var aDir = rng.nextInt(4)
            var startB: Int
            do { startB = VALID_CELLS[rng.nextInt(VALID_CELLS.size)] } while (startB == startA)
            var bx = startB % 13; var by = startB / 13; var bDir = (aDir + 2) % 4
            val recentFrames = ArrayDeque<String>()
            var cycled = false
            while (!cycled) {
                repeat(5) {
                    // Ant A: standard rules — dark→turn right, light→turn left
                    if (!masked(ay, ax)) {
                        val ai = ay * 13 + ax
                        val desired = if (grid[ai] == 0) (aDir + 1) % 4 else (aDir + 3) % 4
                        for (t in 0..3) {
                            val d = (desired + t) % 4
                            val nx = ((ax + dx(d)) + 13) % 13
                            val ny = ((ay + dy(d)) + 13) % 13
                            if (!masked(ny, nx)) { aDir = d; grid[ai] = 1 - grid[ai]; ax = nx; ay = ny; break }
                        }
                    }
                    // Ant B: mirror rules — dark→turn left, light→turn right
                    if (!masked(by, bx)) {
                        val bi = by * 13 + bx
                        val desired = if (grid[bi] == 0) (bDir + 3) % 4 else (bDir + 1) % 4
                        for (t in 0..3) {
                            val d = (desired + t) % 4
                            val nx = ((bx + dx(d)) + 13) % 13
                            val ny = ((by + dy(d)) + 13) % 13
                            if (!masked(ny, nx)) { bDir = d; grid[bi] = 1 - grid[bi]; bx = nx; by = ny; break }
                        }
                    }
                }
                val pixels = grid.joinToString("") { "$it" }
                if (recentFrames.contains(pixels)) {
                    cycled = true
                } else {
                    recentFrames.addLast(pixels)
                    if (recentFrames.size > 12) recentFrames.removeFirst()
                    forwardToyFrameToHardwareApi(pixels)
                    delay(80)
                }
            }
        }
    }

    private suspend fun runDayNight() {
        val rng = java.util.Random()
        val nValid = VALID_CELLS.size
        fun newGrid() = BooleanArray(169) { i -> !masked(i / 13, i % 13) && rng.nextDouble() > 0.5 }
        var grid = newGrid()
        var prev = BooleanArray(169)
        while (true) {
            val next = BooleanArray(169)
            for (r in 0..12) for (c in 0..12) {
                if (masked(r, c)) continue
                var n = 0
                for (dr in -1..1) for (dc in -1..1) {
                    if (dr == 0 && dc == 0) continue
                    val nr = r + dr; val nc = c + dc
                    if (nr < 0 || nr > 12 || nc < 0 || nc > 12 || masked(nr, nc)) continue
                    if (grid[nr * 13 + nc]) n++
                }
                val alive = grid[r * 13 + c]
                next[r * 13 + c] = if (alive) n == 3 || n == 4 || n == 6 || n == 7 || n == 8
                                   else       n == 3 || n == 6 || n == 7 || n == 8
            }
            val alive = next.count { it }
            if (alive < nValid / 10 || alive > nValid * 9 / 10 || next.contentEquals(grid) || next.contentEquals(prev)) {
                prev = BooleanArray(169)
                grid = newGrid()
                delay(300)
                continue
            }
            prev = grid
            grid = next
            forwardToyFrameToHardwareApi(grid.joinToString("") { if (it) "1" else "0" })
            delay(180)
        }
    }

    private suspend fun runWator() {
        val rng = java.util.Random()
        val FISH_BREED = 3; val SHARK_BREED = 6; val SHARK_STARVE = 3
        var grid = IntArray(169)
        var age = IntArray(169)
        var hunger = IntArray(169)
        fun reset() {
            grid = IntArray(169); age = IntArray(169); hunger = IntArray(169)
            val cells = VALID_CELLS.shuffled(rng)
            cells.take(40).forEach { grid[it] = 1 }
            cells.drop(40).take(6).forEach { grid[it] = 2 }
        }
        fun nbrs(i: Int): List<Int> {
            val r = i / 13; val c = i % 13
            val result = mutableListOf<Int>()
            for (dr in -1..1) for (dc in -1..1) {
                if (dr == 0 && dc == 0) continue
                val nr = r + dr; val nc = c + dc
                if (nr in 0..12 && nc in 0..12 && !masked(nr, nc)) result.add(nr * 13 + nc)
            }
            return result
        }
        reset()
        while (true) {
            val ng = grid.copyOf()
            val na = age.copyOf()
            val nh = hunger.copyOf()
            val done = BooleanArray(169)
            for (i in VALID_CELLS.shuffled(rng)) {
                if (done[i]) continue
                when (grid[i]) {
                    2 -> { // shark
                        nh[i]++
                        if (nh[i] > SHARK_STARVE) { ng[i] = 0; na[i] = 0; nh[i] = 0; done[i] = true; continue }
                        val prey = nbrs(i).filter { ng[it] == 1 }
                        val free = nbrs(i).filter { ng[it] == 0 }
                        val target = if (prey.isNotEmpty()) prey[rng.nextInt(prey.size)].also { nh[i] = 0 }
                                     else if (free.isNotEmpty()) free[rng.nextInt(free.size)]
                                     else { done[i] = true; continue }
                        na[i]++
                        ng[target] = 2; na[target] = na[i]; nh[target] = nh[i]; done[target] = true
                        if (na[i] >= SHARK_BREED) { ng[i] = 2; na[i] = 0; nh[i] = 0 } else ng[i] = 0
                        done[i] = true
                    }
                    1 -> { // fish
                        if (ng[i] != 1) { done[i] = true; continue }
                        val free = nbrs(i).filter { ng[it] == 0 }
                        na[i]++
                        done[i] = true
                        if (free.isEmpty()) continue
                        val target = free[rng.nextInt(free.size)]
                        ng[target] = 1; na[target] = na[i]; done[target] = true
                        if (na[i] >= FISH_BREED) { ng[i] = 1; na[i] = 0 } else ng[i] = 0
                    }
                }
            }
            grid = ng; age = na; hunger = nh
            if (grid.count { it == 1 } == 0 || grid.count { it == 2 } == 0) {
                delay(400); reset(); continue
            }
            forwardToyFrameToHardwareApi(grid.joinToString("") { if (it > 0) "1" else "0" })
            delay(250)
        }
    }

    private fun stopWebSocketConnection() {
        webSocketJob?.cancel()
        webSocketJob = null
        reconnectJob?.cancel()
        reconnectJob = null
    }

    private fun scheduleReconnect() {
        reconnectJob?.cancel()
        reconnectJob = serviceScope.launch {
            Log.d(TAG, "Reconnecting in 5 seconds...")
            delay(5_000)
            startWebSocketConnection()
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "Messages",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply { description = "Incoming glyph messages" }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun showMessageNotification(text: String) {
        val prefs = getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(PREF_NOTIFICATIONS_ENABLED, true)) return
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivitySimple::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("hi! glyph")
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(openIntent)
            .setAutoCancel(true)
            .build()
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
    }

    companion object {
        private const val TAG = "RelayClientService"
        const val NOTIFICATION_CHANNEL_ID = "hi_glyph_messages"
        private const val NOTIFICATION_ID = 42

        const val PREF_FILE = "relay_client_prefs"
        const val PREF_SERVER_URL = "server_url"
        const val PREF_ENABLED = "enabled"
        const val PREF_STATUS = "status"
        const val PREF_PHONE_ID = "phone_id"

        const val STATUS_DISCONNECTED = "disconnected"
        const val STATUS_CONNECTING = "connecting"
        const val STATUS_CONNECTED = "connected"
        const val STATUS_FAILED = "failed"

        const val PREF_AI_ENABLED = "ai_enabled"
        const val PREF_AI_PROVIDER = "ai_provider"
        const val PREF_AI_KEY = "ai_key"

        const val PREF_NOTIFICATIONS_ENABLED = "notifications_enabled"

        private val PHONE_MASK_ROWS = arrayOf(
            "xxxx.....xxxx", "xx.........xx", "x...........x", "x...........x",
            ".............", ".............", ".............", ".............",
            ".............", "x...........x", "x...........x", "xx.........xx",
            "xxxx.....xxxx"
        )
        private fun masked(r: Int, c: Int) = PHONE_MASK_ROWS[r][c] == 'x'
        private val VALID_CELLS = (0 until 169).filter { !masked(it / 13, it % 13) }

        fun start(context: Context) {
            context.startService(Intent(context, RelayClientService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, RelayClientService::class.java))
        }
    }
}

data class RelayMessage(
    val type: String,
    val id: String? = null,
    val text: String? = null,
    val speed: Int? = null,
    val pixels: String? = null,
    val toyId: String? = null,
    val action: String? = null
)

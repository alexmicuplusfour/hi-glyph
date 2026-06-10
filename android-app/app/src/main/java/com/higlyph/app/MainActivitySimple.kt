package com.higlyph.app

import android.Manifest
import androidx.appcompat.app.AppCompatActivity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.view.View
import android.view.animation.AlphaAnimation
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.higlyph.app.relay.RelayClientService
import com.higlyph.app.toys.LiveGlyphPreview
import com.higlyph.app.toys.PixelGrid
import com.higlyph.app.views.GlyphMatrixView
import java.util.UUID

class MainActivitySimple : AppCompatActivity() {

    private lateinit var matrixPreview: GlyphMatrixView
    private lateinit var connectionDot: TextView
    private lateinit var connectionIndicatorBtn: FrameLayout
    private lateinit var connectionPanel: ScrollView
    private lateinit var serverUrlInput: EditText
    private lateinit var connectBtn: MaterialButton
    private lateinit var relayStatusText: TextView
    private lateinit var relayUrlText: TextView
    private lateinit var copyIdBtn: MaterialButton
    private lateinit var regenerateIdBtn: MaterialButton
    private lateinit var aiEnabledSwitch: MaterialSwitch
    private lateinit var aiDetailsGroup: LinearLayout
    private lateinit var aiProviderGroup: RadioGroup
    private lateinit var radioOpenAI: RadioButton
    private lateinit var aiKeyInput: EditText
    private lateinit var aiSaveBtn: MaterialButton
    private lateinit var aiSaveStatus: TextView
    private lateinit var notificationsSwitch: MaterialSwitch
    private lateinit var relayPrefs: android.content.SharedPreferences

    private val relayStatusListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
        if (key == RelayClientService.PREF_STATUS) {
            val status = prefs.getString(RelayClientService.PREF_STATUS, RelayClientService.STATUS_DISCONNECTED)
            runOnUiThread { updateRelayStatus(status ?: RelayClientService.STATUS_DISCONNECTED) }
        }
    }

    private val glyphPreviewListener = LiveGlyphPreview.Listener { frame ->
        matrixPreview.pixelGrid = frame?.grid ?: PixelGrid(13)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            setContentView(R.layout.activity_main_simple)

            matrixPreview = findViewById(R.id.matrixPreview)
            connectionDot = findViewById(R.id.connectionDot)
            connectionIndicatorBtn = findViewById(R.id.connectionIndicatorBtn)
            connectionPanel = findViewById(R.id.connectionPanel)
            serverUrlInput = findViewById(R.id.serverUrlInput)
            connectBtn = findViewById(R.id.connectBtn)
            relayStatusText = findViewById(R.id.relayStatusText)
            relayUrlText = findViewById(R.id.relayUrlText)
            copyIdBtn = findViewById(R.id.copyIdBtn)
            regenerateIdBtn = findViewById(R.id.regenerateIdBtn)
            aiEnabledSwitch = findViewById(R.id.aiEnabledSwitch)
            aiDetailsGroup = findViewById(R.id.aiDetailsGroup)
            aiProviderGroup = findViewById(R.id.aiProviderGroup)
            radioOpenAI = findViewById(R.id.radioOpenAI)
            aiKeyInput = findViewById(R.id.aiKeyInput)
            aiSaveBtn = findViewById(R.id.aiSaveBtn)
            aiSaveStatus = findViewById(R.id.aiSaveStatus)
            notificationsSwitch = findViewById(R.id.notificationsSwitch)

            relayPrefs = getSharedPreferences(RelayClientService.PREF_FILE, Context.MODE_PRIVATE)

            // First-run: pre-configure default relay so it works out of the box
            if (!relayPrefs.contains(RelayClientService.PREF_SERVER_URL)) {
                relayPrefs.edit()
                    .putString(RelayClientService.PREF_SERVER_URL, "https://higlyph.app")
                    .putBoolean(RelayClientService.PREF_ENABLED, true)
                    .commit()
            }

            // Phone ID setup
            val phoneId = getOrCreatePhoneId()
            updateRelayUrl(phoneId)
            copyIdBtn.setOnClickListener {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Relay URL", relayUrlText.text.toString()))
            }

            // AI section setup
            aiEnabledSwitch.isChecked = relayPrefs.getBoolean(RelayClientService.PREF_AI_ENABLED, false)
            aiDetailsGroup.visibility = if (aiEnabledSwitch.isChecked) View.VISIBLE else View.GONE
            aiEnabledSwitch.setOnCheckedChangeListener { _, checked ->
                aiDetailsGroup.visibility = if (checked) View.VISIBLE else View.GONE
                relayPrefs.edit().putBoolean(RelayClientService.PREF_AI_ENABLED, checked).apply()
                if (relayPrefs.getBoolean(RelayClientService.PREF_ENABLED, false)) {
                    RelayClientService.stop(this)
                    RelayClientService.start(this)
                }
            }
            val savedProvider = relayPrefs.getString(RelayClientService.PREF_AI_PROVIDER, "openai")
            if (savedProvider == "claude") aiProviderGroup.check(R.id.radioClaude)
            else aiProviderGroup.check(R.id.radioOpenAI)
            aiKeyInput.setText(relayPrefs.getString(RelayClientService.PREF_AI_KEY, ""))
            aiSaveBtn.setOnClickListener {
                val provider = if (aiProviderGroup.checkedRadioButtonId == R.id.radioClaude) "claude" else "openai"
                relayPrefs.edit()
                    .putBoolean(RelayClientService.PREF_AI_ENABLED, aiEnabledSwitch.isChecked)
                    .putString(RelayClientService.PREF_AI_PROVIDER, provider)
                    .putString(RelayClientService.PREF_AI_KEY, aiKeyInput.text.toString().trim())
                    .apply()
                aiSaveStatus.text = "Saved"
                if (relayPrefs.getBoolean(RelayClientService.PREF_ENABLED, false)) {
                    RelayClientService.stop(this)
                    RelayClientService.start(this)
                }
                aiSaveStatus.postDelayed({ aiSaveStatus.text = "" }, 2000)
            }

            // Notifications section
            notificationsSwitch.isChecked = relayPrefs.getBoolean(RelayClientService.PREF_NOTIFICATIONS_ENABLED, true)
            notificationsSwitch.setOnCheckedChangeListener { _, checked ->
                relayPrefs.edit().putBoolean(RelayClientService.PREF_NOTIFICATIONS_ENABLED, checked).apply()
            }

            regenerateIdBtn.setOnClickListener {
                val newId = generatePhoneId()
                relayPrefs.edit().putString(RelayClientService.PREF_PHONE_ID, newId).apply()
                updateRelayUrl(newId)
                if (relayPrefs.getBoolean(RelayClientService.PREF_ENABLED, false)) {
                    RelayClientService.stop(this)
                    RelayClientService.start(this)
                }
            }

            // Show empty grid until a toy publishes a frame
            matrixPreview.pixelGrid = PixelGrid(13)

            // Connection indicator opens panel
            connectionIndicatorBtn.setOnClickListener { openPanel() }
            findViewById<FrameLayout>(R.id.closePanelBtn).setOnClickListener { closePanel() }

            // Load relay settings
            val savedUrl = relayPrefs.getString(RelayClientService.PREF_SERVER_URL, "https://higlyph.app")
            val currentStatus = relayPrefs.getString(RelayClientService.PREF_STATUS, RelayClientService.STATUS_DISCONNECTED)
            serverUrlInput.setText(savedUrl)
            updateRelayStatus(currentStatus ?: RelayClientService.STATUS_DISCONNECTED)

            // Request microphone permission for equalizer
            if (!hasAudioPermission()) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_AUDIO)
            }

            // Request notification permission (required on API 33+, which is below our min SDK 34)
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
            }

            // Auto-reconnect relay if it was previously enabled
            if (relayPrefs.getBoolean(RelayClientService.PREF_ENABLED, false)) {
                RelayClientService.start(this)
            }
        } catch (e: Exception) {
            Log.e(TAG, "onCreate failed", e)
            throw e
        }
    }

    override fun onResume() {
        super.onResume()
        relayPrefs.registerOnSharedPreferenceChangeListener(relayStatusListener)
        LiveGlyphPreview.addListener(glyphPreviewListener)

        val status = relayPrefs.getString(RelayClientService.PREF_STATUS, RelayClientService.STATUS_DISCONNECTED)
        updateRelayStatus(status ?: RelayClientService.STATUS_DISCONNECTED)
    }

    override fun onPause() {
        super.onPause()
        relayPrefs.unregisterOnSharedPreferenceChangeListener(relayStatusListener)
        LiveGlyphPreview.removeListener(glyphPreviewListener)
    }

    private fun openPanel() {
        connectionPanel.visibility = View.VISIBLE
        connectionPanel.startAnimation(AlphaAnimation(0f, 1f).apply { duration = 180 })
    }

    private fun closePanel() {
        val anim = AlphaAnimation(1f, 0f).apply {
            duration = 150
            setAnimationListener(object : android.view.animation.Animation.AnimationListener {
                override fun onAnimationEnd(a: android.view.animation.Animation?) {
                    connectionPanel.visibility = View.GONE
                }
                override fun onAnimationStart(a: android.view.animation.Animation?) {}
                override fun onAnimationRepeat(a: android.view.animation.Animation?) {}
            })
        }
        connectionPanel.startAnimation(anim)
    }

    private fun updateRelayUrl(phoneId: String) {
        val serverUrl = relayPrefs.getString(RelayClientService.PREF_SERVER_URL, "")?.trimEnd('/') ?: ""
        if (serverUrl.isEmpty()) {
            relayUrlText.text = phoneId
            return
        }
        val prefix = "$serverUrl/"
        val full = "$prefix$phoneId"
        val span = SpannableString(full)
        span.setSpan(ForegroundColorSpan(0xFF555555.toInt()), 0, prefix.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        span.setSpan(ForegroundColorSpan(Color.WHITE), prefix.length, full.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        relayUrlText.text = span
    }

    private fun setButtonOutlined(btn: MaterialButton, outlined: Boolean) {
        if (outlined) {
            btn.backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
            btn.setTextColor(Color.WHITE)
            btn.strokeWidth = (resources.displayMetrics.density).toInt()
            btn.strokeColor = ColorStateList.valueOf(0xFF444444.toInt())
        } else {
            btn.backgroundTintList = ColorStateList.valueOf(Color.WHITE)
            btn.setTextColor(Color.BLACK)
            btn.strokeWidth = 0
        }
    }

    private fun updateRelayStatus(status: String) {
        updateIndicatorIcon(status)
        when (status) {
            RelayClientService.STATUS_CONNECTED -> {
                relayStatusText.text = "Connected"
                relayStatusText.setTextColor(0xFF4CAF50.toInt())
                setButtonOutlined(connectBtn, outlined = true)
                connectBtn.text = "Disconnect"
                connectBtn.setOnClickListener { disconnect() }
            }
            RelayClientService.STATUS_CONNECTING -> {
                relayStatusText.text = "Connecting..."
                relayStatusText.setTextColor(0xFFFF9800.toInt())
                setButtonOutlined(connectBtn, outlined = true)
                connectBtn.text = "Cancel"
                connectBtn.setOnClickListener { disconnect() }
            }
            RelayClientService.STATUS_FAILED -> {
                relayStatusText.text = "Failed — retrying"
                relayStatusText.setTextColor(0xFFF44336.toInt())
                setButtonOutlined(connectBtn, outlined = true)
                connectBtn.text = "Cancel"
                connectBtn.setOnClickListener { disconnect() }
            }
            else -> {
                relayStatusText.text = ""
                setButtonOutlined(connectBtn, outlined = false)
                connectBtn.text = "Connect to Relay"
                connectBtn.setOnClickListener {
                    val url = serverUrlInput.text.toString().trim()
                    if (url.isNotEmpty()) {
                        relayPrefs.edit()
                            .putString(RelayClientService.PREF_SERVER_URL, url)
                            .putBoolean(RelayClientService.PREF_ENABLED, true)
                            .putString(RelayClientService.PREF_STATUS, RelayClientService.STATUS_CONNECTING)
                            .commit()
                        RelayClientService.start(this)
                        updateRelayStatus(RelayClientService.STATUS_CONNECTING)
                    }
                }
            }
        }
    }

    private fun updateIndicatorIcon(status: String) {
        val (icon, color) = when (status) {
            RelayClientService.STATUS_CONNECTED -> "✓" to 0xFF4CAF50.toInt()
            RelayClientService.STATUS_CONNECTING -> "⚙" to 0xFFFF9800.toInt()
            RelayClientService.STATUS_FAILED -> "⚙" to 0xFFF44336.toInt()
            else -> "⚙" to 0xFF555555.toInt()
        }
        connectionDot.text = icon
        connectionDot.setTextColor(color)
    }

    private fun disconnect() {
        relayPrefs.edit()
            .putBoolean(RelayClientService.PREF_ENABLED, false)
            .putString(RelayClientService.PREF_STATUS, RelayClientService.STATUS_DISCONNECTED)
            .apply()
        RelayClientService.stop(this)
        updateRelayStatus(RelayClientService.STATUS_DISCONNECTED)
    }

    private fun hasAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    private fun getOrCreatePhoneId(): String {
        var id = relayPrefs.getString(RelayClientService.PREF_PHONE_ID, null)
        if (id == null) {
            id = generatePhoneId()
            relayPrefs.edit().putString(RelayClientService.PREF_PHONE_ID, id).apply()
        }
        return id
    }

    private fun generatePhoneId(): String =
        UUID.randomUUID().toString().replace("-", "").substring(0, 8)

    companion object {
        private const val TAG = "MainActivitySimple"
        private const val REQUEST_AUDIO = 1001
        private const val REQUEST_NOTIFICATIONS = 1002
    }
}

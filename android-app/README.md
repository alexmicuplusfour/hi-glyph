# hi! glyph 👋

**Remote control for the Nothing Phone (4a) Pro Glyph Matrix display.**

Turn your phone's 13×13 LED matrix into a web-controlled display. Send scrolling messages, show custom images, play animations - all via a simple HTTP API.

---

## What Is This?

The Nothing Phone (4a) Pro has a 13×13 LED matrix on the back. **hi! glyph** is an Android app that runs an HTTP server on your phone, exposing the hardware so you can control it from:

- **Web pages** - HTML/CSS/JavaScript UI hosted anywhere
- **Desktop apps** - Python, Node.js, Electron, etc.
- **Command line** - cURL, PowerShell, scripts
- **Home automation** - IFTTT, Home Assistant, webhooks

The Android app is a **pure hardware driver** - all your UI and logic lives in your web application.

---

## Features

### 🔤 **Scrolling Text Messages**
Send text from a web page and watch it scroll across the matrix:
```bash
curl -X POST http://phone-ip:8080/hw/text/scroll \
  -H "Content-Type: application/json" \
  -d '{"text":"Hello from the web!","speed":10}'
```

### 🖼️ **Static Images**
Display custom 13×13 pixel art:
```bash
curl -X POST http://phone-ip:8080/hw/image/show \
  -H "Content-Type: application/json" \
  -d '{"pixels":"010101..."}'  # 169-char binary string
```

### 🎬 **Animations**
Play frame-by-frame animations:
```bash
curl -X POST http://phone-ip:8080/hw/animation/start \
  -H "Content-Type: application/json" \
  -d '{"frames":["010101...","101010..."],"fps":10,"loop":true}'
```

### 📡 **WebSocket Live Preview**
Real-time stream of what's displayed on the matrix:
```javascript
const ws = new WebSocket('ws://phone-ip:8080/hw/stream');
ws.onmessage = (event) => {
  const frame = JSON.parse(event.data);
  // Render frame.pixels in your web UI
};
```

### 🕒 **Built-in Toys**
- **Clock** - 4×5 pixel digital time display
- **Equalizer** - Audio-reactive visualizer (13 bars)
- **Call Indicator** - Phone icon during calls

---

## Quick Start

### 1. Build and Install

```bash
# Configure SDK location in local.properties:
echo "sdk.dir=C:\\Users\\YourUsername\\AppData\\Local\\Android\\Sdk" > local.properties

# Build and install
./gradlew installDebug

# Enable Glyph debug mode
adb shell settings put global nt_glyph_interface_debug_enable 1
```

See [BUILD.md](BUILD.md) for detailed instructions.

### 2. Get Phone IP

```bash
adb shell ip addr show wlan0 | grep "inet "
```

Example: `192.168.1.50`

### 3. Test API

```bash
curl http://192.168.1.50:8080/health
```

### 4. Select Toy in Settings

```
Settings → Glyph Interface → Always-on Glyph Toy
```

Choose:
- **hi! glyph: Scrolling text** - for scrolling messages
- **hi! glyph: Static image** - for static images
- **hi! glyph: Animation** - for animations
- **hi! glyph: Multi function** - for clock/equalizer/calls

### 5. Send Your First Message

```bash
curl -X POST http://192.168.1.50:8080/hw/text/scroll \
  -H "Content-Type: application/json" \
  -d '{"text":"hi! glyph","speed":10,"loopCount":0}'
```

Watch your phone's back! 🎉

---

## Documentation

- **[HARDWARE_API.md](HARDWARE_API.md)** - Complete API reference (all endpoints, examples, troubleshooting)
- **[BUILD.md](BUILD.md)** - Build instructions, setup, first-run guide
- **[AGENTS.md](AGENTS.md)** - Architecture details for developers

---

## Architecture

```
┌─────────────────────────────────────┐
│  Your Web UI / Desktop App          │
│  (HTML/CSS/JS, Python, Node, etc.)  │
└─────────────────────────────────────┘
              ↓ HTTP/WebSocket
┌─────────────────────────────────────┐
│  hi! glyph Android App              │
│  • Ktor HTTP Server (port 8080)    │
│  • Hardware API (REST + WebSocket)  │
│  • Glyph Toy Services               │
└─────────────────────────────────────┘
              ↓ Nothing SDK
┌─────────────────────────────────────┐
│  Nothing Phone (4a) Pro             │
│  13×13 LED Matrix Hardware          │
└─────────────────────────────────────┘
```

**You build the top layer** - the Android app handles everything else.

---

## Example Use Cases

### 💬 **Message Board**
Simple web page where friends can send messages to your phone:
```html
<form id="messageForm">
  <input type="text" id="messageInput" placeholder="Your message">
  <button type="submit">Send</button>
</form>

<script>
  const API_BASE = 'http://192.168.1.50:8080';

  document.getElementById('messageForm').onsubmit = async (e) => {
    e.preventDefault();
    const text = document.getElementById('messageInput').value;

    await fetch(`${API_BASE}/hw/text/scroll`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ text, speed: 10, loopCount: 1 })
    });

    alert('Message sent!');
  };
</script>
```

### 🎨 **Pixel Art Editor**
13×13 canvas where you draw and instantly see it on your phone.

### 🔔 **Notification Display**
Show incoming notifications from your computer on your phone's matrix.

### 📊 **Status Dashboard**
Display live stats: CPU usage, unread emails, build status, etc.

### 🎮 **Game Display**
Use the matrix as a peripheral display for games (health bar, minimap, etc.).

---

## API Highlights

### Core Endpoints

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/hw/text/scroll` | POST | Scroll text horizontally |
| `/hw/text/static` | POST | Show static centered text |
| `/hw/image/show` | POST | Display 13×13 pixel image |
| `/hw/animation/start` | POST | Play frame sequence |
| `/hw/frame` | POST | Display raw pixel grid |
| `/hw/brightness` | POST | Set LED brightness (0-255) |
| `/hw/clear` | POST | Turn off all LEDs |
| `/hw/status` | GET | Get current display state |
| `/hw/capabilities` | GET | Get device capabilities |
| `/hw/stream` | WebSocket | Live frame stream |

See [HARDWARE_API.md](HARDWARE_API.md) for all 20+ endpoints.

---

## Data Format

Pixels are represented as **169-character binary strings** (13×13 grid):
- `'1'` = LED on (white)
- `'0'` = LED off (black)
- Row-major order (left-to-right, top-to-bottom)

**Example:**
```javascript
// Checkerboard pattern
const pixels = Array(169).fill(0)
  .map((_, i) => (Math.floor(i / 13) + (i % 13)) % 2)
  .join('');

await fetch('http://192.168.1.50:8080/hw/image/show', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ pixels })
});
```

---

## Requirements

### Phone
- **Nothing Phone (4a) Pro** (13×13 matrix)
- Android 14+ (API 34+)
- Glyph debug mode enabled

### Development
- Android SDK (build tools 35.0.0)
- Java 11+
- ADB (for device connection)

---

## Limitations

- **Local network only** - API has no authentication (secure your Wi-Fi!)
- **One toy at a time** - Only one AOD toy can be active
- **Monochrome** - LEDs are white only (hardware limitation)
- **Fixed brightness** - All LEDs share same brightness (hardware limitation)
- **Debug mode expires** - Re-enable after 48 hours

---

## Troubleshooting

### API not reachable?
1. Check app is running: Look for "hi! glyph Hardware API" notification
2. Verify phone IP: `adb shell ip addr show wlan0`
3. Test health: `curl http://[phone-ip]:8080/health`

### Display not updating?
1. Select correct toy in Settings → Glyph Interface → Always-on Glyph Toy
2. Check API response for instructions
3. Verify phone isn't in DND mode

### Glyph debug mode expired?
```bash
adb shell settings put global nt_glyph_interface_debug_enable 1
```

See [BUILD.md](BUILD.md#troubleshooting) for more.

---

## Development

### Project Structure
```
app/src/main/java/com/higlyph/app/
├── api/
│   ├── HardwareApiService.kt      # Ktor HTTP server
│   ├── HardwareApiHandler.kt      # API endpoint logic
│   └── HardwareApiModels.kt       # Request/response models
├── toys/
│   ├── ScrollingTextToyService.kt # Scrolling text display
│   ├── AnimationToyService.kt     # Animation playback
│   ├── StaticImageToyService.kt   # Static image display
│   ├── CompositeToyService.kt     # Clock/equalizer/call
│   └── text/
│       ├── PixelFont.kt           # 5×7 pixel font definitions
│       └── TextRenderer.kt        # String → PixelGrid converter
├── models/                        # Data models
├── repository/                    # SharedPreferences storage
└── views/                         # Android UI
```

### Tech Stack
- **Kotlin** - Android app
- **Ktor** - HTTP server framework
- **WebSockets** - Real-time frame streaming
- **Nothing Glyph SDK 2.0** - Hardware control

---

## Credits

Built with the [Nothing Glyph Matrix SDK](https://github.com/Nothing-Developer-Programme).

Based on [glyph-matrix-lab](https://github.com/alex-1121/glyph-matrix-lab) by [alex-1121](https://github.com/alex-1121) — extended with Hardware API, relay server, and web control.

Made by [alexmicuplusfour](https://github.com/alexmicuplusfour).

---

## License

GPL-3.0 — see [LICENSE](LICENSE).

---

## What's Next?

1. **Build a web UI** - Create your custom interface
2. **Connect to other services** - IFTTT, webhooks, RSS, etc.
3. **Share your creations** - Show off your pixel art and animations!

**Happy hacking!** 🚀

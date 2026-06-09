# hi! glyph Hardware API Documentation

Complete REST API for controlling the Nothing Phone (4a) Pro Glyph Matrix (13×13 LEDs).

## Overview

The Android app runs a Ktor HTTP server on port **8080** that exposes all glyph hardware capabilities. This allows you to build web applications, desktop clients, or any other software that can make HTTP requests to control the display.

**Base URL:** `http://[phone-ip]:8080`

**Finding your phone's IP:**
```bash
adb shell ip addr show wlan0 | grep inet
```

---

## Authentication

Currently, the API has **no authentication**. It's designed for local network use only. Ensure your phone is on a trusted Wi-Fi network.

---

## Data Formats

### Pixel Grid Format

All pixel data is sent as a **169-character binary string** (13×13 = 169 pixels).

- `'1'` = LED on (white)
- `'0'` = LED off (black)
- Row-major order: `pixels[0-12]` = row 0, `pixels[13-25]` = row 1, etc.

**Example:** A 3×3 cross pattern on a 13×13 grid:
```
0000000100000000000010000000000111110000000000100000000001000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000
```

---

## API Endpoints

### Core Display

#### `POST /hw/frame`
Display a raw pixel grid.

**Request:**
```json
{
  "pixels": "010101...",  // 169-char binary string
  "brightness": 255       // 0-255, optional (default: 255)
}
```

**Response:**
```json
{
  "success": true,
  "message": "Frame displayed"
}
```

---

#### `POST /hw/brightness`
Set global brightness.

**Request:**
```json
{
  "brightness": 200  // 0-255
}
```

**Response:**
```json
{
  "success": true,
  "message": "Brightness set to 200"
}
```

---

#### `POST /hw/clear`
Turn off all LEDs.

**Request:** (empty body)

**Response:**
```json
{
  "success": true,
  "message": "Display cleared"
}
```

---

### Text Rendering

#### `POST /hw/text/static`
Display static centered text (no scrolling).

**Request:**
```json
{
  "text": "HI",
  "verticalOffset": -1  // -1 = auto-center, or 0-12 for manual position
}
```

**Response:**
```json
{
  "success": true
}
```

**Note:** Text must fit within 13 pixels wide. Longer text requires scrolling.

---

#### `POST /hw/text/scroll`
Start horizontally scrolling text.

**Request:**
```json
{
  "text": "Hello from the web!",
  "speed": 10,           // FPS (1-30), default: 10
  "loopCount": 0,        // 0 = infinite, >0 = loop N times
  "verticalOffset": -1   // -1 = auto-center
}
```

**Response:**
```json
{
  "success": true,
  "message": "Scrolling text started",
  "data": {
    "text": "Hello from the web!",
    "speed": 10,
    "loopCount": 0
  }
}
```

**Important:** You must manually select **"hi! glyph: Scrolling text"** in:
```
Settings → Glyph Interface → Always-on Glyph Toy
```

---

#### `PUT /hw/text/scroll/speed`
Change scroll speed mid-animation.

**Request:**
```json
{
  "speed": 15  // FPS (1-30)
}
```

**Response:**
```json
{
  "success": true,
  "message": "Scroll speed set to 15 FPS"
}
```

---

#### `PUT /hw/text/scroll/pause`
Pause or resume scrolling.

**Request:**
```json
{
  "paused": true  // true to pause, false to resume
}
```

**Response:**
```json
{
  "success": true,
  "message": "Scrolling paused"
}
```

---

#### `POST /hw/text/scroll/stop`
Stop scrolling and clear display.

**Request:** (empty body)

**Response:**
```json
{
  "success": true,
  "message": "Scrolling stopped"
}
```

---

### Static Images

#### `POST /hw/image/show`
Display a static 13×13 image.

**Request:**
```json
{
  "pixels": "010101..."  // 169-char binary string
}
```

**Response:**
```json
{
  "success": true,
  "message": "Frame displayed"
}
```

**Important:** You must manually select **"hi! glyph: Static image"** in AOD settings.

---

### Animations

#### `POST /hw/animation/start`
Play a frame-by-frame animation.

**Request:**
```json
{
  "frames": [
    "010101...",  // frame 1 (169 chars)
    "101010...",  // frame 2 (169 chars)
    "010101..."   // frame 3 (169 chars)
  ],
  "fps": 10,      // 1-60, default: 10
  "loop": true    // true = infinite loop, false = play once
}
```

**Response:**
```json
{
  "success": true,
  "message": "Animation started",
  "data": {
    "frameCount": 3,
    "fps": 10,
    "loop": true
  }
}
```

**Important:** You must manually select **"hi! glyph: Animation"** in AOD settings.

---

#### `PUT /hw/animation/fps`
Change animation playback speed.

**Request:**
```json
{
  "speed": 20  // FPS (1-60)
}
```

**Response:**
```json
{
  "success": true,
  "message": "Animation FPS set to 20"
}
```

---

#### `POST /hw/animation/stop`
Stop animation playback.

**Request:** (empty body)

**Response:**
```json
{
  "success": true,
  "message": "Animation stopped"
}
```

---

### Built-in Toys

#### `POST /hw/toy/clock/activate`
Show the digital clock display.

**Response:**
```json
{
  "success": true,
  "message": "Select 'hi! glyph: Multi function' in Settings → Glyph Interface → Always-on Glyph Toy"
}
```

**Note:** The composite toy automatically shows clock when idle.

---

#### `POST /hw/toy/equalizer/activate`
Activate audio visualizer.

**Response:**
```json
{
  "success": true,
  "message": "Select 'hi! glyph: Multi function' and play audio. Ensure microphone permission is granted."
}
```

**Note:** The composite toy automatically activates equalizer when audio is playing.

---

### Status & Info

#### `GET /hw/status`
Get current display state.

**Response:**
```json
{
  "mode": "scrolling",  // "static", "scrolling", "clock", "equalizer", "call", "animation", "idle"
  "brightness": 255,
  "activeContent": {
    "type": "scrolling_text_toy",
    "preview": "010101..."  // 169-char binary of current frame
  },
  "uptimeSeconds": 3600
}
```

---

#### `GET /hw/capabilities`
Get device capabilities.

**Response:**
```json
{
  "device": "DEVICE_25111p",
  "matrixSize": 13,
  "brightnessRange": {
    "start": 0,
    "end": 255
  },
  "maxFps": 60,
  "features": [
    "text_rendering",
    "scrolling_text",
    "static_image",
    "animation",
    "clock",
    "equalizer",
    "websocket_stream"
  ]
}
```

---

### Test Patterns

#### `POST /hw/test/checkerboard`
Display checkerboard pattern.

**Response:**
```json
{
  "pattern": "checkerboard",
  "applied": true
}
```

---

#### `POST /hw/test/all-on`
Turn on all LEDs.

**Response:**
```json
{
  "pattern": "all-on",
  "applied": true
}
```

---

#### `POST /hw/test/all-off`
Turn off all LEDs.

**Response:**
```json
{
  "pattern": "all-off",
  "applied": true
}
```

---

### Live Preview (WebSocket)

#### `WebSocket /hw/stream`
Real-time stream of displayed frames.

**Connection:** `ws://[phone-ip]:8080/hw/stream`

**Messages Received:**
```json
{
  "type": "frame",
  "pixels": "010101..."  // Current displayed frame (169 chars)
}
```

**Frequency:** Updates pushed whenever the display changes (up to 60 FPS).

**Example (JavaScript):**
```javascript
const ws = new WebSocket('ws://192.168.1.50:8080/hw/stream');

ws.onmessage = (event) => {
  const data = JSON.parse(event.data);
  console.log('Current frame:', data.pixels);
  // Render preview in your web UI
};
```

---

### Health Check

#### `GET /health`
Service health check.

**Response:**
```json
{
  "status": "ok",
  "service": "hi! glyph Hardware API"
}
```

---

## Quick Start Examples

### cURL Examples

**Display "HI":**
```bash
curl -X POST http://192.168.1.50:8080/hw/text/static \
  -H "Content-Type: application/json" \
  -d '{"text":"HI"}'
```

**Scroll a message:**
```bash
curl -X POST http://192.168.1.50:8080/hw/text/scroll \
  -H "Content-Type: application/json" \
  -d '{"text":"Hello from the web!","speed":10,"loopCount":0}'
```

**Show checkerboard:**
```bash
curl -X POST http://192.168.1.50:8080/hw/test/checkerboard
```

**Get current status:**
```bash
curl http://192.168.1.50:8080/hw/status
```

---

### JavaScript Example

```javascript
const API_BASE = 'http://192.168.1.50:8080';

async function sendMessage(text) {
  const response = await fetch(`${API_BASE}/hw/text/scroll`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      text: text,
      speed: 10,
      loopCount: 0
    })
  });

  const result = await response.json();
  console.log(result);
}

// Live preview WebSocket
const ws = new WebSocket('ws://192.168.1.50:8080/hw/stream');
ws.onmessage = (event) => {
  const data = JSON.parse(event.data);
  // Update your canvas/preview with data.pixels
};

sendMessage('Hello!');
```

---

### Python Example

```python
import requests

API_BASE = 'http://192.168.1.50:8080'

def send_message(text, speed=10):
    response = requests.post(
        f'{API_BASE}/hw/text/scroll',
        json={
            'text': text,
            'speed': speed,
            'loopCount': 0
        }
    )
    return response.json()

def display_image(pixels):
    """pixels: 169-char binary string"""
    response = requests.post(
        f'{API_BASE}/hw/image/show',
        json={'pixels': pixels}
    )
    return response.json()

# Example usage
result = send_message('Hello from Python!')
print(result)
```

---

## Important Notes

### Toy Selection Requirement

Different display modes require selecting the correct toy in Android settings:

| API Endpoint | Required Toy Selection |
|--------------|------------------------|
| `/hw/text/scroll` | **"hi! glyph: Scrolling text"** |
| `/hw/animation/start` | **"hi! glyph: Animation"** |
| `/hw/image/show` | **"hi! glyph: Static image"** |
| `/hw/toy/clock/activate` | **"hi! glyph: Multi function"** |
| `/hw/toy/equalizer/activate` | **"hi! glyph: Multi function"** |

**Path to settings:**
```
Settings → Glyph Interface → Always-on Glyph Toy → [Select toy]
```

---

### Network Discovery

To find your phone on the network:

**Via ADB:**
```bash
adb shell ip addr show wlan0 | grep "inet "
```

**Via Router:** Check DHCP leases in your router admin panel.

**Future Enhancement:** mDNS/Bonjour support for `http://glyph.local` (not yet implemented).

---

### CORS

The API server has **CORS enabled** for all origins, making it easy to call from web pages hosted anywhere.

---

### Error Responses

All endpoints return standard format on error:

```json
{
  "success": false,
  "message": "Invalid pixels format. Must be 169-char binary string."
}
```

**HTTP Status Codes:**
- `200 OK` - Success
- `400 Bad Request` - Invalid input
- `500 Internal Server Error` - Server error

---

## Troubleshooting

### API server not reachable

1. **Check if app is running:**
   - Open the "hi! glyph" app on your phone
   - You should see a persistent notification: "hi! glyph Hardware API - Server running on port 8080"

2. **Check phone IP:**
   ```bash
   adb shell ip addr show wlan0
   ```

3. **Check firewall:**
   - Ensure port 8080 is not blocked
   - Try from another device on the same network

4. **Check logs:**
   ```bash
   adb logcat -s HardwareApiService HardwareApiHandler
   ```

---

### Display not updating

1. **Verify correct toy is selected:**
   - Settings → Glyph Interface → Always-on Glyph Toy
   - Select the appropriate toy for your API call

2. **Check API response:**
   - Ensure `"success": true` in response
   - Check `message` field for instructions

3. **Check live preview:**
   - Connect to `ws://[phone-ip]:8080/hw/stream`
   - Verify frames are being pushed

---

### Scrolling text shows static bars instead of reacting

- Grant **microphone permission** to "hi! glyph" app:
  ```
  Settings → Apps → hi! glyph → Permissions → Microphone → Allow
  ```

- This permission is required for the equalizer feature in the composite toy.

---

## Next Steps

Now that you have the Hardware API running, you can:

1. **Build a web interface** - Create HTML/CSS/JS front-end that calls this API
2. **Desktop client** - Build Electron, Python, or native app
3. **Home automation integration** - Connect to Home Assistant, IFTTT, etc.
4. **Notification system** - Show messages from webhooks, RSS feeds, etc.

The Android app is now a pure hardware driver - all your application logic lives in your web server or client!

---

## License

This API is part of the "hi! glyph" project. Free to use for personal and commercial projects.

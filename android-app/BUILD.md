# Building hi! glyph

Instructions for building and installing the "hi! glyph" Android app.

---

## Prerequisites

You need **one** of the following:

### Option A: Command Line Only (Minimal Setup)

1. **Android SDK Command Line Tools**
   - Download from: https://developer.android.com/studio#command-line-tools-only
   - Extract to a location like `C:\Android\cmdline-tools`
   - Set environment variable: `ANDROID_HOME=C:\Android`

2. **Java JDK 11 or newer**
   - Download from: https://adoptium.net/
   - Ensure `JAVA_HOME` is set

### Option B: Android Studio (Full IDE)

1. **Download Android Studio:** https://developer.android.com/studio
2. **Install SDK components:**
   - Android SDK Platform 35
   - Android SDK Build-Tools 35.0.0
   - Android SDK Command-line Tools

Android Studio will automatically set `ANDROID_HOME`.

---

## Setup

### 1. Configure SDK Location

Create a file named `local.properties` in the project root:

**Windows:**
```properties
sdk.dir=C\:\\Users\\YourUsername\\AppData\\Local\\Android\\Sdk
```

**Mac/Linux:**
```properties
sdk.dir=/Users/YourUsername/Library/Android/sdk
```

To find your SDK path:
- **Android Studio:** File → Project Structure → SDK Location
- **Windows default:** `C:\Users\YourUsername\AppData\Local\Android\Sdk`
- **Mac default:** `~/Library/Android/sdk`
- **Linux default:** `~/Android/Sdk`

### 2. Connect Your Phone

Enable **USB Debugging** on your Nothing Phone (4a) Pro:

1. Settings → About phone → Tap "Build number" 7 times
2. Settings → System → Developer options → Enable "USB debugging"
3. Connect phone via USB
4. Accept the "Allow USB debugging" prompt on your phone

Verify connection:
```bash
adb devices
```

You should see:
```
List of devices attached
ABC123XYZ    device
```

---

## Building

### Option 1: Build and Install in One Command

```bash
./gradlew installDebug
```

This will:
1. Build the debug APK
2. Install it on your connected phone
3. The app will appear as "hi! glyph" in your app drawer

### Option 2: Build APK Only

```bash
./gradlew assembleDebug
```

The APK will be at:
```
app/build/outputs/apk/debug/app-debug.apk
```

Install manually:
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## First Run Setup

### 1. Enable Debug Mode

Glyph debug mode auto-disables after 48 hours. Enable it:

```bash
adb shell settings put global nt_glyph_interface_debug_enable 1
```

### 2. Grant Permissions

Open the "hi! glyph" app and:
1. Grant **Microphone** permission (for equalizer feature)
2. The app will automatically start the Hardware API server

### 3. Verify API Server

Check that the server is running:

```bash
# Get phone IP
adb shell ip addr show wlan0 | grep "inet "

# Test API (replace with your phone's IP)
curl http://192.168.1.50:8080/health
```

Expected response:
```json
{
  "status": "ok",
  "service": "hi! glyph Hardware API"
}
```

### 4. Select a Toy

Go to:
```
Settings → Glyph Interface → Always-on Glyph Toy
```

You'll see:
- **hi! glyph: Multi function** - Clock, equalizer, call indicator
- **hi! glyph: Static image** - Static custom image
- **hi! glyph: Scrolling text** - Scrolling text messages
- **hi! glyph: Animation** - Frame-by-frame animations

Select the one you want to control via the API.

---

## Testing the API

### Test Scrolling Text

1. Select **"hi! glyph: Scrolling text"** in AOD settings
2. Send a message:

```bash
curl -X POST http://192.168.1.50:8080/hw/text/scroll \
  -H "Content-Type: application/json" \
  -d '{"text":"Hello from the web!","speed":10,"loopCount":0}'
```

3. Watch your phone's back - the text should scroll across the matrix!

### Test Static Text

```bash
curl -X POST http://192.168.1.50:8080/hw/text/static \
  -H "Content-Type: application/json" \
  -d '{"text":"HI"}'
```

### Test Checkerboard Pattern

```bash
curl -X POST http://192.168.1.50:8080/hw/test/checkerboard
```

### Live Preview Stream (WebSocket)

Create an HTML file and open it in your browser:

```html
<!DOCTYPE html>
<html>
<head>
    <title>Live Glyph Preview</title>
    <style>
        canvas { border: 1px solid black; image-rendering: pixelated; }
    </style>
</head>
<body>
    <h1>Live Glyph Preview</h1>
    <canvas id="preview" width="260" height="260"></canvas>
    <script>
        const canvas = document.getElementById('preview');
        const ctx = canvas.getContext('2d');
        const ws = new WebSocket('ws://192.168.1.50:8080/hw/stream');

        ws.onmessage = (event) => {
            const data = JSON.parse(event.data);
            const pixels = data.pixels;

            ctx.fillStyle = '#000';
            ctx.fillRect(0, 0, 260, 260);

            for (let y = 0; y < 13; y++) {
                for (let x = 0; x < 13; x++) {
                    const index = y * 13 + x;
                    if (pixels[index] === '1') {
                        ctx.fillStyle = '#fff';
                        ctx.fillRect(x * 20, y * 20, 20, 20);
                    }
                }
            }
        };

        ws.onerror = (error) => {
            console.error('WebSocket error:', error);
        };
    </script>
</body>
</html>
```

---

## Troubleshooting

### Build Fails: "SDK location not found"

Create or fix `local.properties` with correct SDK path (see Setup step 1).

### Build Fails: "Ktor dependencies not found"

Gradle needs to download dependencies. Ensure you have internet access and run:
```bash
./gradlew build --refresh-dependencies
```

### Phone Not Detected

```bash
# Check connection
adb devices

# If "unauthorized", check phone for USB debugging prompt

# If no devices, try:
adb kill-server
adb start-server
adb devices
```

### API Server Not Starting

Check logs:
```bash
adb logcat -s HardwareApiService HardwareApiHandler
```

Look for:
```
D/HardwareApiService: Hardware API Service created
I/HardwareApiService: Hardware API server started on port 8080
```

### Display Not Updating

1. **Check toy selection:**
   - Settings → Glyph Interface → Always-on Glyph Toy
   - Ensure correct toy is selected for your API endpoint

2. **Check API response:**
   ```bash
   curl http://192.168.1.50:8080/hw/status
   ```

3. **Force restart toy service:**
   ```bash
   adb shell am force-stop com.higlyph.app
   # Reopen the app
   ```

### Glyph Debug Mode Expired

Re-enable (auto-expires after 48 hours):
```bash
adb shell settings put global nt_glyph_interface_debug_enable 1
```

---

## Development Workflow

### Code Changes

If you modify the Kotlin code, rebuild and reinstall:

```bash
./gradlew clean installDebug
```

### Web UI Development

The web UI that calls the Hardware API can be developed completely separately:

1. **Local development:** Run a local web server (e.g., `python -m http.server`)
2. **Point to phone API:** Set `API_BASE = 'http://192.168.1.50:8080'` in your JavaScript
3. **Iterate:** No need to rebuild Android app, just refresh your web page

See [HARDWARE_API.md](HARDWARE_API.md) for complete API documentation.

---

## Clean Build

If you encounter issues, try a clean build:

```bash
./gradlew clean
./gradlew assembleDebug
```

---

## Logs

### View All Logs
```bash
adb logcat
```

### View API Logs Only
```bash
adb logcat -s HardwareApiService HardwareApiHandler ScrollingTextToy AnimationToy
```

### View Toy Service Logs
```bash
adb logcat -s GlyphToyBase CompositeToy StaticImageToy ScrollingTextToy AnimationToy
```

---

## Next Steps

1. **Read the API documentation:** [HARDWARE_API.md](HARDWARE_API.md)
2. **Build a web interface** that calls the Hardware API
3. **Deploy your web app** on a local server or cloud
4. **Send messages** from anywhere to your phone's matrix!

---

## Useful Commands

```bash
# Build and install
./gradlew installDebug

# Uninstall
adb uninstall com.higlyph.app

# View installed version
adb shell dumpsys package com.higlyph.app | grep versionName

# Open app
adb shell am start -n com.higlyph.app/.MainActivity

# Open AOD toy picker
adb shell am start -n com.nothing.thirdparty/com.nothing.thirdparty.matrix.toys.manager.AodToySelectActivity

# Enable Glyph debug mode
adb shell settings put global nt_glyph_interface_debug_enable 1

# Get phone IP
adb shell ip addr show wlan0 | grep "inet "

# Test API health
curl http://192.168.1.50:8080/health

# Send scrolling text
curl -X POST http://192.168.1.50:8080/hw/text/scroll \
  -H "Content-Type: application/json" \
  -d '{"text":"Hello!","speed":10,"loopCount":0}'
```

---

## Support

If you encounter issues:

1. Check logs: `adb logcat -s HardwareApiService`
2. Verify phone IP: `adb shell ip addr show wlan0`
3. Test API health: `curl http://[phone-ip]:8080/health`
4. Ensure correct toy is selected in Settings

Happy hacking! 🎉

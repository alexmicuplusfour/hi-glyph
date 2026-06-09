# hi! glyph

Control the Nothing Phone (4a) Pro's 13×13 LED matrix from any browser, anywhere.

Install the Android app, point it at a relay server, and anyone with the link can send messages and pixel art to the display.

---

## How it works

```
[Browser at higlyph.app/uuid] → [Relay Server] → WebSocket → [Android App] → [LED Matrix]
```

The Android app runs as an AOD toy on the phone and maintains a persistent WebSocket connection to the relay. The relay hosts a web UI — open it on any device and whatever you send appears on the matrix.

---

## Components

### `android-app/`

Kotlin app for Nothing Phone (4a) Pro. Registers as a Glyph Toy, connects to the relay server, and drives the 13×13 LED matrix. Also includes built-in display modes that run independently:

- **Clock** — 4×5 pixel digital time
- **Equalizer** — audio-reactive bars, 13 columns
- **Call indicator** — phone icon during active calls
- **Custom images** — pixel art you draw in the app

See [android-app/BUILD.md](android-app/BUILD.md) for build instructions.

### `relay-server/`

Node.js + Express server. Hosts the web UI and brokers messages between browsers and phones over WebSocket. Supports multiple phones simultaneously via UUID-scoped routes.

A public instance runs at **[higlyph.app](https://higlyph.app)**.

---

## Self-hosting the relay

```bash
cd relay-server
npm install
npm start
# Server runs on port 3000
```

Then point the Android app at your server URL in its settings.

For production: put nginx in front (see the nginx config in the repo), get a cert with Certbot, and run the server with `pm2`.

---

## Credits

Built with the [Nothing Glyph Matrix SDK](https://github.com/Nothing-Developer-Programme).

Original "Matrix Lab" project by [sajenko](https://github.com/sajenko) — extended with relay server and web control.

Made by [alexmicuplusfour](https://github.com/alexmicuplusfour).

---

## License

GPL-3.0 — see [android-app/LICENSE](android-app/LICENSE).

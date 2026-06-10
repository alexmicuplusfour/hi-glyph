# hi! glyph — Android app

Kotlin app for Nothing Phone (4a) Pro. Registers as a Glyph Toy, connects to the relay server, and drives the 13×13 LED matrix.

The app connects to [higlyph.app](https://higlyph.app) by default on first launch and gives you a personal URL to share. Anyone with the link can send messages, draw pixel art, or run animations on the display.

---

## Setup

After installing:

1. Go to **Settings → Apps → hi! glyph → App battery usage** — enable **Allow background usage** and set it to **Unrestricted**
2. Go to **Settings → Glyph Interface → Flip to Glyph** — enable **Always-on Glyph Toy** and select **hi! glyph**

---

## Building

See [BUILD.md](BUILD.md) for full instructions. Quick start:

```bash
./gradlew installDebug
adb shell settings put global nt_glyph_interface_debug_enable 1
```

---

## Credits

Built with the [Nothing Glyph Matrix SDK](https://github.com/Nothing-Developer-Programme).

Based on [glyph-matrix-lab](https://github.com/alex-1121/glyph-matrix-lab) by [alex-1121](https://github.com/alex-1121) — extended with relay server and web control.

> **Disclaimer:** This project is 100% vibecoded. All code was written by Claude.

---

## License

GPL-3.0 — see [LICENSE](LICENSE).

# hi! glyph Relay Server

Public relay server for sending messages to Nothing Phone (4a) Pro LED matrix display.

## Quick Start

### 1. Install Dependencies

```bash
npm install
```

### 2. Start Server

```bash
npm start
```

Server runs on port 3000 by default.

### 3. Configure Android App

1. Find your laptop's IP address on your local network
2. Open the hi! glyph app on your phone
3. Enter server URL: `http://YOUR_LAPTOP_IP:3000`
4. Click "Connect to Relay"

### 4. Send Messages

Open `http://YOUR_LAPTOP_IP:3000` in any browser and send messages!

## Deployment to DigitalOcean

When ready to deploy publicly:

1. Create a DigitalOcean droplet
2. Clone this repo
3. Run `npm install && npm start`
4. Update the server URL in the Android app to your droplet's IP/domain
5. (Optional) Set up nginx reverse proxy for HTTPS

## API Endpoints

- `GET /` - Web UI for sending messages
- `POST /api/send` - Send message (JSON: `{text, speed}`)
- `GET /api/status` - Check server status
- WebSocket `/` - Phone connection endpoint

## Architecture

```
[Web Browser] → POST /api/send → [Relay Server] → WebSocket → [Phone]
```

The phone maintains a persistent WebSocket connection to the relay server. When someone sends a message via the web UI, the server forwards it to the phone over the WebSocket, and the phone displays it on the LED matrix.

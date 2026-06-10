const express = require('express');
const WebSocket = require('ws');
const http = require('http');
const https = require('https');
const path = require('path');
const fs = require('fs');
const { URL } = require('url');

const app = express();
app.use(express.json());
app.use(express.static('public', { index: false }));
const server = http.createServer(app);
const wss = new WebSocket.Server({ server });

const DATA_DIR = path.join(__dirname, 'data');
const FAV_FILE = path.join(DATA_DIR, 'favorites.json');
fs.mkdirSync(DATA_DIR, { recursive: true });

// ── Rate limiting ──────────────────────────────────────────────────────────────
class RateLimiter {
  constructor(limit, windowMs) {
    this.limit = limit;
    this.windowMs = windowMs;
    this.hits = new Map(); // uuid -> [timestamps]
  }
  check(uuid) {
    const now = Date.now();
    const cutoff = now - this.windowMs;
    const hits = (this.hits.get(uuid) || []).filter(t => t > cutoff);
    if (hits.length >= this.limit) return false;
    hits.push(now);
    this.hits.set(uuid, hits);
    return true;
  }
  // Prevent unbounded growth when phones disconnect
  prune() {
    const cutoff = Date.now() - this.windowMs;
    for (const [uuid, hits] of this.hits) {
      const trimmed = hits.filter(t => t > cutoff);
      if (trimmed.length === 0) this.hits.delete(uuid);
      else this.hits.set(uuid, trimmed);
    }
  }
}

const apiLimiter = new RateLimiter(30, 60_000);  // 30 req/min per uuid
setInterval(() => { apiLimiter.prune(); }, 5 * 60_000);

function rateLimit(limiter) {
  return (req, res, next) => {
    if (!limiter.check(req.params.uuid))
      return res.status(429).json({ success: false, error: 'Too many requests' });
    next();
  };
}

// ── AI generation ─────────────────────────────────────────────────────────────
// AI config is pushed from the phone over WebSocket; stored per UUID in memory
const aiConfigs = new Map(); // uuid -> { enabled, provider, key }

// 1 = masked/inactive pixel position, 0 = active
const PHONE_MASK_FLAT =
  '1111000001111' + '1100000000011' + '1000000000001' + '1000000000001' +
  '0000000000000' + '0000000000000' + '0000000000000' + '0000000000000' +
  '0000000000000' + '1000000000001' + '1000000000001' + '1100000000011' +
  '1111000001111';

const AI_SYSTEM_PROMPT = `You draw pixel art for a 13x13 LED display on the Nothing Phone (4a) Pro.

The display has a phone-shaped boundary. Draw on this exact canvas — 'x' marks physically inactive positions (output 0), '.' marks positions you can light up:

xxxx.....xxxx
xx.........xx
x...........x
x...........x
.............
.............
.............
.............
.............
x...........x
x...........x
xx.........xx
xxxx.....xxxx

Output EXACTLY 13 rows of 13 characters using 1 (lit) and 0 (unlit/inactive). No labels, no explanation — just the 13 rows.`;

const EXAMPLE_HEART = `0000000000000
0000000000000
0001100110000
0011111111000
0111111111110
0011111111000
0001111110000
0000111100000
0000011000000
0000001000000
0000000000000
0000000000000
0000000000000`;

const EXAMPLE_A = `0000000000000
0000000000000
0000001000000
0000010100000
0000100010000
0000111110000
0001000001000
0001000001000
0001000001000
0000000000000
0000000000000
0000000000000
0000000000000`;

function callOpenAI(userPrompt, apiKey) {
  return new Promise((resolve, reject) => {
    const body = JSON.stringify({
      model: 'gpt-4o',
      messages: [
        { role: 'system', content: AI_SYSTEM_PROMPT },
        { role: 'user', content: 'a heart' },
        { role: 'assistant', content: EXAMPLE_HEART },
        { role: 'user', content: 'letter A' },
        { role: 'assistant', content: EXAMPLE_A },
        { role: 'user', content: userPrompt }
      ],
      max_tokens: 300,
      temperature: 0.4
    });
    const req = https.request({
      hostname: 'api.openai.com',
      path: '/v1/chat/completions',
      method: 'POST',
      timeout: 20000,
      headers: {
        'Content-Type': 'application/json',
        'Content-Length': Buffer.byteLength(body),
        'Authorization': `Bearer ${apiKey}`
      }
    }, (res) => {
      let data = '';
      res.on('data', chunk => data += chunk);
      res.on('end', () => { try { resolve(JSON.parse(data)); } catch (e) { reject(e); } });
    });
    req.on('timeout', () => { req.destroy(new Error('OpenAI request timed out')); });
    req.on('error', reject);
    req.write(body);
    req.end();
  });
}

function callClaude(userPrompt, apiKey) {
  return new Promise((resolve, reject) => {
    const body = JSON.stringify({
      model: 'claude-haiku-4-5-20251001',
      max_tokens: 300,
      system: AI_SYSTEM_PROMPT,
      messages: [
        { role: 'user', content: 'a heart' },
        { role: 'assistant', content: EXAMPLE_HEART },
        { role: 'user', content: 'letter A' },
        { role: 'assistant', content: EXAMPLE_A },
        { role: 'user', content: userPrompt }
      ]
    });
    const req = https.request({
      hostname: 'api.anthropic.com',
      path: '/v1/messages',
      method: 'POST',
      timeout: 20000,
      headers: {
        'Content-Type': 'application/json',
        'Content-Length': Buffer.byteLength(body),
        'x-api-key': apiKey,
        'anthropic-version': '2023-06-01'
      }
    }, (res) => {
      let data = '';
      res.on('data', chunk => data += chunk);
      res.on('end', () => { try { resolve(JSON.parse(data)); } catch (e) { reject(e); } });
    });
    req.on('timeout', () => { req.destroy(new Error('Claude request timed out')); });
    req.on('error', reject);
    req.write(body);
    req.end();
  });
}

app.post('/:uuid/api/generate', async (req, res) => {
  const { uuid } = req.params;
  const { prompt } = req.body;
  if (!prompt) return res.status(400).json({ success: false, error: 'Prompt required' });
  const config = aiConfigs.get(uuid);
  if (!config || !config.enabled || !config.key) {
    return res.status(403).json({ success: false, error: 'AI not configured on phone' });
  }
  try {
    let text = '';
    if (config.provider === 'claude') {
      const data = await callClaude(prompt, config.key);
      console.log(`[generate] Claude response:`, JSON.stringify(data).slice(0, 200));
      text = data.content?.[0]?.text?.trim() || '';
    } else {
      const data = await callOpenAI(prompt, config.key);
      console.log(`[generate] OpenAI response:`, JSON.stringify(data).slice(0, 200));
      text = data.choices?.[0]?.message?.content?.trim() || '';
    }
    const flat = text.replace(/\s+/g, '');
    const match = flat.match(/[01]{169}/);
    if (!match) return res.json({ success: false, error: 'Could not parse AI response' });
    let pixels = '';
    for (let i = 0; i < 169; i++) pixels += PHONE_MASK_FLAT[i] === '1' ? '0' : match[0][i];
    res.json({ success: true, pixels });
  } catch (e) {
    console.error('AI error:', e.message);
    res.status(500).json({ success: false, error: 'AI request failed' });
  }
});

function readFavs() {
  try { return JSON.parse(fs.readFileSync(FAV_FILE, 'utf8')); } catch { return {}; }
}
function writeFavs(data) {
  fs.writeFileSync(FAV_FILE, JSON.stringify(data));
}

// Store connected phones keyed by UUID
const phones = new Map(); // uuid -> { ws, ip }
// Store browser stream clients keyed by UUID
const streams = new Map(); // uuid -> Set<WebSocket>
// Track active toy per phone
const activeToys = new Map(); // uuid -> toyId
// Pending messages awaiting ack: uuid -> Map<msgId, {payload, retries, timer}>
const pending = new Map();
// Latest frame per phone — only the most recent is forwarded to browsers
const latestFrames = new Map(); // uuid -> frame text

const MAX_RETRIES = 3;
const RETRY_MS = 4000;
let msgCounter = 0;

function nextMsgId() { return String(++msgCounter); }

function schedulePendingRetry(uuid, msgId) {
  const queue = pending.get(uuid);
  if (!queue) return;
  const entry = queue.get(msgId);
  if (!entry) return;
  entry.timer = setTimeout(() => {
    const q = pending.get(uuid);
    const e = q?.get(msgId);
    if (!e) return;
    e.retries++;
    if (e.retries >= MAX_RETRIES) {
      console.log(`[${uuid}] msg ${msgId} unacked after ${MAX_RETRIES} retries, dropping`);
      q.delete(msgId);
      return;
    }
    const phone = phones.get(uuid);
    if (phone && phone.ws.readyState === WebSocket.OPEN) {
      console.log(`[${uuid}] Retrying msg ${msgId} (attempt ${e.retries})`);
      phone.ws.send(e.payload);
    }
    schedulePendingRetry(uuid, msgId);
  }, RETRY_MS);
}

function enqueueMessage(uuid, buildPayload) {
  const msgId = nextMsgId();
  const payload = buildPayload(msgId);
  if (!pending.has(uuid)) pending.set(uuid, new Map());
  pending.get(uuid).set(msgId, { payload, retries: 0, timer: null });
  const phone = phones.get(uuid);
  if (phone && phone.ws.readyState === WebSocket.OPEN) {
    phone.ws.send(payload);
    schedulePendingRetry(uuid, msgId);
  }
  return msgId;
}

function flushPending(uuid) {
  const queue = pending.get(uuid);
  if (!queue || queue.size === 0) return;
  const phone = phones.get(uuid);
  if (!phone || phone.ws.readyState !== WebSocket.OPEN) return;
  console.log(`[${uuid}] Flushing ${queue.size} pending message(s)`);
  for (const [msgId, entry] of queue) {
    clearTimeout(entry.timer);
    entry.retries = 0;
    phone.ws.send(entry.payload);
    schedulePendingRetry(uuid, msgId);
  }
}

function ackMessage(uuid, msgId) {
  const queue = pending.get(uuid);
  if (!queue) return;
  const entry = queue.get(msgId);
  if (!entry) return;
  clearTimeout(entry.timer);
  queue.delete(msgId);
  console.log(`[${uuid}] Ack received for msg ${msgId}`);
}

// ── WebSocket connection from phone ───────────────────────────────────────────
wss.on('connection', (ws, req) => {
  const parsed = new URL(req.url, 'http://localhost');

  // Browser stream proxy: /stream/<uuid>
  if (parsed.pathname.startsWith('/stream/')) {
    const uuid = parsed.pathname.split('/')[2];
    if (!streams.has(uuid)) streams.set(uuid, new Set());
    streams.get(uuid).add(ws);
    console.log(`[${uuid}] Browser stream client connected (${streams.get(uuid).size} total)`);
    ws.on('close', () => {
      streams.get(uuid)?.delete(ws);
      console.log(`[${uuid}] Browser stream client disconnected`);
    });
    return;
  }

  // Phone connection: /?id=<uuid>
  const uuid = parsed.searchParams.get('id');
  if (!uuid) {
    console.log('Rejected connection: no id provided');
    ws.close(4000, 'Missing id');
    return;
  }

  const existing = phones.get(uuid);
  if (existing) existing.ws.close(4001, 'Replaced');

  const ip = req.socket.remoteAddress.replace(/^::ffff:/, '');
  phones.set(uuid, { ws, ip });
  console.log(`Phone connected: ${uuid} from ${ip}`);
  flushPending(uuid);

  ws.on('message', (message) => {
    try {
      const data = JSON.parse(message);
      if (data.type === 'ping') ws.send(JSON.stringify({ type: 'pong' }));
      else if (data.type === 'ack') ackMessage(uuid, data.id);
      else if (data.type === 'ai_config') {
        aiConfigs.set(uuid, { enabled: !!data.enabled, provider: data.provider || 'openai', key: data.key || '' });
        console.log(`[${uuid}] AI config updated: enabled=${data.enabled}, provider=${data.provider}`);
      } else if (data.type === 'frame') {
        const clients = streams.get(uuid);
        if (clients && clients.size > 0) {
          latestFrames.set(uuid, message.toString());
        }
      }
    } catch (e) {
      console.error('Error parsing message:', e);
    }
  });

  ws.on('close', () => {
    if (phones.get(uuid)?.ws === ws) {
      phones.delete(uuid);
      aiConfigs.delete(uuid);
      activeToys.delete(uuid);
      latestFrames.delete(uuid);
    }
    console.log(`Phone disconnected: ${uuid}`);
  });

  ws.on('error', (error) => {
    console.error(`WebSocket error for ${uuid}:`, error);
    if (phones.get(uuid)?.ws === ws) phones.delete(uuid);
  });
});

// ── UUID-scoped routes ────────────────────────────────────────────────────────

app.get('/:uuid/manifest.json', (req, res) => {
  const { uuid } = req.params;
  res.json({
    name: 'hi! glyph',
    short_name: 'hi! glyph',
    start_url: `/${uuid}`,
    display: 'standalone',
    background_color: '#000000',
    theme_color: '#000000',
    icons: [
      { src: '/icons/android-icon-36x36.png',  sizes: '36x36',   type: 'image/png' },
      { src: '/icons/android-icon-48x48.png',  sizes: '48x48',   type: 'image/png' },
      { src: '/icons/android-icon-72x72.png',  sizes: '72x72',   type: 'image/png' },
      { src: '/icons/android-icon-96x96.png',  sizes: '96x96',   type: 'image/png' },
      { src: '/icons/android-icon-144x144.png', sizes: '144x144', type: 'image/png' },
      { src: '/icons/android-icon-192x192.png', sizes: '192x192', type: 'image/png', purpose: 'any maskable' },
    ]
  });
});

app.get('/:uuid', (req, res) => {
  const { uuid } = req.params;
  const html = fs.readFileSync(path.join(__dirname, 'public', 'index.html'), 'utf8');
  const injected = html
    .replace('<head>', `<head><base href="/"><link rel="manifest" href="/${uuid}/manifest.json">`)
    .replace('<script>', `<script>window.PHONE_ID=${JSON.stringify(uuid)};\n`);
  res.setHeader('Content-Type', 'text/html');
  res.send(injected);
});

app.get('/:uuid/api/status', (req, res) => {
  const entry = phones.get(req.params.uuid);
  res.json({
    connected: entry && entry.ws.readyState === WebSocket.OPEN ? 1 : 0,
    phoneIp: entry ? entry.ip : null,
    uptime: process.uptime(),
    aiEnabled: aiConfigs.get(req.params.uuid)?.enabled ?? false,
    activeToy: activeToys.get(req.params.uuid) ?? null
  });
});

app.get('/:uuid/api/favorites', (req, res) => {
  const data = readFavs();
  res.json({ favorites: data[req.params.uuid] || [] });
});

app.post('/:uuid/api/favorites', rateLimit(apiLimiter), (req, res) => {
  const { uuid } = req.params;
  const { pixels } = req.body;
  if (!pixels || pixels.length !== 169 || !/^[01]+$/.test(pixels))
    return res.status(400).json({ success: false, error: 'Invalid pixels' });
  const data = readFavs();
  const favs = data[uuid] || [];
  if (favs[0] !== pixels) {
    favs.unshift(pixels);
    if (favs.length > 50) favs.length = 50;
    data[uuid] = favs;
    writeFavs(data);
  }
  res.json({ success: true, favorites: data[uuid] });
});

app.delete('/:uuid/api/favorites/:idx', (req, res) => {
  const { uuid, idx } = req.params;
  const data = readFavs();
  const favs = data[uuid] || [];
  favs.splice(Number(idx), 1);
  data[uuid] = favs;
  writeFavs(data);
  res.json({ success: true, favorites: favs });
});

app.post('/:uuid/api/send', rateLimit(apiLimiter), (req, res) => {
  const { uuid } = req.params;
  const { text, speed } = req.body;

  if (!text) return res.status(400).json({ success: false, error: 'Text is required' });
  if (text.length > 100) return res.status(400).json({ success: false, error: 'Message too long (max 100 chars)' });

  const msgId = enqueueMessage(uuid, (id) => JSON.stringify({ type: 'message', id, text, speed: speed || 20 }));
  console.log(`[${uuid}] Queued message ${msgId}: "${text}"`);
  res.json({ success: true });
});

app.post('/:uuid/api/draw', rateLimit(apiLimiter), (req, res) => {
  const { uuid } = req.params;
  const { pixels } = req.body;

  if (!pixels || pixels.length !== 169 || !/^[01]+$/.test(pixels)) {
    return res.status(400).json({ success: false, error: 'pixels must be a 169-char binary string' });
  }

  const entry = phones.get(uuid);
  if (!entry || entry.ws.readyState !== WebSocket.OPEN) {
    return res.status(503).json({ success: false, error: 'Phone not connected' });
  }

  entry.ws.send(JSON.stringify({ type: 'draw', pixels }));
  console.log(`[${uuid}] Sent draw frame`);
  res.json({ success: true });
});

app.post('/:uuid/api/toy/start', rateLimit(apiLimiter), (req, res) => {
  const { uuid } = req.params;
  const { id } = req.body;
  if (!id) return res.status(400).json({ success: false, error: 'id required' });
  const phone = phones.get(uuid);
  if (!phone || phone.ws.readyState !== WebSocket.OPEN)
    return res.status(503).json({ success: false, error: 'Phone not connected' });
  phone.ws.send(JSON.stringify({ type: 'toy', action: 'start', toyId: id }));
  activeToys.set(uuid, id);
  console.log(`[${uuid}] Toy start: ${id}`);
  res.json({ success: true });
});

app.post('/:uuid/api/toy/stop', rateLimit(apiLimiter), (req, res) => {
  const { uuid } = req.params;
  const phone = phones.get(uuid);
  if (phone && phone.ws.readyState === WebSocket.OPEN)
    phone.ws.send(JSON.stringify({ type: 'toy', action: 'stop' }));
  activeToys.delete(uuid);
  res.json({ success: true });
});

// ── Root landing page ("hi!") ────────────────────────────────────────────────

const HI_PIXELS =
  '0000000000000' +
  '0000000000000' +
  '0000000000000' +
  '0000000000000' +
  '0001000101000' +
  '0001000001000' +
  '0001100101000' +
  '0001010100000' +
  '0001010101000' +
  '0000000000000' +
  '0000000000000' +
  '0000000000000' +
  '0000000000000';

const PHONE_MASK = [
  'xxxx.....xxxx',
  'xx.........xx',
  'x...........x',
  'x...........x',
  '.............',
  '.............',
  '.............',
  '.............',
  '.............',
  'x...........x',
  'x...........x',
  'xx.........xx',
  'xxxx.....xxxx',
];

app.get('/', (req, res) => {
  res.setHeader('Content-Type', 'text/html');
  res.send(`<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>hi! glyph</title>
  <style>
    * { margin: 0; padding: 0; box-sizing: border-box; }
    body { background: #000; min-height: 100vh; display: flex; align-items: center; justify-content: center; }
  </style>
</head>
<body>
  <canvas id="c" width="325" height="325" style="width:325px;height:325px"></canvas>
  <script>
    const MASK = ${JSON.stringify(PHONE_MASK)};
    const PIXELS = '${HI_PIXELS}';
    const canvas = document.getElementById('c');
    const ctx = canvas.getContext('2d');
    const size = 325, cellSize = size / 13, gap = cellSize * 0.15, pixelSize = cellSize - gap;
    ctx.fillStyle = '#000';
    ctx.fillRect(0, 0, size, size);
    for (let y = 0; y < 13; y++) {
      for (let x = 0; x < 13; x++) {
        if (MASK[y][x] === 'x') continue;
        const on = PIXELS[y * 13 + x] === '1';
        ctx.fillStyle = on ? '#ffffff' : '#1c1c1c';
        ctx.fillRect(x * cellSize + gap / 2, y * cellSize + gap / 2, pixelSize, pixelSize);
      }
    }
  </script>
</body>
</html>`);
});

const PORT = process.env.PORT || 3000;
server.listen(PORT, '0.0.0.0', () => {
  console.log(`hi! glyph relay server running on port ${PORT}`);
  console.log(`Web UI: http://localhost:${PORT}/<phone-id>`);
  console.log(`Phone WebSocket: ws://localhost:${PORT}?id=<phone-id>`);
});

// Flush latest simulation frame to browser stream clients at 50ms intervals
setInterval(() => {
  for (const [uuid, text] of latestFrames) {
    latestFrames.delete(uuid);
    const clients = streams.get(uuid);
    if (!clients || clients.size === 0) continue;
    clients.forEach(client => {
      if (client.readyState === WebSocket.OPEN) client.send(text);
    });
  }
}, 50);

// Keep browser stream connections alive through nginx
setInterval(() => {
  streams.forEach((clients) => {
    clients.forEach(client => {
      if (client.readyState === WebSocket.OPEN) client.ping();
    });
  });
}, 30000);

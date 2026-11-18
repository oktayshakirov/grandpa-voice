# Token broker (optional)

The simplest setup stores the OpenAI key on the phone (see the main README). Run this broker instead when
the permanent key must never be on a phone, for example with several phones or a public release.

A ~200-line Node 20 server with no dependencies. Endpoints:

- `GET /health` → `{ ok: true, model }`
- `POST /session` (header `Authorization: Bearer <DEVICE_TOKEN>`) → `{ value: "ek_…", expires_at, model }`.
  It may also return `calls_url` to send the WebRTC call somewhere other than OpenAI. Only the test server in
  `tools/mock-realtime` does that.

It calls `POST https://api.openai.com/v1/realtime/client_secrets` with your real key and returns a key that is valid
for 60 seconds — long enough for the phone to open its WebRTC call (the call itself then continues normally).

## Configuration (environment variables)

| Variable | Required | Meaning |
|---|---|---|
| `OPENAI_API_KEY` | yes | Your OpenAI key (`sk-…`). Never put it in the app. |
| `DEVICE_TOKENS` | yes | Comma-separated list; one long random token per phone (24+ chars). |
| `REALTIME_MODEL` | no | Default `gpt-realtime-1.5`. |
| `ALLOWED_MODELS` | no | Models a phone may choose in its settings. Default `gpt-realtime-1.5,gpt-realtime-2.1-mini`. |
| `MAX_SESSIONS_PER_HOUR` | no | Per device, default 20. |
| `PORT` | no | Default 8787 (most hosts set this for you). |

Generate a device token:

```bash
node -e "console.log(require('crypto').randomBytes(32).toString('base64url'))"
```

## Run locally

```bash
cd backend
OPENAI_API_KEY=sk-... DEVICE_TOKENS=your-token node server.js
curl localhost:8787/health
curl -X POST localhost:8787/session -H "Authorization: Bearer your-token" -d '{}'
```

The phone needs **HTTPS**. For a quick test from a home PC, a Cloudflare quick tunnel gives you an https URL:

```bash
cloudflared tunnel --url http://localhost:8787
```

## Deploy (pick one)

- **Render / Railway:** create a new Web Service from this folder (Node, start command `node server.js`),
  add the environment variables, choose an always-on instance. Use the https URL they give you.
- **Fly.io / any Docker host:** `docker build -t grandpa-broker . && docker run -p 8787:8787 --env-file .env grandpa-broker`
  behind HTTPS (Fly provides it; on a VPS put Caddy in front: `caddy reverse-proxy --from your.domain --to :8787`).

## Checking it works with OpenAI

After deploying, run the `curl -X POST …/session` command above against your URL. A JSON response containing
`"value":"ek_…"` means your key, billing and model are fine. A `502` means OpenAI refused — the server log shows why
(wrong key, no billing, unknown model name).

If the log shows OpenAI rejecting a session parameter (the Realtime API evolves), the session config is built in
one place — `createSession()` in `server.js` — so a fix is usually a one-line change there (for example removing
`speed` or switching `noise_reduction` to `near_field`). No new APK is needed for backend-side changes.

// Grandpa Voice – token broker.
// Holds the permanent OpenAI API key and hands the phone a short-lived (60 s) Realtime key.
// Zero dependencies: Node 20+ only. Logs never contain conversation content.

import http from "node:http";
import crypto from "node:crypto";

const PORT = Number(process.env.PORT || 8787);
const OPENAI_API_KEY = process.env.OPENAI_API_KEY || "";
const DEVICE_TOKENS = (process.env.DEVICE_TOKENS || "")
  .split(",")
  .map((t) => t.trim())
  .filter(Boolean);
// Default model, and the models a phone may ask for (it sends the one chosen in its settings).
const MODEL = process.env.REALTIME_MODEL || "gpt-realtime-1.5";
const ALLOWED_MODELS = new Set(
  (process.env.ALLOWED_MODELS || `${MODEL},gpt-realtime-1.5,gpt-realtime-2.1-mini`)
    .split(",")
    .map((m) => m.trim())
    .filter(Boolean),
);
const MAX_SESSIONS_PER_HOUR = Number(process.env.MAX_SESSIONS_PER_HOUR || 20);
const KEY_TTL_SECONDS = 60; // only needs to live long enough to open the WebRTC call
const MAX_INSTRUCTIONS_CHARS = 8000;

const ALLOWED_VOICES = new Set([
  "marin", "cedar", "alloy", "ash", "ballad", "coral", "echo", "sage", "shimmer", "verse",
]);

if (!OPENAI_API_KEY) {
  console.error("OPENAI_API_KEY is not set.");
  process.exit(1);
}
if (DEVICE_TOKENS.length === 0 || DEVICE_TOKENS.some((t) => t.length < 24)) {
  console.error("DEVICE_TOKENS must contain at least one token of 24+ characters (comma-separated).");
  process.exit(1);
}

const FALLBACK_INSTRUCTIONS = `Ти си приятелски настроен, търпелив и сърдечен събеседник за възрастен човек.
Говори естествено, топло и спокойно, винаги и само на български език.
Използвай кратки и лесни за разбиране изречения. Никога не го пришпорвай.
Ако не разбираш нещо, учтиво помоли човека да повтори.
Когато човекът иска да приключи разговора, кажи кратко довиждане и извикай функцията end_conversation.`;

const END_TOOL = {
  type: "function",
  name: "end_conversation",
  description:
    "Приключва разговора. Извикай я само когато човекът ясно иска да приключи разговора и вече си му казал довиждане.",
  parameters: { type: "object", properties: {}, additionalProperties: false },
};

function turnDetection(mode, allowInterrupt) {
  const interrupt = allowInterrupt !== false;
  switch (mode) {
    case "semantic_medium":
      return { type: "semantic_vad", eagerness: "medium", create_response: true, interrupt_response: interrupt };
    case "server_patient":
      return {
        type: "server_vad",
        threshold: 0.6,
        prefix_padding_ms: 400,
        silence_duration_ms: 1200,
        create_response: true,
        interrupt_response: interrupt,
      };
    case "semantic_low":
    default:
      // "low" = waits longer before deciding he has finished; best for slow speech and pauses.
      return { type: "semantic_vad", eagerness: "low", create_response: true, interrupt_response: interrupt };
  }
}

function safeEqual(a, b) {
  const ab = Buffer.from(a);
  const bb = Buffer.from(b);
  return ab.length === bb.length && crypto.timingSafeEqual(ab, bb);
}

function authorize(req) {
  const header = req.headers.authorization || "";
  const token = header.startsWith("Bearer ") ? header.slice(7).trim() : "";
  if (!token) return null;
  return DEVICE_TOKENS.find((t) => safeEqual(t, token)) || null;
}

const usage = new Map(); // device token -> timestamps of issued sessions
function rateLimited(token) {
  const now = Date.now();
  const recent = (usage.get(token) || []).filter((t) => now - t < 3_600_000);
  if (recent.length >= MAX_SESSIONS_PER_HOUR) {
    usage.set(token, recent);
    return true;
  }
  recent.push(now);
  usage.set(token, recent);
  return false;
}

function readJson(req, limit = 32 * 1024) {
  return new Promise((resolve, reject) => {
    let size = 0;
    const chunks = [];
    req.on("data", (c) => {
      size += c.length;
      if (size > limit) {
        reject(new Error("body too large"));
        req.destroy();
      } else chunks.push(c);
    });
    req.on("end", () => {
      if (chunks.length === 0) return resolve({});
      try {
        resolve(JSON.parse(Buffer.concat(chunks).toString("utf8")));
      } catch {
        reject(new Error("invalid json"));
      }
    });
    req.on("error", reject);
  });
}

function send(res, status, body) {
  res.writeHead(status, { "Content-Type": "application/json", "Cache-Control": "no-store" });
  res.end(JSON.stringify(body));
}

function log(msg) {
  console.log(`[${new Date().toISOString()}] ${msg}`);
}

function deviceLabel(token) {
  return crypto.createHash("sha256").update(token).digest("hex").slice(0, 8);
}

async function createSession(body) {
  const instructions =
    typeof body.instructions === "string" && body.instructions.trim()
      ? body.instructions.slice(0, MAX_INSTRUCTIONS_CHARS)
      : FALLBACK_INSTRUCTIONS;
  const voice = ALLOWED_VOICES.has(body.voice) ? body.voice : "marin";
  const model = ALLOWED_MODELS.has(body.model) ? body.model : MODEL;
  const speedRaw = Number(body.speed);
  const speed = Number.isFinite(speedRaw) ? Math.min(1.2, Math.max(0.7, speedRaw)) : 1.0;

  const session = {
    type: "realtime",
    model,
    instructions,
    output_modalities: ["audio"],
    audio: {
      input: {
        noise_reduction: { type: "far_field" }, // phone lying on a table, not held to the ear
        turn_detection: turnDetection(body.vad, body.allowInterrupt),
      },
      output: { voice, speed },
    },
    tools: [END_TOOL],
    tool_choice: "auto",
  };

  const r = await fetch("https://api.openai.com/v1/realtime/client_secrets", {
    signal: AbortSignal.timeout(15_000),
    method: "POST",
    headers: {
      Authorization: `Bearer ${OPENAI_API_KEY}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({
      expires_after: { anchor: "created_at", seconds: KEY_TTL_SECONDS },
      session,
    }),
  });
  const data = await r.json().catch(() => ({}));
  if (!r.ok) {
    const err = new Error(`OpenAI ${r.status}: ${data?.error?.code || ""} ${data?.error?.message || ""}`.trim());
    err.upstreamStatus = r.status;
    err.noCredit = data?.error?.type === "insufficient_quota" ||
      ["insufficient_quota", "credit_balance_exhausted"].includes(data?.error?.code);
    throw err;
  }
  const value = data.value || data.client_secret?.value;
  if (!value) throw new Error("OpenAI response had no client secret");
  return { value, expires_at: data.expires_at ?? data.client_secret?.expires_at ?? null, model, voice };
}

const server = http.createServer(async (req, res) => {
  try {
    const path = (req.url || "/").split("?")[0];

    if (req.method === "GET" && path === "/health") return send(res, 200, { ok: true, model: MODEL });

    if (req.method === "POST" && path === "/session") {
      const token = authorize(req);
      if (!token) {
        log("rejected: bad device token");
        return send(res, 401, { error: "unauthorized" });
      }
      if (rateLimited(token)) {
        log(`rate limited: device ${deviceLabel(token)}`);
        return send(res, 429, { error: "rate_limited" });
      }
      let body;
      try {
        body = await readJson(req);
      } catch (e) {
        return send(res, 400, { error: e.message });
      }
      try {
        const result = await createSession(body);
        log(`session issued: device ${deviceLabel(token)}, model ${result.model}, voice ${result.voice}`);
        return send(res, 200, result);
      } catch (e) {
        log(`upstream error: ${e.message}`);
        // Pass "no credit" through so the phone can say so; everything else is a generic upstream failure.
        if (e.noCredit) return send(res, 402, { error: "no_credit" });
        const status = e.upstreamStatus === 429 ? 429 : 502;
        return send(res, status, { error: "upstream" });
      }
    }

    send(res, 404, { error: "not_found" });
  } catch (e) {
    log(`internal error: ${e.message}`);
    if (!res.headersSent) send(res, 500, { error: "internal" });
  }
});

server.listen(PORT, () => log(`Grandpa Voice broker listening on :${PORT} (model ${MODEL})`));

"""
Mock OpenAI Realtime server - test the whole app without an OpenAI account or credits.

It plays the part of both the token broker (POST /session) and OpenAI's WebRTC endpoint
(POST /v1/realtime/calls). The phone does a real WebRTC call to it: microphone audio flows
up, scripted English speech (macOS "Samantha" voice) flows back, and the same JSON events OpenAI sends
go over the "oai-events" data channel. So shake detection, audio routing, the conversation
state machine, ending by "goodbye" and error handling are all exercised for real.

A live session is needed to test voice recognition and multilingual responses.

Scripted conversation (default):
  greeting -> (you speak, or 3 s pass) -> reply -> (you speak, or 3 s pass) -> goodbye + end_conversation

Run:   python3 mock_server.py                (listens on 0.0.0.0:8788)
App:   Settings -> "Your own server"
         address: http://10.0.2.2:8788       (emulator; a real phone on Wi-Fi: http://<this computer's IP>:8788,
                                              debug APK only, since release builds require https)
         device token: mock-device-token-for-local-testing

Options (environment variables):
  MOCK_PORT=8788
  MOCK_TOKEN=mock-device-token-for-local-testing
  MOCK_FAIL=insufficient_quota | unauthorized | rate_limited   simulate an OpenAI error
  MOCK_TURNS=2          user turns before the mock says goodbye
"""

import asyncio
import fractions
import json
import logging
import math
import os
import shutil
import struct
import subprocess
import sys
import time
import uuid
import wave
from pathlib import Path

from aiohttp import web
from aiortc import MediaStreamTrack, RTCPeerConnection, RTCSessionDescription
from aiortc.mediastreams import MediaStreamError
from av import AudioFrame

PORT = int(os.environ.get("MOCK_PORT", "8788"))
TOKEN = os.environ.get("MOCK_TOKEN", "mock-device-token-for-local-testing")
FAIL = os.environ.get("MOCK_FAIL", "").strip()
TURNS = int(os.environ.get("MOCK_TURNS", "2"))
SILENCE_BEFORE_FAKE_TURN_S = 3.0
SPEECH_RMS = 500  # int16 RMS above which incoming audio counts as speech

RATE = 48000
FRAME = 960  # 20 ms
AUDIO_DIR = Path(__file__).parent / "audio" / "english"

LINES = {
    "greeting": "Hello! Good to hear you. How are you today?",
    "reply1": "That's great. Tell me more when you're ready.",
    "reply2": "Interesting! What are you doing today?",
    "goodbye": "It was nice talking with you. Goodbye!",
}

log = logging.getLogger("mock")
logging.basicConfig(level=logging.INFO, format="%(asctime)s %(message)s", datefmt="%H:%M:%S")
for noisy in ("aioice", "aiortc", "aiohttp.access"):
    logging.getLogger(noisy).setLevel(logging.WARNING)


# ------------------------------------------------------------------ audio clips

def make_clips() -> dict:
    AUDIO_DIR.mkdir(parents=True, exist_ok=True)
    clips = {}
    for name, text in LINES.items():
        path = AUDIO_DIR / f"{name}.wav"
        if not path.exists():
            if shutil.which("say") and shutil.which("afconvert"):
                aiff = AUDIO_DIR / f"{name}.aiff"
                subprocess.run(["say", "-v", "Samantha", "-o", str(aiff), text], check=True)
                subprocess.run(["afconvert", "-f", "WAVE", "-d", f"LEI16@{RATE}", "-c", "1", str(aiff), str(path)], check=True)
                aiff.unlink()
            else:  # not on a Mac: a short two-tone chime stands in for speech
                write_tone(path, seconds=2.0)
        with wave.open(str(path)) as w:
            assert w.getframerate() == RATE and w.getnchannels() == 1 and w.getsampwidth() == 2, path
            clips[name] = w.readframes(w.getnframes())
    return clips


def write_tone(path: Path, seconds: float):
    n = int(RATE * seconds)
    data = b"".join(
        struct.pack("<h", int(8000 * math.sin(2 * math.pi * (440 if i < n // 2 else 660) * i / RATE)))
        for i in range(n)
    )
    with wave.open(str(path), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes(data)


CLIPS = make_clips()


class SpeakerTrack(MediaStreamTrack):
    """Outgoing audio: silence, or whatever clip is queued. Calls on_done when a clip finishes."""

    kind = "audio"

    def __init__(self):
        super().__init__()
        self.buffer = b""
        self.on_done = None
        self._pts = 0
        self._start = None

    def play(self, pcm: bytes, on_done):
        self.buffer = pcm
        self.on_done = on_done

    def clear(self):
        self.buffer = b""
        self.on_done = None

    async def recv(self):
        if self._start is None:
            self._start = time.time()
        wait = self._start + self._pts / RATE - time.time()
        if wait > 0:
            await asyncio.sleep(wait)

        need = FRAME * 2
        chunk, self.buffer = self.buffer[:need], self.buffer[need:]
        if self.on_done and not self.buffer:
            done, self.on_done = self.on_done, None
            asyncio.get_event_loop().call_soon(done)
        chunk = chunk.ljust(need, b"\0")

        frame = AudioFrame(format="s16", layout="mono", samples=FRAME)
        frame.planes[0].update(chunk)
        frame.sample_rate = RATE
        frame.pts = self._pts
        frame.time_base = fractions.Fraction(1, RATE)
        self._pts += FRAME
        return frame


# ------------------------------------------------------------------ one call

class Call:
    def __init__(self, pc: RTCPeerConnection):
        self.id = "rtc_" + uuid.uuid4().hex[:8]
        self.pc = pc
        self.speaker = SpeakerTrack()
        self.channel = None
        self.speaking = False
        self.user_turns = 0
        self.frames_in = 0
        self.peak_rms = 0
        self.last_voice = 0.0
        self.user_talking = False
        self.events_in = []
        self.fake_turn_task = None
        self.closed = False

    # ---- events to the phone
    def send(self, type_, **fields):
        if self.channel and self.channel.readyState == "open":
            self.channel.send(json.dumps({"type": type_, "event_id": "evt_" + uuid.uuid4().hex[:10], **fields}))

    def respond(self, clip: str, end_call: bool = False):
        if self.closed:
            return
        self.cancel_fake_turn()
        rid = "resp_" + uuid.uuid4().hex[:8]
        self.send("response.created", response={"id": rid, "status": "in_progress"})
        self.speaking = True
        self.send("output_audio_buffer.started", response_id=rid)
        log.info("[%s] AI says (%s): %s", self.id, clip, LINES[clip])

        def done():
            self.speaking = False
            self.send("output_audio_buffer.stopped", response_id=rid)
            output = [{"type": "message", "role": "assistant", "content": [{"type": "output_audio", "transcript": LINES[clip]}]}]
            if end_call:
                output.append({"type": "function_call", "name": "end_conversation", "call_id": "call_" + uuid.uuid4().hex[:8], "arguments": "{}"})
                log.info("[%s] AI calls end_conversation", self.id)
            self.send("response.done", response={"id": rid, "status": "completed", "output": output})
            if not end_call:
                self.schedule_fake_turn()

        self.speaker.play(CLIPS[clip], done)

    # ---- user turns: real speech if the mic hears any, otherwise simulated after a pause
    def schedule_fake_turn(self):
        self.cancel_fake_turn()

        async def fake():
            await asyncio.sleep(SILENCE_BEFORE_FAKE_TURN_S)
            if self.user_talking or self.speaking or self.closed:
                return
            log.info("[%s] (simulating the user speaking - mic heard nothing louder than RMS %d)", self.id, self.peak_rms)
            self.send("input_audio_buffer.speech_started", audio_start_ms=0, item_id="item_fake")
            await asyncio.sleep(2.0)
            self.send("input_audio_buffer.speech_stopped", audio_end_ms=2000, item_id="item_fake")
            self.user_turn_finished()

        self.fake_turn_task = asyncio.ensure_future(fake())

    def cancel_fake_turn(self):
        if self.fake_turn_task and not self.fake_turn_task.done():
            self.fake_turn_task.cancel()

    def user_turn_finished(self):
        self.user_turns += 1
        if self.user_turns >= TURNS:
            self.respond("goodbye", end_call=True)
        else:
            self.respond(f"reply{min(self.user_turns, 2)}")

    async def listen(self, track):
        """Consume the phone's microphone; a crude VAD turns loud audio into speech events."""
        try:
            while True:
                frame = await track.recv()
                self.frames_in += 1
                pcm = bytes(frame.planes[0])
                samples = struct.unpack(f"<{len(pcm) // 2}h", pcm[: len(pcm) // 2 * 2])
                rms = int(math.sqrt(sum(s * s for s in samples) / max(1, len(samples))))
                self.peak_rms = max(self.peak_rms, rms)
                now = time.time()
                if self.frames_in % 250 == 0:
                    log.info("[%s] mic: %d frames received (%.0f s), peak RMS %d", self.id, self.frames_in, self.frames_in * 0.02, self.peak_rms)
                if rms > SPEECH_RMS and not self.speaking:
                    self.last_voice = now
                    if not self.user_talking:
                        self.user_talking = True
                        self.cancel_fake_turn()
                        log.info("[%s] user started speaking (RMS %d)", self.id, rms)
                        self.send("input_audio_buffer.speech_started", audio_start_ms=0, item_id="item_real")
                elif self.user_talking and now - self.last_voice > 1.2:
                    self.user_talking = False
                    log.info("[%s] user stopped speaking", self.id)
                    self.send("input_audio_buffer.speech_stopped", audio_end_ms=0, item_id="item_real")
                    self.user_turn_finished()
        except MediaStreamError:
            pass

    # ---- events from the phone
    def on_client_event(self, raw: str):
        try:
            event = json.loads(raw)
        except ValueError:
            return
        t = event.get("type")
        self.events_in.append(t)
        if t == "response.create":
            instructions = (event.get("response") or {}).get("instructions", "")
            last_line = instructions.strip().splitlines()[-1] if instructions.strip() else ""
            log.info("[%s] phone asked for a response: ...%s", self.id, last_line[-90:])
            if instructions.startswith(("Greet ", "Разговорът тъкмо започва")):
                self.respond("greeting")
            else:  # a farewell request (inactivity, time limit, or after end_conversation without words)
                self.respond("goodbye")
        elif t == "conversation.item.create":
            log.info("[%s] phone sent %s", self.id, (event.get("item") or {}).get("type"))
        else:
            log.info("[%s] phone sent %s", self.id, t)


CALLS = set()


# ------------------------------------------------------------------ HTTP

def bearer(request) -> str:
    h = request.headers.get("Authorization", "")
    return h[7:].strip() if h.startswith("Bearer ") else ""


def openai_error(status, code, message):
    return web.json_response({"error": {"type": "invalid_request_error", "code": code, "message": message}}, status=status)


async def health(request):
    return web.json_response({"ok": True, "mock": True, "active_calls": len(CALLS)})


async def session(request):
    if bearer(request) != TOKEN:
        log.info("rejected /session: wrong device token")
        return web.json_response({"error": "unauthorized"}, status=401)
    body = await request.json()
    log.info(
        "session requested: model=%s voice=%s speed=%s vad=%s interrupt=%s, %d chars of instructions",
        body.get("model"), body.get("voice"), body.get("speed"), body.get("vad"), body.get("allowInterrupt"),
        len(body.get("instructions", "")),
    )
    if FAIL == "unauthorized":
        return web.json_response({"error": "unauthorized"}, status=401)
    if FAIL == "rate_limited":
        return web.json_response({"error": "rate_limited"}, status=429)
    host = request.headers.get("Host", f"localhost:{PORT}")
    return web.json_response({
        "value": "ek_mock_" + uuid.uuid4().hex,
        "expires_at": int(time.time()) + 60,
        "calls_url": f"http://{host}/v1/realtime/calls",
    })


async def calls(request):
    if not bearer(request).startswith("ek_mock_"):
        return openai_error(401, "invalid_api_key", "Bad ephemeral key")
    if FAIL == "insufficient_quota":
        log.info("simulating: account has no credit")
        return openai_error(429, "insufficient_quota", "You exceeded your current quota.")

    offer = RTCSessionDescription(sdp=await request.text(), type="offer")
    pc = RTCPeerConnection()
    call = Call(pc)
    CALLS.add(call)
    log.info("[%s] call started", call.id)

    @pc.on("datachannel")
    def on_datachannel(channel):
        call.channel = channel
        log.info("[%s] data channel '%s' open", call.id, channel.label)
        channel.on("message", call.on_client_event)
        call.send("session.created", session={"id": "sess_mock", "model": "mock", "type": "realtime"})

    @pc.on("track")
    def on_track(track):
        if track.kind == "audio":
            log.info("[%s] receiving the phone's microphone", call.id)
            asyncio.ensure_future(call.listen(track))

    @pc.on("connectionstatechange")
    async def on_state():
        log.info("[%s] connection %s", call.id, pc.connectionState)
        if pc.connectionState in ("failed", "closed"):
            call.closed = True
            call.cancel_fake_turn()
            CALLS.discard(call)
            log.info("[%s] call ended: %d mic frames received (%.0f s of audio), events from phone: %s",
                     call.id, call.frames_in, call.frames_in * 0.02, call.events_in)
            await pc.close()

    pc.addTrack(call.speaker)
    await pc.setRemoteDescription(offer)
    answer = await pc.createAnswer()
    await pc.setLocalDescription(answer)
    return web.Response(text=pc.localDescription.sdp, content_type="application/sdp", status=201)


def main():
    app = web.Application()
    app.router.add_get("/health", health)
    app.router.add_post("/session", session)
    app.router.add_post("/v1/realtime/calls", calls)
    log.info("Mock Realtime server on http://0.0.0.0:%d  (token: %s%s)", PORT, TOKEN, f", simulating {FAIL}" if FAIL else "")
    web.run_app(app, host="0.0.0.0", port=PORT, print=None)


if __name__ == "__main__":
    sys.exit(main())

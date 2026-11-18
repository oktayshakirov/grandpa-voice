# Emulator test helpers

Drive the app on an Android emulator without touching it. Used together with `../mock-realtime`.

| Script | What it does |
|---|---|
| `shake.sh` | One deliberate shake: five ~3 g pushes through the emulator's virtual accelerometer (`adb emu sensor`). |
| `ui.py tap "<text>"` | Taps the element showing that exact text (uiautomator). `ui.py texts` lists every text on screen. |
| `watch.py [seconds]` | Prints each conversation state as it appears (Свързвам се… → Говоря… → Слушам те… → Довиждане!) and screenshots it. |

Typical run, with the mock server up and the debug app pointed at it:

```bash
sh shake.sh & python3 watch.py 45; wait
```

Tips:
- `adb shell input text` cannot type Cyrillic. Use Latin test values.
- Select-all doesn't work in Compose text fields. Clear a field with `adb shell input keyevent 123` (end) and then `67` (backspace) repeated.
- `uiautomator dump` takes about 1 s, so `watch.py` can miss states shorter than that. The mock server log has exact timings.
- The emulator↔Mac WebRTC connection takes about 5 s (NAT); against OpenAI's servers it is much faster.

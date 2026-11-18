# Testing on a real phone

Do these in order. Each stage isolates one layer, so when something fails you know where.

## 0. Watch the logs (very helpful)

```bash
adb logcat -s Conversation CompanionService
```

## 1. Connection

In settings tap **Провери връзката**. It must show „✓ Връзката работи“. This is free: it only creates a
one-minute key and starts no conversation. Typical failures:
- „Ключът е грешен…“: the key was mistyped or deleted.
- „Сметката в OpenAI няма средства“: add credit under Billing. (An account without credit may pass this check and
  fail only when the call starts; the app then says the same thing out loud.)

Using the token broker instead? From your computer:
`curl -X POST https://YOUR-BROKER/session -H "Authorization: Bearer YOUR-TOKEN" -d '{}'` must return `"value":"ek_…"`.

No credit yet? Everything except OpenAI itself can be tested with the mock server in `tools/mock-realtime`
(see the README).

## 2. Bulgarian quality check (before investing more time)

Open the OpenAI Realtime playground (platform.openai.com › Playground › Realtime), paste the instructions from
`settings/Defaults.kt`, choose the same voice, and speak Bulgarian slowly with pauses, the way they speak.
If you dislike the voice, try `cedar`, `marin`, `ash`, `sage` — then set the same one in the app.

## 3. Shake sensitivity

Open hidden settings. Under **Разклащане** the live bar („Сега: … g“, „Разпознати: …“) updates as you move the phone; shakes here are only counted, they never start a conversation.

- Pick the phone up, put it down, carry it across the room: the counter must **not** increase.
- Have *them* shake it the way they naturally would: the counter **must** increase each time (beep + buzz).
- Adjust: lower the threshold (e.g. 1.8 g) if their shakes are gentle; raise it or require more pushes if it
  triggers by accident. Save.

## 4. First conversation (screen on)

1. Shake. Expected: beep/buzz → „Свързвам се…“ → greeting in Bulgarian within ~2–4 s → „Слушам те…“.
2. Talk, pause mid-sentence for 3 seconds: it should wait, not jump in (patient turn detection).
3. Interrupt it while it speaks: it should stop and listen.
4. Say „Довиждане“: it should say goodbye, then the screen shows „Довиждане!“ and returns to standby.
5. Start again and shake during the conversation: it ends immediately.
6. Start again and stay silent for 2 minutes: it says a short goodbye and ends.

## 5. Audio routing and echo

- Phone on the table, loudspeaker, normal room: does it ever cut itself off? If yes, turn off
  „Може да прекъсва асистента“ or choose „По тишина“ turn detection.
- Connect Bluetooth headphones/speaker → shake → audio should go there.

## 6. Screen off / locked

1. Plug in the charger, set „Екранът свети“ to *Normal phone behaviour*, wait until the screen turns off.
2. Wait 5 minutes, then 30 minutes (Doze). Shake.
   Expected: screen turns on, conversation starts. If nothing happens: battery optimisation is still on,
   or the manufacturer's battery manager killed the app (see PHONE_SETUP §4).

## 7. Failure handling

- Wi-Fi off + mobile data off → shake → „Няма връзка с интернет.“ shown and spoken; back to standby after ~10 s.
- During a conversation, turn Wi-Fi off → within ~10–30 s „Връзката прекъсна.“ → standby → shaking later works again.
- Wrong key or device token in settings → „Приложението не е настроено правилно.“
- Revoke microphone in Android settings → the red „Разреши микрофона“ button appears.

## 8. Reboot and interruptions

- Reboot the phone: the app must appear by itself (Home app). Shake works without touching anything.
- Receive a phone call during standby; after it ends, the app must be back in front (press Home if needed).
- Leave it overnight on the charger; next morning shake once.

## 9. Privacy check

- Android Settings › Privacy › Microphone access history: the mic is used only during conversations.
- The status-bar microphone indicator must disappear when the conversation ends.

# Testing on a real phone

Do these in order. Each stage isolates one layer, so when something fails you know where.

## 0. Watch the logs (very helpful)

```bash
adb logcat -s Conversation CompanionService
```

## 1. Connection

In Settings, tap **Check connection**. The result should say the connection works. This requests a temporary credential without starting a conversation. A successful check can be followed by an out-of-credit error when a call begins.

## 2. Language and upgrade

- On a fresh installation, confirm English is preselected. Choose Spanish or German and confirm the setup screens, conversation, and spoken errors match the selection.
- Change the language in Settings, save, and reopen the app. Verify the choice persists and the next greeting and goodbye use the selected language.
- Update a device from the signed original APK. Verify it opens directly in Bulgarian and retains the account and settings. Switch to English and verify the existing setup still connects.
- Check French and Portuguese with the same flow. For spoken errors, install the matching Android text-to-speech voice.

## 3. Shake sensitivity

Open hidden settings. Under **Shake detection**, the live meter updates as you move the phone; shakes here are only counted, they never start a conversation.

- Pick the phone up, put it down, carry it across the room: the counter must **not** increase.
- Have *them* shake it the way they naturally would: the counter **must** increase each time (beep + buzz).
- Adjust: lower the threshold (e.g. 1.8 g) if their shakes are gentle; raise it or require more pushes if it
  triggers by accident. Save.

## 4. First conversation (screen on)

1. Shake. Expected: beep/buzz → connecting → greeting in the selected language → listening.
2. Talk, pause mid-sentence for 3 seconds: it should wait, not jump in (patient turn detection).
3. Interrupt it while it speaks: it should stop and listen.
4. Say goodbye in the selected language. The assistant should say goodbye, then the app shows its goodbye state and returns to standby.
5. Start again and shake during the conversation: it ends immediately.
6. Start again and stay silent for 2 minutes: it says a short goodbye and ends.

## 5. Audio routing and echo

- Phone on the table, loudspeaker, normal room: does it ever cut itself off? If yes, turn off
  **Allow the person to interrupt the assistant** or choose the silence-based turn detection.
- Connect Bluetooth headphones/speaker → shake → audio should go there.

## 6. Screen off / locked

1. Plug in the charger, set **Screen** to **Follow phone settings**, wait until the screen turns off.
2. Wait 5 minutes, then 30 minutes (Doze). Shake.
   Expected: screen turns on, conversation starts. If nothing happens: battery optimisation is still on,
   or the manufacturer's battery manager killed the app (see PHONE_SETUP §4).

## 7. Failure handling

- Wi-Fi off + mobile data off → shake → the no-internet error shown and spoken; back to standby after ~10 s.
- During a conversation, turn Wi-Fi off → within ~10–30 s the connection-lost error → standby → shaking later works again.
- Wrong key or device token in settings → the connection-setup error
- Revoke microphone in Android settings → the red **Allow microphone access** button appears.

## 8. Reboot and interruptions

- Reboot the phone: the app must appear by itself (Home app). Shake works without touching anything.
- Receive a phone call during standby; after it ends, the app must be back in front (press Home if needed).
- Leave it overnight on the charger; next morning shake once.

## 9. Privacy check

- Android Settings › Privacy › Microphone access history: the mic is used only during conversations.
- The status-bar microphone indicator must disappear when the conversation ends.


## Mock server

The mock server exercises the call flow without OpenAI credit. Its scripted audio is Bulgarian, so use a live OpenAI session for multilingual voice checks.

```sh
cd tools/mock-realtime
python3 -m venv .venv
.venv/bin/pip install -r requirements.txt
.venv/bin/python mock_server.py
```

In a debug build, choose **Your own server** and use `http://10.0.2.2:8788` for the emulator or `http://<your-computer-ip>:8788` for a phone on the same network. Use `mock-device-token-for-local-testing` as the device token. Restart the server with `MOCK_FAIL=insufficient_quota` to test the out-of-credit state.

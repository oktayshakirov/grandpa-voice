# Setting up the phone (once, ≈15 minutes)

Do these steps yourself before giving them the phone. They should never need to see any of it.

## 1. Basic Android settings

1. **Screen lock:** If the phone requires a PIN after reboot, unlock it before using the shake gesture.
2. **System language:** Set Android to the language the person reads so they can understand system prompts.
3. **Volume:** Check media and call volume. The app can raise call volume during conversations, controlled from Settings.
4. **Display:** By default, the app keeps the screen on while charging. Change this in Settings → Screen.
5. **Connection:** Connect to Wi-Fi and make sure it reconnects automatically.
6. **Text-to-speech:** Install voice data for the selected language in Android's text-to-speech settings if you want the phone to speak local errors aloud.

## 2. Install and first start

1. Install the APK from [GitHub Releases](https://github.com/oktayshakirov/grandpa-voice/releases), or build it from the [README](../README.md).
2. Open the app, choose the interface and conversation language, and allow microphone access when prompted.
3. Under **OpenAI connection**, enter an API key or choose **Your own server** and enter its address and device token. Tap **Check connection**.
4. Under **About you**, optionally enter a name. When Bulgarian is selected, choose the grammatical gender as well.
5. Tap **Save**. Shake the phone or tap the microphone to start talking.

To open Settings again, hold the greeting for three seconds.

For screen-off shake detection on a dedicated phone, the **Phone** section offers battery optimization settings. You can also use **Set as Home app** so the Home button returns to the companion and Android opens it as the launcher after reboot and unlock.

## 3. Permissions the app uses

| Permission | Why |
|---|---|
| Microphone (`RECORD_AUDIO`) | The conversation. Requested on first start. |
| Notifications (`POST_NOTIFICATIONS`, Android 13+) | Android requires a visible notification for the always-running service. It is silent. |
| Internet, network state | Talking to OpenAI (or your token broker); detect "no internet". |
| Foreground service (microphone, special use) | Keeps shake detection and the conversation running. |
| Wake lock | Keeps the accelerometer running with the screen off; turns the screen on after a shake. |
| Modify audio settings, Bluetooth (≤ Android 11) | Speakerphone vs. headset routing, call volume. |
| Vibrate | Short buzz confirming a shake was recognised. |
| Ignore battery optimisations | Asked for via the settings button so Android does not kill the service. |

## 4. Manufacturer-specific "don't kill my app" settings

Many phones have extra battery managers on top of Android's. Look up your model on **dontkillmyapp.com**. Typical:

- **Samsung:** Settings › Battery › Background usage limits → add the app to *Never sleeping apps*.
- **Xiaomi / Redmi / POCO:** App info › Battery saver → *No restrictions*; also enable *Autostart*.
- **Huawei / Honor:** App launch → turn off *Manage automatically*, enable all three toggles.
- **OnePlus / Oppo / Realme:** Battery › App battery management → *Allow background activity*; lock the app in Recents.

Google Pixel and Motorola usually need nothing beyond step 2.

## 5. Optional: full kiosk mode (dedicated-device mode)

This makes the phone a single-purpose device: no status bar, no Home/Recents buttons, no way to leave the app by
accident. It uses Android's official *lock task* mode and requires making the app **device owner**, which Android only
allows on a phone **with no accounts** on it (a fresh factory reset where you skip Google sign-in, or remove all
accounts temporarily).

1. Enable Developer options (tap *Build number* 7×) → enable **USB debugging**.
2. Connect to a computer with `adb` and run:
   ```bash
   adb shell dpm set-device-owner com.grandpavoice/.kiosk.AdminReceiver
   ```
   Expected output: `Success: Device owner set to package com.grandpavoice`.
   If it says the device already has accounts, remove them (Settings › Accounts) and retry.
3. Open the app. It enters lock-task mode automatically, disables the status bar and the lock screen,
   and pins itself as the Home app.

To leave kiosk mode: hold the greeting 3 s → Settings → **Exit kiosk mode until restart** (temporary) or
**Remove kiosk mode** (permanent; needed before you can uninstall the app).

Kiosk mode is optional. The Home-app setup in step 2 already gives most of the benefit.

## 6. Physical setup

- A charging stand or a cable that stays plugged in. Screen-off shake detection uses battery.
- Put the phone where they sit. On a table it works best with the screen facing up (speaker unobstructed).
- If they use hearing aids or a Bluetooth speaker/headset, pair it; the app routes audio there automatically.

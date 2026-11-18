# Setting up the phone (once, ≈15 minutes)

Do these steps yourself before giving the phone to him. He should never need to see any of it.

## 1. Basic Android settings

1. **Screen lock → None** (Settings › Security › Screen lock). With a PIN/pattern the app cannot start after a reboot
   until someone unlocks, and the lock screen gets in the way of a "just shake it" experience.
2. **Language:** Bulgarian (optional, but makes system pop-ups understandable for him).
3. **Volume:** turn media and call volume up. The app also sets call volume to maximum during conversations
   (switchable in settings).
4. **Display › Screen timeout:** anything; the app keeps the screen on while charging.
5. **Wi-Fi:** connect and make sure it reconnects automatically. Mobile data as a backup is ideal.
6. **Text-to-speech:** Settings › System › Languages › Text-to-speech output → install **Bulgarian** voice data
   for Google's speech engine. Used only to say error messages ("Няма връзка с интернет") when OpenAI is unreachable.

## 2. Install and first start

1. Install the APK (see README).
2. Open **Събеседник**. Allow **Microphone** and **Notifications** when asked.
3. Tap **Настрой** (first start) or press and hold „Здравей!“ for 3 seconds → **Settings**:
   - under **Връзка с OpenAI**, paste your OpenAI API key (or choose **Собствен сървър** and enter the
     server address and device token if you run the token broker), then tap **Провери връзката**,
   - optionally his name (e.g. „дядо Иван“ or just „Иване“ — this is how it will address him),
   - tap **Изключи оптимизацията на батерията** and confirm,
   - tap **Направи го начален екран (Home)** and choose Събеседник as the Home app (choose "Always"),
   - **Запази**.

Choosing it as the Home app is what makes it start automatically after a reboot and makes the Home button
always come back to it.

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

- **Samsung:** Settings › Battery › Background usage limits → add Събеседник to *Never sleeping apps*.
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

To leave kiosk mode: hold the greeting 3 s → Settings → **Излез от киоск режим до рестарт** (temporary) or
**Премахни киоск режима напълно** (permanent; needed before you can uninstall the app).

Kiosk mode is optional. The Home-app setup in step 2 already gives most of the benefit.

## 6. Physical setup

- A charging stand or a cable that stays plugged in. Screen-off shake detection uses battery.
- Put the phone where he sits. On a table it works best with the screen facing up (speaker unobstructed).
- If he uses hearing aids or a Bluetooth speaker/headset, pair it; the app routes audio there automatically.

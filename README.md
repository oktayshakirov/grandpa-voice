# Shake to AI for Android

**Shake to talk to AI.**

Shake your phone or tap the microphone to start a real-time voice conversation. Ask a quick question, think through an idea out loud, practice speaking another language, or just chat. Say goodbye, shake again, or tap the microphone to end the conversation.

The app is designed around one action. A large microphone button, clear listening and speaking indicators, and patient turn-taking make it easy to use at a desk, on a phone stand, or when setting up a simpler way to chat for someone who prefers speaking to typing.

English is preselected on first launch. You can choose another language during setup or change it later under **Settings → Language**. Your choice applies to the interface and the conversation.

## Install and set up

You'll need an Android 8 or newer phone with a microphone and accelerometer and either a funded OpenAI API account or a compatible [self-hosted server](backend/README.md).

1. [Build the app from source](#build-from-source) and install it on your phone.
2. Open the app, choose a language if prompted, and allow microphone access.
3. In **Settings → OpenAI connection**, enter an [OpenAI API key](https://platform.openai.com/api-keys) or your server address and device token. Select **Check connection** and then **Save**.
4. Shake the phone or tap the microphone. Hold the greeting for three seconds to reopen Settings.

You can adjust shake sensitivity, speaking speed, volume, and the conversation timeout in Settings. For help configuring a phone, see the [phone setup guide](docs/PHONE_SETUP.md). Screen-off shake detection uses battery and can depend on the phone's background-app settings.

## Cost and privacy

The source code is free and open source. Live voice conversations are billed to your OpenAI API account. Costs depend on the model and usage. Check [OpenAI pricing](https://developers.openai.com/api/docs/pricing) and monitor usage in your account. You can choose a lower-cost model in Settings.

The app streams microphone audio to OpenAI during a conversation over WebRTC. It does not save audio or transcripts on the phone. Direct-mode credentials are stored encrypted on the device. With a token broker, the permanent OpenAI key stays on your server. See [OpenAI's API data controls](https://platform.openai.com/docs/guides/your-data) for provider-side data handling.

## Build from source

Use JDK 17 and Android SDK 36.

```sh
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

Install the locally built APK with Android Studio or run `adb install app/build/outputs/apk/debug/app-debug.apk`. To test without an API key, use the [local mock server](docs/TESTING.md#mock-server). See the [device testing checklist](docs/TESTING.md).

Screenshots are available in [the project documentation](docs/screenshots/).

## Feedback

Use the repository's Issues tab for bugs or suggestions. Include your app version, Android version, and steps to reproduce. Keep API keys and private conversation details out of reports.

[MIT License](LICENSE).

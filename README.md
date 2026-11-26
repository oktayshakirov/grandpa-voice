# Voice Companion for Android

Shake your phone or tap the microphone to start a voice conversation. Voice Companion gives older adults a simple way to chat, with a large microphone button, clear status messages, and time to pause while speaking. Shake again, tap the microphone, or say goodbye to end a conversation.

English is the default on new installations. On first launch, choose English, Bulgarian, Spanish, German, French, or Portuguese. Settings → Language changes both the interface and the conversation language. Installations upgraded from the original Bulgarian app keep Bulgarian until the user changes it.

## Install and set up

You'll need an Android 8 or newer phone with a microphone and accelerometer and either a funded OpenAI API account or a compatible [self-hosted server](backend/README.md).

1. Download the APK from [GitHub Releases](https://github.com/oktayshakirov/grandpa-voice/releases). The v1.2.0 release is the original Bulgarian build, named Събеседник. The multilingual version is available from source until a new APK is released.
2. Open the app, choose a language if prompted, and allow microphone access.
3. In **Settings → OpenAI connection**, enter an [OpenAI API key](https://platform.openai.com/api-keys) or your server address and device token. Select **Check connection** and then **Save**.
4. Shake the phone or tap the microphone. Hold the greeting for three seconds to reopen Settings.

A family member can set up the app and tune the shake sensitivity, speaking speed, volume, and conversation timeout. For a dedicated phone, see [phone setup](docs/PHONE_SETUP.md). Screen-off shake detection uses battery and can depend on the phone's background-app settings.

## Cost and privacy

The app is free and open source. Your OpenAI API account is billed for voice usage. Check [OpenAI pricing](https://developers.openai.com/api/docs/pricing) and monitor usage in your account. You can select a lower-cost model in Settings.

The app streams microphone audio to OpenAI during a conversation over WebRTC. It does not save audio or transcripts on the phone. Direct-mode credentials are stored encrypted on the device. With a token broker, the permanent OpenAI key stays on your server. See [OpenAI's API data controls](https://platform.openai.com/docs/guides/your-data) for provider-side data handling.

## Build from source

Use JDK 17 and Android SDK 36.

```sh
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

The debug APK is written to `app/build/outputs/apk/debug/`. To test without an API key, use the [local mock server](docs/TESTING.md#mock-server). See the [device testing checklist](docs/TESTING.md) before preparing a distribution build.

For an update to an existing release, keep the application ID and sign it with the same release key. Set the keystore path and signing credentials in the git-ignored `keystore.properties` file. The release variant falls back to debug signing when that file is absent.

## Feedback

Use **Report a bug** or **Suggest a feature** in Settings, or open a [GitHub issue](https://github.com/oktayshakirov/grandpa-voice/issues). Include your app version, Android version, and steps to reproduce. Keep API keys and private conversation details out of reports.

[MIT License](LICENSE).

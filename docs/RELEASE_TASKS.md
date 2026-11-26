# Android public release tasks

## Language and setup

- [x] Default new installations to English and keep Bulgarian for upgrades from the original app.
- [x] Offer English, Bulgarian, Spanish, German, French, and Portuguese in onboarding and Settings.
- [x] Use the selected language for the interface, conversation, and local spoken errors.
- [x] Add bug report and feature suggestion actions.
- [ ] Test a fresh install and an upgrade from the signed v1.2.0 APK. Verify language switching, conversations, and screen-off activation on a physical phone.

## Name and icon

- [ ] Choose the public name. Options for review are **Hello Again**, **Here to Talk**, **Easy Hello**, **Just Say Hello**, and **Shake to Talk**. The current English build uses **Voice Companion** as a working label.
- [ ] Check the chosen name for store and brand conflicts. Update the launcher label, documentation, and store copy. Keep the Android application ID for install compatibility.
- [ ] Create a launcher icon with adaptive foreground and background layers, a monochrome version, and a Play Store icon. Use a clear speech or microphone symbol that remains legible at small sizes.

## GitHub and store assets

- [x] Rewrite the README in English for a general audience, with setup, voice billing, privacy, and source-build information.
- [ ] Publish the multilingual changes and a signed APK to GitHub Releases after testing. Use the original signing key.
- [ ] Capture three actual Android screenshots in English after the branding is final. Show standby with the shake-to-talk prompt, a live listening state, and onboarding with the language choices. Use test data and hide credentials.
- [ ] If we proceed with Google Play, prepare the Android App Bundle, Play App Signing, support contact, store listing, Data safety form, privacy policy, and foreground-service declarations. Run a closed test to check whether families can complete setup on their own.

## Portfolio

- [ ] Once the public project is ready, add the project to the portfolio using its final name and visual. Add a GitHub button and use the existing disabled Android store button until a Play listing is live. The project is Android-only.

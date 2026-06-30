# Zaxo — Play Store Listing

## App Icon
- Size: 512×512 PNG
- Adaptive icon with foreground/background layers
- Design: Neumorphic chat bubble with Zaxo logo

## Feature Graphic
- Size: 1024×500 PNG
- Shows: App name, tagline, device mockup with chat screen

---

## Short Description (80 chars max)
Private messaging & P2P calls with Zaxo Numbers. End-to-end encrypted.

---

## Full Description (4000 chars max)

**Zaxo — Private Messaging & P2P Calls**

Stay connected with friends and family through secure, end-to-end encrypted messaging and crystal-clear P2P voice and video calls. Zaxo puts your privacy first with the Signal Protocol protecting every message, every call, every time.

**🆔 Your Zaxo Number**
Get a unique 9-digit Zaxo Number — share it instead of your phone number. Find friends easily without exposing personal contact info. Your number, your identity, your privacy.

**📞 P2P Calls**
Make voice and video calls powered by LiveKit with end-to-end encryption. No middlemen, no eavesdropping. Crystal-clear audio and HD video, peer-to-peer.

**📸 Status & Stories**
Share moments that disappear after 24 hours. Capture photos and videos with built-in camera, add captions, drawings, and text overlays. Choose who sees your updates with granular privacy controls.

**🔒 Privacy & Security**
- End-to-end encryption via Signal Protocol for all messages
- P2P encrypted voice and video calls
- Read receipts with privacy toggle — control when others see your read status
- Block contacts instantly
- No data selling, no ad tracking, ever

**✨ Features**
- 💬 Real-time messaging with typing indicators and read receipts
- 🎤 Voice messages with waveform visualization and speed control
- 🔍 Full-text search across all your conversations
- 🎨 Custom chat wallpapers with auto-contrast detection
- 📊 Group admin controls for managing communities
- 🖼️ Media gallery viewer for photos and videos
- 🔕 Mute specific status updates without unfriending
- ⚡ Background playback for voice messages with audio focus
- 📝 Voice transcription for accessibility
- ✂️ Video trimming and drawing for status updates

**🌙 Neumorphic Design**
Zaxo features a beautiful neumorphic UI that's easy on the eyes in both light and dark mode. Smooth animations and thoughtful haptic feedback make every interaction feel natural.

**🌍 Built for Everyone**
- Works on Android 8.0+ (API 26+)
- Optimized for phones and tablets
- Minimal battery usage with efficient background processing
- Works on slow networks with offline message queueing

**📧 Contact**
- Email: support@zaxo.eu.cc
- Website: https://zaxo.eu.cc
- Privacy Policy: https://zaxo.eu.cc/privacy

---

## App Category
Communication

## Tags
Messaging, Calls, Social, Privacy, Encryption

## Content Rating
Everyone (no violent, sexual, or gambling content)

## Screenshots Needed (minimum 4, recommended 8)

### Phone Screenshots (1080×1920 or 1440×2560)
1. **Chat List** — Main conversation list showing chat bubbles with last messages
2. **Chat Room** — Active conversation with voice message waveform and read receipts
3. **Status Tab** — Status updates with circular avatars and "My Status" card
4. **Status Camera** — Camera view with neumorphic capture button and flash toggle
5. **Status Editor** — Video preview with caption field and drawing tools
6. **P2P Call** — Active voice/video call screen with end-to-end encryption badge
7. **Search** — Message search with FTS results and highlighted matches
8. **Profile** — User profile with Zaxo Number and settings

### Tablet Screenshots (optional, 7-inch + 10-inch)
- Same screens adapted for tablet layout

---

## Privacy Policy URL
https://zaxo.eu.cc/privacy

F52: Privacy policy must be accessible before Play Store submission.

---

## Beta Testing Setup

### Firebase App Distribution
1. Add testers in Firebase Console → App Distribution
2. Build release APK: `./gradlew assembleRelease`
3. Upload APK to App Distribution
4. Testers receive email invitation
5. Collect feedback via Crashlytics + in-app feedback

### Google Play Internal Testing
1. Create internal testing track in Play Console
2. Upload signed AAB: `./gradlew bundleRelease`
3. Add tester email addresses (min 20, max 100)
4. Testers install via Play Store private link
5. Review feedback before promoting to open testing

---

## Signing Key Checklist (F49)
- [ ] Keystore generated with `keytool`
- [ ] Keystore backed up to secure offline location
- [ ] Passwords stored in password manager (NOT in repo)
- [ ] Upload certificate exported for Play App Signing
- [ ] Build configured with signing config in build.gradle.kts

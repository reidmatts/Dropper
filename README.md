# Dropper 📦

**Dropper** is a fast, lightweight Minecraft mod manager for Android. Browse Modrinth, resolve dependencies, and manage your mod folders directly for PojavLauncher, ZalithLauncher, and custom directories.

---

## ✨ Features

- **Modrinth Integration:** Search, filter by category, and sort thousands of Minecraft mods directly from the official Modrinth API.
- **Dynamic Version Support:** Automatically fetches up-to-date Minecraft versions (including the latest releases) and supports Fabric, Forge, NeoForge, and Quilt loaders.
- **Smart Dependency Resolution:** Automatically detects required dependencies and lets you install everything in a single tap.
- **Direct & SAF Storage Support:**
  - **Auto-Discovery:** Detects standard launcher folders (`Amethyst`, `ZalithLauncher`).
  - **Storage Access Framework (SAF):** Pick any custom folder on Android 11+ using the system document picker.
- **Local Mod Manager:**
  - Toggle individual mods on/off (`.jar` ↔ `.jar.disabled`) without deleting them.
  - Bulk **Enable All** and **Disable All** actions with instant feedback.
  - Filter installed mods by Active, Disabled, or search query.
- **Rich Mod Details:** Clean, native-styled project descriptions with support for Markdown, HTML formatting, images, and external project links.
- **Tactile Haptic Feedback:** Custom haptic feedback and vibration simulation across buttons, switches, and actions.
- **Security & Integrity First:**
  - SHA-512 hash verification on every downloaded mod file.
  - Strict HTTPS-only connections and private network/SSRF protection.
  - Path traversal (`CWE-22`) protection for all local and SAF file operations.
- **Modern Material 3 UI:** Supports System, Light, Dark, and true black **AMOLED** themes with Material You dynamic colors.

---

## 🛠️ Tech Stack

- **Language:** Kotlin
- **UI Framework:** Jetpack Compose (Material 3)
- **Networking:** Ktor Client & Kotlinx Serialization
- **Background Tasks:** AndroidX WorkManager (foreground/background mod downloading with live progress)
- **Image Loading:** Coil Compose
- **Storage:** Java NIO + AndroidX DocumentFile (SAF)

---

## 🚀 Getting Started

### Prerequisites
- **Android Studio** (Ladybug or newer recommended)
- **JDK 17+**
- **Android SDK 26+** (Android 8.0 Oreo or higher)

### Building the App
1. Clone the repository:
   ```bash
   git clone https://github.com/<your-username>/Dropper.git
   cd Dropper
   ```
2. Open the project in **Android Studio** and let Gradle sync.
3. Build a debug APK via CLI or Android Studio:
   ```bash
   ./gradlew assembleDebug
   ```
   The compiled APK will be generated in `app/build/outputs/apk/debug/app-debug.apk`.

---

## 📱 Permissions Used

- `INTERNET` — Fetching mod metadata and downloading `.jar` files from Modrinth.
- `MANAGE_EXTERNAL_STORAGE` / `READ_EXTERNAL_STORAGE` *(Optional)* — Used for automatic discovery of PojavLauncher / ZalithLauncher directories in shared storage. You can also use the built-in SAF folder picker without granting all-files access.
- `VIBRATE` — Provides tactile haptic feedback when interacting with controls.

---

## 📄 License

This project is licensed under the [MIT License](LICENSE).

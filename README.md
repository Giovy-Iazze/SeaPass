<div align="center">

# ⚓ SeaPass
### The Digital Wallet for Maritime Professionals

*Track embarkations · Manage certificates · Back up to the cloud*

[![Android](https://img.shields.io/badge/Android-API%2024%2B-4fc3f7?style=flat-square&logo=android&logoColor=white)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9-4fc3f7?style=flat-square&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![License](https://img.shields.io/badge/License-MIT-4fc3f7?style=flat-square)](LICENSE)
[![Version](https://img.shields.io/badge/Version-1.0-ffd54f?style=flat-square)](https://github.com/8888-8844/SeaPass/releases)

---

</div>

## What is SeaPass?

SeaPass is an Android app built for mariners — a clean, offline-first tool to manage the documents and records that define a seafarer's career. Keep your embarkation history, track certificate expiries, and back up everything to your personal Google Drive with a single tap.

No subscriptions. No servers. Your data stays on your device.

---

## Features

| | |
|---|---|
| **⚓   Embarkation Log** | Record every voyage — vessel, dates, rank, and flag state. Your complete service history in one place. |
| **📋   Certificate Tracker** | Store all your certificates with expiry dates. Get notified before anything lapses. |
| **☁️   Cloud Backup** | Back up to your personal Google Drive App Data folder with a single tap. Restore on any device, anytime. |
| **🔒   Private by Design** | Drive backups go to a hidden folder only you can access — invisible to other apps and people. |

---

## Tech Stack

- **Language** — Kotlin
- **UI** — Jetpack Compose + Material 3
- **Database** — Room (local, offline-first)
- **Cloud** — Google Drive API (`drive.appdata` scope)
- **Architecture** — MVVM with ViewModels and Repositories
- **Min SDK** — Android 7.0 (API 24)
- **Target SDK** — Android 16 (API 36)

---

## Getting Started

### Prerequisites

- [Android Studio](https://developer.android.com/studio) (Hedgehog or newer)
- A Google Cloud project with the **Google Drive API** enabled
- A valid OAuth 2.0 client ID (Android) configured in your Cloud Console

### Build & Run

```bash
# 1. Clone the repo
git clone https://github.com/8888-8844/SeaPass.git
cd SeaPass

# 2. Create your .env file
cp .env.example .env
# Fill in your keys (see .env.example for details)

# 3. Open in Android Studio and run on a device or emulator
```

> **Note:** Remove the `signingConfig = signingConfigs.getByName("debugConfig")` line from `app/build.gradle.kts` before running locally.

---

## Privacy

SeaPass stores all data locally on your device. The optional Google Drive backup uses the `drive.appdata` scope — a private, hidden folder that only SeaPass can access. No personal data is sent to any server operated by us.

Read the full [Privacy Policy](https://8888-8844.github.io/SeaPass#privacy).

---

## Contact

**Giovanni Iazzetta** · [iazzettagiovanni01@gmail.com](mailto:iazzettagiovanni01@gmail.com)

---

<div align="center">
<sub>Built for life at sea.</sub>
</div>

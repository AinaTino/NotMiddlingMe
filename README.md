# StopMiddlingMe
> *Because Android won’t let you sniff packets like a real hacker.*

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Android](https://img.shields.io/badge/Android-10+-3DDC84.svg)](https://www.android.com/)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9+-7F52FF.svg)](https://kotlinlang.org/)

---

## What This Does (And Doesn’t)
Detects **MITM attacks on local networks**—or at least tries to, given Android’s *generous* API restrictions.
Uses `/proc/net/arp`, `VpnService`, and sheer stubbornness to catch attackers (when Google isn’t actively sabotaging you).

---

## Features
- **ARP Spoofing Detection** – Monitors `/proc/net/arp` for MAC changes (because raw sockets are a pipe dream).
- **DNS/TLS Analysis** – `VpnService` to the rescue (sort of).
- **Rogue AP Detection** – Because even Android’s `WifiManager` can spot a fake hotspot.
- **No Root Required** – Also, no miracles.

---
## ⚠️ Limitations (Thanks, Android)
- **No raw sockets** – Blame Google, not us.
- **No packet sniffing** – See above.
- **False positives** – Sometimes the app barks at shadows. Deal with it.
- **Android 10+ only** – Because older versions are *too* easy.

---
## 🛠️ Installation
1. Clone this repo.
2. Open in **Android Studio**.
3. Cry silently when Android’s API refuses to cooperate.
4. Build and run.

---
## Usage
1. Open the app.
2. Connect to a network (preferably one with an attacker, for testing purposes).
3. Watch as it *attempts* to detect MITM attacks.
4. Pray it works.

---
## 🤝 Contributing
Found a bug? **Great.**
Fixed it? **Even better.**
Want to contribute? **Sure, but no promises we’ll merge it.**

---
## License
**MIT** – Do whatever you want, but if you break it, you bought it.
(And if you improve it, maybe send us a pull request. Or don’t. We’re not your mom.)
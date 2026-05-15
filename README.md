# 🛡️ Raksha: Advanced Tactical Safety & Emergency Response

[![Kotlin](https://img.shields.io/badge/Kotlin-Android-7F52FF?logo=kotlin)](https://kotlinlang.org/)
[![Python](https://img.shields.io/badge/Python-Backend-3776AB?logo=python)](https://www.python.org/)
[![Twilio](https://img.shields.io/badge/Twilio-SMS-F22F46?logo=twilio)](https://www.twilio.com/)
[![Status](https://img.shields.io/badge/Status-Hackathon--Ready-success)]()

**Raksha** is a mission-critical safety ecosystem designed to protect vulnerable citizens through AI-driven risk assessment, predictive routing, and a real-time Tactical Command Center. Developed for high-pressure emergency scenarios, Raksha bridges the gap between citizens and responders.

---

## 🚀 Key Innovation Pillars

### 📡 1. Tactical Command Center (v3)
A high-performance, dark-mode dashboard for emergency dispatch.
*   **Real-time SOS Monitoring**: Instant alerts with GPS coordinates and victim profiles.
*   **Responder Fanout**: One-click SMS dispatch to the nearest support teams via Twilio.
*   **Incident Lifecycle Management**: Verify, track, and resolve reports in real-time.

### 🧠 2. Predictive AI Risk Mapping
Our proprietary risk engine doesn't just show locations; it predicts safety.
*   **ML-Driven Heatmaps**: Analyzes historical crime data (Indore Dataset) to visualize danger zones.
*   **Dynamic Hazard Detection**: Real-time "Geofence" alerts if a user enters a high-risk area.
*   **Triple-Path Routing**: Choose between **Fastest**, **Balanced**, or **Safest** routes based on AI safety scores.

### 📶 3. Hackathon-Ready Connectivity
Built for unstable network environments (like demo floors).
*   **Dynamic IP Discovery**: Manual API endpoint override in app settings—no code changes required to switch servers.
*   **Offline-First Reports**: Incident reports are queued locally if the network drops.
*   **E.164 Global Dispatch**: Automatic phone number formatting for international SMS routing.

---

## 🛠️ Technology Stack

| Layer | Technologies |
| :--- | :--- |
| **Mobile** | Kotlin, Android SDK 34, Google Maps SDK, Fused Location |
| **Backend** | Python (Flask-style API), SQLite, Scikit-Learn |
| **Messaging** | Twilio SMS API (E.164 Standard) |
| **Design** | Material Design 3, Tactical Dark Mode (Tactical Response v3) |

---

## ⚙️ Quick Start Guide

### 1. Backend Setup
```bash
# Clone the repository
git clone https://github.com/chupkarlakshya/raksha.git
cd raksha/backend

# Configure your environment
# Add your TWILIO_ACCOUNT_SID and TWILIO_AUTH_TOKEN to .env
cp .env.example .env

# Start the server
python server.py
```
*Dashboard Access:* `http://localhost:8080/response_v3.html`

### 2. Android App Setup
1. Open the project in **Android Studio**.
2. Add your `MAPS_API_KEY` to `gradle.properties`.
3. Build and run on a physical device.
4. **Important**: Go to **Settings > Developer Tools** and enter your laptop's local IP (e.g., `192.168.1.8:8080`) to connect.

---

## 🛡️ The Raksha Safety Suite
*   **Persistent SOS**: Long-press activation with haptic feedback.
*   **Fake Call**: Realistic ringtone and UI to deter suspicious individuals.
*   **Live Tracking**: Share real-time location with emergency contacts.
*   **Safe-Path Navigation**: Exclusive "Main Road Bias" routing to keep users on well-lit, populated spines.

---

## 🤝 Contributing
Raksha was built with a vision for safer cities. We welcome contributions that improve our risk models or responder coordination.

**Built with ❤️ for Indore & Beyond.**

# 🎬 Online Examination Management System (OEMS) - Video Presentation

[![Watch Presentation on Google Drive](https://img.shields.io/badge/▶%20Watch%20Full%20Video%20Presentation-Google%20Drive-4285F4?style=for-the-badge&logo=googledrive&logoColor=white)](https://drive.google.com/file/d/1OsGQHxEmHiaKSQwqbjMwYxkCB3dn8TfZ/view?usp=sharing)

> 🔗 **Video Stream Link**: [https://drive.google.com/file/d/1OsGQHxEmHiaKSQwqbjMwYxkCB3dn8TfZ/view?usp=sharing](https://drive.google.com/file/d/1OsGQHxEmHiaKSQwqbjMwYxkCB3dn8TfZ/view?usp=sharing)

---

## 📌 Presentation Overview

This folder contains the official presentation and technical demonstration video for the **Online Examination Management System (OEMS)**. The presentation provides an end-to-end architectural walkthrough, source code explanation across the 49 core Java classes, and a live execution demo of the desktop assessment platform.

---

## ⏱️ Video Presentation Structure & Agenda

| Part | Section Title | Key Topics Covered |
|:---:|:---|:---|
| **Part 1** | **System Architecture & Database Persistence** | Type-4 JDBC connection pooling, automated schema migrations (`SchemaMigrator.java`), and ACID transactional persistence. |
| **Part 2** | **Cryptographic Defense & Security Services** | PBKDF2-HMAC-SHA256 password hashing (120,000 rounds), 128-bit CSPRNG salts, and concurrent session locking with heartbeats. |
| **Part 3** | **Assessment Engine & UI/UX Design System** | In-memory exam state models, FlatLaf glassmorphic design system (`GlassPanel.java`), and animated 24 FPS gradient canvas (`AuroraBackground.java`). |
| **Part 4** | **Multi-Channel Anti-Cheating & Proctoring** | Global keystroke filtering (`KeyboardFocusManager`), JVM focus-loss interception (`WindowFocusListener`), and webcam frame surveillance. |
| **Part 5** | **Psychometric Analytics & Certification** | Classical Test Theory (CTT) item difficulty ($P$) and discrimination ($D$) indexes, plus dynamic vector PDF certificate compilation with 2D QR matrix verification. |

---

## 🖥️ Live Demonstration Highlights

In the video, the following live system flows are demonstrated:
1. **Candidate Onboarding & Sign-In**: Secure authentication using salted PBKDF2 hashes.
2. **Subject & Exam Catalog**: Dynamic filtering of active tests based on server timestamps.
3. **Kiosk Exam Runner**: Distraction-free full-screen testing, live countdown timer synced with MySQL `SELECT NOW()`, and interactive question palette.
4. **Proctoring Interception**: Real-time detection and logging of window switching attempts.
5. **Scorecard & Analytics**: Instant result computation, difficulty analysis, and multi-axis competency radar charts.
6. **Certificate Verification**: Scanning and validating dynamic certificates against database records.

---

## 📂 Quick Access Files

- **[presentation.url](./presentation.url)**: Windows Internet Shortcut (double-click to open video directly in browser).
- **[VIDEO_LINK.txt](./VIDEO_LINK.txt)**: Plain text URL for direct copy-pasting.

---

<p align="center">
  <sub>Online Examination Management System (OEMS) • Engineered in Java & MySQL</sub>
</p>

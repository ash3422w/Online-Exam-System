<!-- ============================================================ -->
<!-- ONLINE EXAMINATION MANAGEMENT SYSTEM (OEMS) - GITHUB REPOSITORY -->
<!-- ============================================================ -->

<div align="center">

<!-- ANIMATED 3D GRADIENT WAVING BANNER -->
<img src="https://capsule-render.vercel.app/api?type=waving&color=gradient&customColorList=6,11,18,24&height=280&section=header&text=ONLINE%20EXAMINATION%20SYSTEM&fontSize=40&fontColor=ffffff&animation=twinkling&desc=Enterprise%20Desktop%20CBT%20•%20Hardware%20Proctoring%20•%20Psychometrics&descSize=17&descAlignY=68" width="100%" alt="Header Banner" />

<!-- ANIMATED TYPING SVG -->
<a href="https://github.com/ash3422w/Online-Exam-System">
  <img src="https://readme-typing-svg.demolab.com?font=Fira+Code&weight=600&size=20&duration=2800&pause=1000&color=6366F1&center=true&vCenter=true&width=750&lines=⚡+Zero-Latency+Java+Swing+%2B+FlatLaf+Desktop+Engine;🛡️+Hardware-Level+Window+Focus+%26+Webcam+Surveillance;🔐+Military-Grade+PBKDF2-HMAC-SHA256+Cryptography;📊+Real-Time+Classical+Test+Theory+(CTT)+Item+Analytics;📜+SHA-256+Tamper-Proof+PDF+Certificates+with+QR+Ledger;🚀+ACID+Two-Phase+Transactions+with+MySQL+8.0" alt="Typing SVG" />
</a>

<br/><br/>

<!-- 3D GLASSMORPHISM BADGES -->
<p align="center">
  <img src="https://img.shields.io/badge/Java-17%2B%20%7C%2021%20LTS-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white" alt="Java" />
  <img src="https://img.shields.io/badge/MySQL-8.0%20InnoDB-4479A1?style=for-the-badge&logo=mysql&logoColor=white" alt="MySQL" />
  <img src="https://img.shields.io/badge/GUI-FlatLaf%203.5-6366F1?style=for-the-badge&logo=java&logoColor=white" alt="FlatLaf" />
  <img src="https://img.shields.io/badge/Security-PBKDF2--HMAC--SHA256-10B981?style=for-the-badge&logo=shield&logoColor=white" alt="Security" />
  <img src="https://img.shields.io/badge/Architecture-MVC%20%7C%20DAO%20%7C%20Singleton-8B5CF6?style=for-the-badge&logo=blueprint&logoColor=white" alt="Architecture" />
  <img src="https://img.shields.io/badge/License-MIT-0EA5E9?style=for-the-badge&logo=open-source-initiative&logoColor=white" alt="License" />
</p>

<!-- QUICK NAVIGATION -->
<p align="center">
  <a href="#-core-innovations">Core Innovations</a> •
  <a href="#-project-directory-structure">Directory Structure</a> •
  <a href="#-package-architecture-49-java-classes">Package Architecture</a> •
  <a href="#-tech-stack--dependencies">Tech Stack</a> •
  <a href="#-quick-start--run-guide">Quick Start</a>
</p>

</div>

---

## 🚀 Core Innovations

<table width="100%">
<tr>
<td width="50%" valign="top">

### 🛡️ Multi-Channel Proctoring Engine
- **JVM Focus Loss Interceptor**: Real-time detection of OS window deactivations (`Alt + Tab`, Windows key, application switching).
- **Global Keystroke Filtering**: Hooks `KeyboardFocusManager` to block cheating shortcuts (`Ctrl + C`, `Ctrl + V`, `Alt + F4`, function keys).
- **Automated Webcam Surveillance**: Background daemon capturing periodic visual surveillance frames with dynamic threat index scoring.
- **3-Strike Kiosk Termination**: Automated emergency submission triggering upon exceeding candidate violation thresholds.

</td>
<td width="50%" valign="top">

### 🔐 Cryptographic Defense & Session Integrity
- **PBKDF2-HMAC-SHA256**: 120,000 iterative key-stretching rounds via `javax.crypto.SecretKeyFactory` to defeat GPU/ASIC brute-force cracking.
- **128-Bit CSPRNG Salts**: Cryptographically secure salts generated per account via `java.security.SecureRandom`.
- **Transparent Migration**: Automatically upgrades legacy credentials to PBKDF2 upon successful candidate authentication.
- **Concurrent Session Locking**: Validates active UUID tokens and runs 30-second heartbeat pings to eliminate multi-device proxy logins.

</td>
</tr>
<tr>
<td width="50%" valign="top">

### 📊 Psychometric Item Analytics (CTT)
- **Facility Value (Item Difficulty)**:
  $$P = \frac{R}{N}$$
  *Algorithms automatically classify items into Optimal, Easy, or Ambiguous difficulty tiers.*
- **Discrimination Index ($D$)**:
  $$D = \frac{U - L}{N_G}$$
  *Calculates item discrimination power between upper and lower 27% student cohorts (Kelley's Rule).*
- **Distractor Efficiency Audit**: Identifies dead choices and highlights confusing answer alternatives.

</td>
<td width="50%" valign="top">

### 📜 Tamper-Proof Vector Certification
- **Dynamic PDF Compilation**: Automated vector PDF credential rendering with candidate metrics, grade badges, and institutional seals.
- **SHA-256 Digital Fingerprint**: Computes unique cryptographic integrity checksum across student ID, exam ID, and score.
- **2D QR Matrix Verification**: High-density QR matrix verifiable against official database records via `VerifyCertificateUI.java`.

</td>
</tr>
<tr>
<td width="50%" valign="top">

### ⚡ Zero-Latency Desktop Experience
- **FlatLaf Modern Design System**: Clean light SaaS theme, sub-pixel LCD font anti-aliasing hints, and responsive glassmorphic cards.
- **EDT Concurrency Isolation**: All heavy database queries and hardware I/O operations are offloaded from the Event Dispatch Thread to maintain 60 FPS responsiveness.
- **Custom Java2D Polar Graphics**: Trigonometric multi-axis competency radar projection visualizing candidate skill distribution.

</td>
<td width="50%" valign="top">

### 🔄 Resilient Persistence & Transactions
- **Type-4 JDBC Architecture**: Centralized thread-safe connection pooling in `DBConnection.java` with server-time query logic (`SELECT NOW()`).
- **Two-Phase Atomic Transactions**: Full `COMMIT` and `ROLLBACK` guarantees saving exam attempts and individual responses synchronously.
- **Idempotent Schema Migration**: `SchemaMigrator.java` dynamically evolves table structures and seeds catalogs at startup with zero data loss.

</td>
</tr>
</table>

---

## 📂 Project Directory Structure

```
Online-Exam-System/
├── java files/                           # All 49 Java source files organized into canonical packages
│   ├── com/onlineexam/
│   │   ├── main/                         # Bootstrap & Application Lifecycle (Main.java)
│   │   ├── dao/                          # Database Access, PBKDF2 Hashing, Sessions, & CTT (12 classes)
│   │   ├── data/                         # Domain Models, In-Memory State & PDF Builder (4 classes)
│   │   └── ui/                           # Design Tokens, Glassmorphism & Animations (13 classes)
│   │       ├── student/                  # Candidate Dashboard, Proctored Runner & Scorecards (9 classes)
│   │       └── admin/                    # Admin Command Center, Proctor Logs & Analytics (8 classes)
│   └── TestDB.java                       # Database Diagnostic Utility (1 class)
│
├── sql/                                  # Relational Database Migration & Seed Scripts
│   ├── database_setup.sql                # 8 Normalized InnoDB Tables & Foreign Keys
│   └── sample_data.sql                   # Default Admin/Student Accounts & 100 Sample MCQs
│
├── docs/                                 # Project Documentation & Reports
│   ├── Online_Exam_System_Java_Project_Report.docx # Word Report (1.50 MB, Times New Roman, Pure Black)
│   ├── Java_Project_Report_Pagewise.md   # Complete Markdown Report (All 49 Classes Documented)
│   └── archive/                          # Historical Revisions & Drafts Archive
│
├── lib/                                  # Pre-Packaged Runtime Dependencies (FlatLaf, MySQL, OpenPDF, Webcam)
├── reportimages/                         # High-Resolution Architectural Flowcharts & UI Screen Captures
├── tools/                                # Offscreen Screenshot Capture & Rendering Utilities
├── run.bat                               # Windows Command Prompt One-Click Launcher
├── run.ps1                               # Native PowerShell One-Click Launcher
├── README.md                             # Project Showcase Documentation
└── LICENSE                               # MIT Open Source License
```

---

## 📂 Package Architecture (49 Java Classes)

| Package | Class Count | Architectural Responsibility | Key Design Patterns |
|:---|:---:|:---|:---|
| **`com.onlineexam.main`** | 1 | Application bootstrap, Look & Feel initialization, pre-flight checks, and EDT scheduling. | *Bootstrap Coordinator* |
| **`com.onlineexam.dao`** | 12 | Thread-safe JDBC persistence, PBKDF2 key stretching, session locking, proctoring persistence, and CTT analytics. | *DAO, Singleton, Factory, Service* |
| **`com.onlineexam.data`** | 4 | Immutable domain models, thread-safe in-memory state repository, answer evaluation, and vector PDF compilation. | *DTO, Value Object, Builder, Engine* |
| **`com.onlineexam.ui`** | 13 | Reusable UI design system tokens, glassmorphic containers, physics animations, and authentication views. | *Component, Composite, Observer* |
| **`com.onlineexam.ui.student`** | 9 | Candidate portal, assessment catalog, webcam consent, distraction-free kiosk exam runner, scorecards, and review. | *MVC View, State, Template Method* |
| **`com.onlineexam.ui.admin`** | 8 | Administrator command center, exam authoring, question bank management, psychometrics console, and proctor audit. | *Dashboard, TableRenderer, Observer* |
| **`com.onlineexam.test`** | 1 | Database network socket handshake and credential verification CLI utility. | *Diagnostic CLI* |

<details>
<summary><b>📋 Click to View the Complete 49-Class Inventory</b></summary>
<br/>

```
com.onlineexam.main
  └── Main.java
com.onlineexam.dao
  ├── DBConnection.java          ├── SchemaMigrator.java        ├── UserDAO.java
  ├── ExamDAO.java               ├── QuestionDAO.java           ├── AttemptDAO.java
  ├── SessionDAO.java            ├── SessionHeartbeat.java      ├── ProctorDAO.java
  ├── WebcamProctorService.java  ├── AchievementService.java    ├── CertificateService.java
  └── QuestionAnalyticsDAO.java
com.onlineexam.data
  ├── ExamConfig.java            ├── ExamStore.java
  ├── AnswerEvaluator.java       └── CertificatePdfBuilder.java
com.onlineexam.ui
  ├── UITheme.java               ├── Anim.java                  ├── GlassPanel.java
  ├── AuroraBackground.java      ├── AuthComponents.java        ├── DashboardComponents.java
  ├── PasswordStrengthMeter.java ├── ConfettiPanel.java         ├── RadarChartPanel.java
  ├── GlassDialog.java           ├── DemoFrame.java             ├── LoginUI.java
  └── SignupUI.java
com.onlineexam.ui.student
  ├── StudentDashboard.java      ├── SubjectSelectionUI.java    ├── InstructionsUI.java
  ├── ExamModule.java            ├── ResultUI.java              ├── ReviewUI.java
  ├── StudentPerformanceUI.java  ├── ProfilePanel.java          └── AchievementsPanel.java
com.onlineexam.ui.admin
  ├── AdminDashboard.java        ├── ExamManagementUI.java      ├── AddQuestionUI.java
  ├── ViewQuestionsUI.java       ├── PerformanceAnalysisUI.java ├── QuestionAnalyticsUI.java
  ├── ProctorLogUI.java          └── VerifyCertificateUI.java
Default Package
  └── TestDB.java
```

</details>

---

## 🛠️ Tech Stack & Dependencies

<div align="center">
  <img src="https://skillicons.dev/icons?i=java,mysql,git,github,vscode,idea,eclipse" alt="Tech Stack Icons" />
</div>

<br/>

| Layer | Component | Version | Purpose |
|:---|:---|:---:|:---|
| **Core Runtime** | **Java SE (OpenJDK)** | `17+ / 21 LTS` | Object-oriented application logic, multithreaded concurrency |
| **UI Framework** | **Java Swing & Java2D** | Built-in | Window management, LCD sub-pixel font rendering, vector graphics |
| **Look & Feel** | **FlatLaf** | `3.5.x` | Modern light design tokens, rounded borders, glassmorphic cards |
| **Relational Database** | **MySQL Server (InnoDB)** | `8.0+` | Normalized relational storage, foreign keys, ACID transactions |
| **Database Driver** | **MySQL Connector/J** | `9.x` | High-performance Type-4 JDBC driver with batch execution |
| **Cryptography** | **Java Cryptography Architecture** | Built-in | PBKDF2WithHmacSHA256 stretching, 128-bit CSPRNG salts |
| **Document Engine** | **OpenPDF** | `1.3.40` | Archival vector PDF certificate generation |
| **QR Code Matrix** | **ZXing Core** | `3.5.3` | High-density 2D QR matrix generation and parsing |
| **Hardware Video** | **Webcam-Capture & BridJ** | `0.3.12` | Asynchronous webcam frame surveillance daemon |

---

## ⚡ Quick Start & Run Guide

### 1. Prerequisites
- **JDK**: Java Development Kit 17 or higher (`java -version`).
- **MySQL**: MySQL Server 8.0 running on `localhost:3306`.

### 2. Database Initialization
Execute the SQL setup scripts in MySQL Workbench or via CLI:
```bash
mysql -u root -p < sql/database_setup.sql
mysql -u root -p < sql/sample_data.sql
```

### 3. One-Click Launch (Windows)

#### Native PowerShell:
```powershell
.\run.ps1
```

#### Windows Command Prompt:
```cmd
run.bat
```

#### Manual Compilation & Launch:
```powershell
# Compile all source files from java files/ into out/ directory
javac -cp "lib/*" -d out (Get-ChildItem -Path "java files" -Filter *.java -Recurse | Select-Object -ExpandProperty FullName)

# Verify database connection
java -cp "out;lib/*" TestDB

# Launch the Application
java -cp "out;lib/*" com.onlineexam.main.Main
```

---

<div align="center">

<!-- ANIMATED FOOTER WAVE -->
<img src="https://capsule-render.vercel.app/api?type=waving&color=gradient&customColorList=6,11,18,24&height=120&section=footer" width="100%" alt="Footer Banner" />

<p align="center">
  <sub>Engineered with ❤️ in Core Java & MySQL • Released under the MIT License</sub>
</p>

</div>

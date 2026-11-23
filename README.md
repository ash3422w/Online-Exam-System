<!-- ============================================================ -->
<!-- ONLINE EXAMINATION MANAGEMENT SYSTEM (OEMS) - GITHUB SHOWCASE -->
<!-- ============================================================ -->

<div align="center">

<!-- ANIMATED 3D GRADIENT WAVING BANNER -->
<img src="https://capsule-render.vercel.app/api?type=waving&color=gradient&customColorList=6,11,18,24&height=280&section=header&text=ONLINE%20EXAMINATION%20SYSTEM&fontSize=40&fontColor=ffffff&animation=twinkling&desc=Enterprise%20Desktop%20CBT%20•%20Hardware%20Proctoring%20•%20Psychometrics&descSize=17&descAlignY=68" width="100%" alt="Header Banner" />

<!-- ANIMATED TYPING SVG -->
<a href="https://github.com/ash3422w/Online-Exam-System">
  <img src="https://readme-typing-svg.demolab.com?font=Fira+Code&weight=600&size=20&duration=2800&pause=1000&color=6366F1&center=true&vCenter=true&width=750&lines=⚡+Zero-Latency+Java+Swing+%2B+FlatLaf+Desktop+Engine;🛡️+Hardware-Level+Window+Focus+%26+Webcam+Surveillance;🔐+Military-Grade+PBKDF2-HMAC-SHA256+Cryptography;📊+Real-Time+Classical+Test+Theory+(CTT)+Item+Analytics;📜+SHA-256+Tamper-Proof+PDF+Certificates+with+QR+Ledger;🚀+ACID+Two-Phase+Transactions+with+MySQL+8.0" alt="Typing SVG" />
</a>

<br/><br/>

<!-- SHIELD BADGES ROW -->
<p align="center">
  <img src="https://img.shields.io/badge/Java-17%2B%20%7C%2021%20LTS-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white" alt="Java" />
  <img src="https://img.shields.io/badge/MySQL-8.0%20InnoDB-4479A1?style=for-the-badge&logo=mysql&logoColor=white" alt="MySQL" />
  <img src="https://img.shields.io/badge/GUI-FlatLaf%203.5-6366F1?style=for-the-badge&logo=java&logoColor=white" alt="FlatLaf" />
  <img src="https://img.shields.io/badge/Security-PBKDF2--HMAC--SHA256-10B981?style=for-the-badge&logo=shield&logoColor=white" alt="Security" />
  <img src="https://img.shields.io/badge/License-MIT-0EA5E9?style=for-the-badge&logo=open-source-initiative&logoColor=white" alt="License" />
</p>

<p align="center">
  <a href="#-key-features">Key Features</a> •
  <a href="#-system-architecture">Architecture</a> •
  <a href="#-application-screens">Live Screens</a> •
  <a href="#-quick-start">Quick Start</a> •
  <a href="#-project-report">Project Report</a>
</p>

</div>

---

## 🌟 Executive Overview

**Online Examination Management System (OEMS)** is a next-generation desktop Computer-Based Testing (CBT) platform engineered in **Core Java (Swing, Java2D)** and **MySQL 8.0**. 

Unlike conventional browser-based portals that are vulnerable to inspect-element tampering, unmonitored window switching, and network disconnect dropouts, OEMS runs as an isolated, hardware-accelerated desktop application that enforces JVM-level event interception, cryptographic credential protection, automated remote proctoring, and sub-millisecond execution response.

<div align="center">
  <img src="reportimages/fig01archflowchart.png" width="92%" alt="Multi-Tier Enterprise Architecture" />
</div>

---

## 🚀 Key Architectural Innovations

<table width="100%">
<tr>
<td width="50%" valign="top">

### 🛡️ Multi-Channel Proctoring
- **JVM Focus Loss Interceptor**: Listens to OS-level window focus deactivations (`Alt+Tab`, `Win` key, push notifications).
- **Global Key Hook Filtering**: Blocks cheating shortcuts (`Ctrl+C`, `Ctrl+V`, `Alt+F4`, function keys).
- **Webcam Surveillance Daemon**: Captures candidate snapshots at randomized intervals with cumulative threat scoring.
- **3-Strike Termination**: Automatically triggers emergency test submission upon exceeding violation limits.

</td>
<td width="50%" valign="top">

### 🔐 Military-Grade Cryptography
- **PBKDF2-HMAC-SHA256**: 120,000 iterative key-stretching rounds via `javax.crypto.SecretKeyFactory`.
- **128-Bit CSPRNG Salts**: Cryptographically secure salts generated per user via `SecureRandom`.
- **Automatic Legacy Migration**: Seamlessly upgrades legacy plaintext/SHA-256 hashes to PBKDF2 upon login.
- **Session Locking**: Prevents concurrent logins across multiple devices using active UUID session tokens.

</td>
</tr>
<tr>
<td width="50%" valign="top">

### 📊 Psychometric Item Analytics
- **Classical Test Theory (CTT)**:
  $$\text{Facility Index (Difficulty) } P = \frac{R}{N}$$
  $$\text{Discrimination Index } D = \frac{U - L}{N_G}$$
- **Kelley 27% Cohort Rule**: Computes discrimination power between top and bottom student quartiles.
- **Distractor Efficiency Audit**: Flags non-functional choices and identifies ambiguous question stems.

</td>
<td width="50%" valign="top">

### 📜 Tamper-Proof Certification
- **High-Density Vector PDF**: Generates archival certificates embedding candidate bonafides and institutional seals.
- **SHA-256 Cryptographic Signature**: Calculates unique digital checksum across candidate score and timestamp.
- **2D QR Matrix Verification**: Instant public and administrative verification via `VerifyCertificateUI.java`.

</td>
</tr>
</table>

---

## 📸 Complete Application Flow & Screen Demonstrations

<div align="center">

### 🎮 Candidate Assessment Experience

<table>
<tr>
  <td align="center" width="50%">
    <b>Split-Screen Authentication Gateway (<code>LoginUI</code>)</b><br/>
    <img src="reportimages/screen01login.png" width="98%" alt="Login UI" /><br/>
    <sub>PBKDF2-HMAC-SHA256 password derivation with database ping indicator</sub>
  </td>
  <td align="center" width="50%">
    <b>Candidate Portal & Bento Dashboard (<code>StudentDashboard</code>)</b><br/>
    <img src="reportimages/screen02studentdashboard.png" width="98%" alt="Student Dashboard" /><br/>
    <sub>KPI summary cards, test attempt history, and quick launch tiles</sub>
  </td>
</tr>
<tr>
  <td align="center" width="50%">
    <b>Assessment Catalog & Availability Gates (<code>SubjectSelectionUI</code>)</b><br/>
    <img src="reportimages/screen03subjectselection.png" width="98%" alt="Subject Selection" /><br/>
    <sub>Countdown schedule gates, attempt limit counters, and subject cards</sub>
  </td>
  <td align="center" width="50%">
    <b>Proctoring Consent & Hardware Briefing (<code>InstructionsUI</code>)</b><br/>
    <img src="reportimages/screen04instructions.png" width="98%" alt="Instructions" /><br/>
    <sub>Mandatory webcam permission, exam rules, and integrity policies</sub>
  </td>
</tr>
<tr>
  <td align="center" width="50%">
    <b>Distraction-Free Proctored Exam Runner (<code>ExamModule</code>)</b><br/>
    <img src="reportimages/screen05exammodule.png" width="98%" alt="Exam Module" /><br/>
    <sub>Live countdown timer, question palette navigation, and instant auto-save</sub>
  </td>
  <td align="center" width="50%">
    <b>Executive Result Scorecard (<code>ResultUI</code>)</b><br/>
    <img src="reportimages/screen09resultscorecard.png" width="98%" alt="Result Scorecard" /><br/>
    <sub>Score breakdown, percentage accuracy, and one-click PDF generation</sub>
  </td>
</tr>
</table>

<br/>

### 🛡️ Security, Proctoring & Administration Command Center

<table>
<tr>
  <td align="center" width="50%">
    <b>Remote Proctoring Audit Log (<code>ProctorLogUI</code>)</b><br/>
    <img src="reportimages/screen06proctorlog.png" width="98%" alt="Proctor Log" /><br/>
    <sub>Timestamped violation records, threat index scoring, and snapshot gallery</sub>
  </td>
  <td align="center" width="50%">
    <b>Psychometric Item Analytics (<code>QuestionAnalyticsUI</code>)</b><br/>
    <img src="reportimages/screen07questionanalytics.png" width="98%" alt="Question Analytics" /><br/>
    <sub>Facility Index (P) & Discrimination (D) metrics flagging flawed questions</sub>
  </td>
</tr>
<tr>
  <td align="center" width="50%">
    <b>Multi-Axis Competency Radar Chart (<code>StudentPerformanceUI</code>)</b><br/>
    <img src="reportimages/screen10performanceradar.png" width="98%" alt="Performance Radar" /><br/>
    <sub>Custom Java2D trigonometric polar projection of subject masteries</sub>
  </td>
  <td align="center" width="50%">
    <b>Tamper-Proof Certificate Verification (<code>VerifyCertificateUI</code>)</b><br/>
    <img src="reportimages/screen08verifycertificate.png" width="98%" alt="Verify Certificate" /><br/>
    <sub>Public SHA-256 digital signature validation against MySQL ledger</sub>
  </td>
</tr>
</table>

</div>

---

## 🏗️ System Architecture & Visual Process Models

<details>
<summary><b>🔍 Click to Expand Architectural Models & State Machines</b></summary>
<br/>

### 1. Examination Lifecycle State Machine
Candidate progress is guarded by strict state machine transitions from identity verification to automated timeout submission:
<div align="center">
  <img src="reportimages/fig02examlifecycleflowchart.png" width="85%" alt="Lifecycle State Machine" />
</div>

### 2. Proctoring & Academic Integrity Subsystem
Layered interceptor model auditing OS focus loss, keyboard scan codes, and webcam surveillance:
<div align="center">
  <img src="reportimages/fig03proctoringflowchart.png" width="85%" alt="Proctoring Subsystem" />
</div>

### 3. Object-Oriented Class Hierarchy & Package Decoupling
Enterprise separation of concerns across 49 classes and 7 packages:
<div align="center">
  <img src="reportimages/fig04classhierarchy.png" width="85%" alt="Class Hierarchy" />
</div>

</details>

---

## 📂 Package Architecture (49 Java Classes)

```
com.onlineexam
├── main          # [1 Class] Application bootstrap, FlatLaf initialization, & schema checks
│   └── Main.java
├── dao           # [12 Classes] Thread-safe Type-4 JDBC, PBKDF2 hashing, sessions, & CTT analytics
│   ├── DBConnection.java       ├── SchemaMigrator.java     ├── UserDAO.java
│   ├── ExamDAO.java            ├── QuestionDAO.java        ├── AttemptDAO.java
│   ├── SessionDAO.java         ├── SessionHeartbeat.java   ├── ProctorDAO.java
│   ├── WebcamProctorService.java├── AchievementService.java ├── QuestionAnalyticsDAO.java
│   └── CertificateService.java
├── data          # [4 Classes] Domain models, in-memory state, scoring, & vector PDF builder
│   ├── ExamConfig.java         ├── ExamStore.java
│   ├── AnswerEvaluator.java    └── CertificatePdfBuilder.java
├── ui            # [13 Classes] Reusable design tokens, glassmorphism, animations, & auth views
│   ├── UITheme.java            ├── Anim.java               ├── GlassPanel.java
│   ├── AuroraBackground.java   ├── AuthComponents.java     ├── DashboardComponents.java
│   ├── PasswordStrengthMeter.java├── ConfettiPanel.java    ├── RadarChartPanel.java
│   ├── GlassDialog.java        ├── DemoFrame.java          ├── LoginUI.java
│   └── SignupUI.java
├── ui.student    # [9 Classes] Candidate testing portal, proctored runner, results, & review
│   ├── StudentDashboard.java   ├── SubjectSelectionUI.java ├── InstructionsUI.java
│   ├── ExamModule.java         ├── ResultUI.java           ├── ReviewUI.java
│   ├── StudentPerformanceUI.java├── ProfilePanel.java     └── AchievementsPanel.java
├── ui.admin      # [8 Classes] Administration console, proctoring gallery, & question bank
│   ├── AdminDashboard.java     ├── ExamManagementUI.java   ├── AddQuestionUI.java
│   ├── ViewQuestionsUI.java    ├── PerformanceAnalysisUI.java├── QuestionAnalyticsUI.java
│   ├── ProctorLogUI.java       └── VerifyCertificateUI.java
└── test          # [1 Class] Database socket & driver diagnostic tool
    └── TestDB.java
```

---

## 🛠️ Tech Stack & Dependencies

<div align="center">
  <img src="https://skillicons.dev/icons?i=java,mysql,git,github,vscode,idea,eclipse" alt="Tech Stack Icons" />
</div>

| Layer | Technology | Purpose |
|:---|:---|:---|
| **Language** | **Java SE 17+ / 21 LTS** | Core object-oriented runtime, multithreaded concurrency |
| **Presentation** | **Java Swing & Java2D** | Desktop window management, LCD anti-aliasing, vector graphics |
| **Theme & UX** | **FlatLaf 3.5.x** | Modern light SaaS design system, glassmorphism, and rounded cards |
| **Relational Database** | **MySQL 8.0 (InnoDB)** | ACID compliant transactions, referential foreign key integrity |
| **Connectivity** | **MySQL Connector/J 9.x** | High-performance Type-4 JDBC driver with statement batching |
| **Cryptography** | **Java Cryptography Architecture** | PBKDF2WithHmacSHA256 key stretching, CSPRNG salts |
| **PDF & QR Engine** | **OpenPDF 1.3.40 & ZXing 3.5.3** | Dynamic vector certificate rendering, 2D QR matrix generation |
| **Surveillance** | **Webcam-Capture 0.3.12 & BridJ** | Hardware video capture daemon for proctoring snapshots |

---

## ⚡ Quick Start & Run Guide

### 1. Prerequisites
- **JDK**: Java Development Kit 17 or higher (`java -version`).
- **MySQL**: MySQL Server 8.0 running on localhost port 3306.

### 2. Database Initialization
Execute the setup scripts in MySQL Workbench or via CLI:
```bash
mysql -u root -p < database_setup.sql
mysql -u root -p < sample_data.sql
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
# Compile all classes into out/ directory
javac -cp "lib/*" -d out *.java

# Run database diagnostic check
java -cp "out;lib/*" TestDB

# Launch the Application
java -cp "out;lib/*" com.onlineexam.main.Main
```

### 4. Default Seed Credentials

| Role | Username | Password | Access Level |
|:---|:---|:---|:---|
| **Administrator** | `admin` | `admin123` | Full admin console, question management, proctor logs, analytics |
| **Student Candidate** | `student1` | `pass123` | Student dashboard, exam catalog, test runner, certificates |
| **Student Candidate** | `student2` | `pass123` | Student dashboard, exam catalog, test runner, certificates |

---

## 📑 Formal Academic Project Report

The repository includes the complete, formal academic project documentation adhering to standard university submission rubrics:

- 📄 **Word Document**: [`Online_Exam_System_Java_Project_Report.docx`](Online_Exam_System_Java_Project_Report.docx) *(1.50 MB, Times New Roman, pure black styling, native Word page breaks, and embedded high-res diagrams)*
- 📝 **Markdown Report**: [`Java_Project_Report_Pagewise.md`](Java_Project_Report_Pagewise.md)
- 🌐 **Live Google Doc**: [Google Doc Project Report](https://docs.google.com/document/d/1wLFKMi-yDf_eAy1hBuASCnsFNO_sUGTMHFJLKsiCr-4/edit)

---

## 👤 Author & Maintainer

<table align="center">
  <tr>
    <td align="center">
      <a href="https://github.com/ash3422w">
        <img src="https://avatars.githubusercontent.com/u/230601269?v=4" width="100px;" alt="Ashok Reddy" /><br />
        <sub><b>Ashok Reddy (ash3422w)</b></sub>
      </a><br />
      <sub>Lead Software Architect & Security Engineer</sub>
    </td>
  </tr>
</table>

<div align="center">

<!-- ANIMATED FOOTER WAVE -->
<img src="https://capsule-render.vercel.app/api?type=waving&color=gradient&customColorList=6,11,18,24&height=120&section=footer" width="100%" alt="Footer Banner" />

<p align="center">
  <sub>Engineered with ❤️ in Core Java & MySQL • Released under the MIT License</sub>
</p>

</div>

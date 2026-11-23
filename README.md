# Online Exam System

## Project Description
The Online Exam System is a desktop-based application developed using Java, Swing, JDBC, and MySQL. It conducts timed online examinations with randomized question sets, automatic submission, transactional database persistence, and student performance analytics & ranking.

---

## Features
- Role-based User Login (Admin and Student) and Student Registration
- Isolated per-student session management (fresh state on login, cleared on logout)
- Centralized JDBC connection management (`DBConnection.java`) with `try-with-resources`
- Subject Selection loaded from MySQL
- Advanced Random Question Generation (20 unique randomized questions per exam, or all available if fewer than 20)
- Exam Timer in `MM:SS` format with 5-minute warning, Previous/Next + clickable Question Navigator palette, manual confirmation, single-submission lock, and automatic submission on timeout
- Atomic JDBC Transaction Management (`COMMIT` and `ROLLBACK`) saving exam attempts and individual question answers together
- Automatic Evaluation, Result Display (Score, Percentage, Grade, Pass/Fail, Submission Type, Time Taken), and Card-based Answer Review
- Student Performance History (personal summary stats, rank, subject filter, and attempt history)
- Admin Performance Analysis & Leaderboard (ranked by percentage with tie handling and subject filtering)
- Full Question Management (Add, View, Edit, and Delete Questions) and Subject-wise Attempt Reports (Admin)
- Modern Light SaaS Swing UI (`UITheme.java`) with rounded cards, gradient headers, hover effects, and status badges

---

## Technologies Used
- Programming Language: Java
- User Interface: Java Swing
- Database: MySQL (InnoDB)
- Connectivity: JDBC (`mysql-connector-j`)

---

## Database Setup (`online_exam_db`)

Create the database and tables using `ENGINE=InnoDB` (required for foreign keys and JDBC `COMMIT`/`ROLLBACK` transactions):

```sql
CREATE DATABASE IF NOT EXISTS online_exam_db;
USE online_exam_db;

CREATE TABLE IF NOT EXISTS users (
    id INT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(100) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS subjects (
    id INT PRIMARY KEY AUTO_INCREMENT,
    subject_name VARCHAR(100) NOT NULL UNIQUE
) ENGINE=InnoDB;

INSERT IGNORE INTO subjects (id, subject_name) VALUES
    (1, 'Java'),
    (2, 'DBMS'),
    (3, 'SQL'),
    (4, 'Aptitude');

CREATE TABLE IF NOT EXISTS questions (
    id INT PRIMARY KEY AUTO_INCREMENT,
    subject_id INT NOT NULL,
    question TEXT NOT NULL,
    option1 VARCHAR(255) NOT NULL,
    option2 VARCHAR(255) NOT NULL,
    option3 VARCHAR(255) NOT NULL,
    option4 VARCHAR(255) NOT NULL,
    correct_option INT NOT NULL,
    FOREIGN KEY (subject_id) REFERENCES subjects(id)
) ENGINE=InnoDB;
```

> **Note:** The `exam_attempts` and `exam_attempt_answers` tables (both `ENGINE=InnoDB` with foreign key relationship) are created automatically at startup by `AttemptDAO.initializeAttemptTable()`.

---

## How to Configure, Compile, and Run

1. Install JDK (Java 11+) and MySQL 8 Server.
2. Execute `database_setup.sql` in MySQL Workbench to create `online_exam_db` and all 5 `InnoDB` tables.
3. *(Optional)* Execute `sample_data.sql` in MySQL Workbench to seed default accounts (`admin`/`admin123`, `student1`/`pass123`, `student2`/`pass123`) and 100 sample MCQs (25 per subject).
4. Update MySQL `URL`, `USER`, and `PASS` in `DBConnection.java` if needed.
5. **Quick Launch (Windows)**: Double-click **`run.bat`** (automatically compiles into `out\` and runs using `lib\mysql-connector-j.jar` and `lib\flatlaf.jar`).
6. **Manual CLI Commands**:
   ```powershell
   javac -cp "lib\mysql-connector-j.jar;lib\flatlaf.jar" -d out *.java
   java -cp "out;lib\mysql-connector-j.jar;lib\flatlaf.jar" TestDB
   java -cp "out;lib\mysql-connector-j.jar;lib\flatlaf.jar" com.onlineexam.main.Main
   ```

---

## Project Structure
All 20 Java source files live in the project root directory and compile into `com.onlineexam.*` packages (plus `TestDB` in the default package):
- `Main.java` (`com.onlineexam.main`)
- `DBConnection.java`, `UserDAO.java`, `QuestionDAO.java`, `AttemptDAO.java` (`com.onlineexam.dao`)
- `ExamStore.java` (`com.onlineexam.data`)
- `LoginUI.java`, `SignupUI.java` (`com.onlineexam.ui`)
- `AdminDashboard.java`, `AddQuestionUI.java`, `ViewQuestionsUI.java`, `PerformanceAnalysisUI.java` (`com.onlineexam.ui.admin`)
- `StudentDashboard.java`, `SubjectSelectionUI.java`, `InstructionsUI.java`, `ExamModule.java`, `ResultUI.java`, `ReviewUI.java`, `StudentPerformanceUI.java` (`com.onlineexam.ui.student`)
- `TestDB.java` (default package)

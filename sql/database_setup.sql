-- ============================================================================
-- Online Examination Management System - MySQL 8 Database Setup Script
-- ============================================================================
-- Matches the exact schema and queries used by:
--   DBConnection.java, UserDAO.java, QuestionDAO.java, AttemptDAO.java,
--   ExamStore.java, AddQuestionUI.java, SubjectSelectionUI.java, ExamModule.java
--
-- Safe to run multiple times (idempotent: IF NOT EXISTS / INSERT IGNORE).
-- All tables use ENGINE=InnoDB to support Foreign Keys and JDBC Transactions
-- (COMMIT / ROLLBACK).
-- ============================================================================

-- 1. Create and select database
CREATE DATABASE IF NOT EXISTS online_exam_db
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE online_exam_db;

-- 2. Users table (Admin and Student authentication & registration)
-- Used by: UserDAO.java (addUser, registerUser, validateUser)
CREATE TABLE IF NOT EXISTS users (
    id INT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(100) NOT NULL,
    password VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL,
    UNIQUE KEY uk_users_username (username),
    CONSTRAINT chk_users_role CHECK (role IN ('admin', 'student'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3. Subjects table
-- Used by: SubjectSelectionUI.java, AttemptDAO.java (getAvailableSubjects)
-- Subject IDs match constants in ExamStore.java:
--   1 = Java, 2 = DBMS, 3 = SQL, 4 = Aptitude
CREATE TABLE IF NOT EXISTS subjects (
    id INT PRIMARY KEY AUTO_INCREMENT,
    subject_name VARCHAR(100) NOT NULL,
    UNIQUE KEY uk_subjects_name (subject_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4. Questions table (MCQ Question Bank)
-- Used by: QuestionDAO.java (loadQuestionsFromDB, getRandomQuestionsBySubject),
--          AddQuestionUI.java (addQuestion)
CREATE TABLE IF NOT EXISTS questions (
    id INT PRIMARY KEY AUTO_INCREMENT,
    subject_id INT NOT NULL,
    question TEXT NOT NULL,
    option1 VARCHAR(500) NOT NULL,
    option2 VARCHAR(500) NOT NULL,
    option3 VARCHAR(500) NOT NULL,
    option4 VARCHAR(500) NOT NULL,
    correct_option INT NOT NULL,
    INDEX idx_questions_subject_id (subject_id),
    CONSTRAINT chk_questions_correct_option CHECK (correct_option BETWEEN 1 AND 4),
    CONSTRAINT fk_questions_subject
        FOREIGN KEY (subject_id) REFERENCES subjects(id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 5. Exam Attempts table (Score, Timer, Submission Type, Performance & Ranking)
-- Used by: AttemptDAO.java (initializeAttemptTable, saveAttempt,
--          saveAttemptWithAnswers, loadAttemptsFromDB, getStudentLeaderboard,
--          getAttemptDetails, getStudentPerformanceSummary)
CREATE TABLE IF NOT EXISTS exam_attempts (
    id INT PRIMARY KEY AUTO_INCREMENT,
    user_id VARCHAR(100) NOT NULL,
    subject_id INT NOT NULL,
    score INT NOT NULL,
    total_questions INT NOT NULL DEFAULT 20,
    time_taken_seconds INT NOT NULL DEFAULT 0,
    submission_type VARCHAR(20) NOT NULL DEFAULT 'Manual',
    attempted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_exam_attempts_user_id (user_id),
    INDEX idx_exam_attempts_subject_id (subject_id),
    CONSTRAINT chk_exam_attempts_score CHECK (score >= 0),
    CONSTRAINT chk_exam_attempts_total_questions CHECK (total_questions > 0),
    CONSTRAINT chk_exam_attempts_time_taken CHECK (time_taken_seconds >= 0),
    CONSTRAINT chk_exam_attempts_submission_type CHECK (submission_type IN ('Manual', 'Automatic')),
    CONSTRAINT fk_exam_attempts_subject
        FOREIGN KEY (subject_id) REFERENCES subjects(id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 6. Exam Attempt Answers table (Transactional per-question answer storage)
-- Used by: AttemptDAO.java (initializeAttemptTable, saveAttemptWithAnswers,
--          getAttemptAnswers)
-- selected_option is 1..4 when answered, or NULL when unanswered.
CREATE TABLE IF NOT EXISTS exam_attempt_answers (
    id INT PRIMARY KEY AUTO_INCREMENT,
    attempt_id INT NOT NULL,
    question_id INT NOT NULL,
    selected_option INT NULL,
    correct_option INT NOT NULL,
    is_correct BOOLEAN NOT NULL,
    INDEX idx_exam_attempt_answers_attempt_id (attempt_id),
    INDEX idx_exam_attempt_answers_question_id (question_id),
    CONSTRAINT chk_attempt_answers_selected_option
        CHECK (selected_option IS NULL OR selected_option BETWEEN 1 AND 4),
    CONSTRAINT chk_attempt_answers_correct_option
        CHECK (correct_option BETWEEN 1 AND 4),
    CONSTRAINT fk_exam_attempt_answers_attempt
        FOREIGN KEY (attempt_id) REFERENCES exam_attempts(id)
        ON DELETE CASCADE
        ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 7. Seed default subjects matching ExamStore constants (idempotent)
INSERT IGNORE INTO subjects (id, subject_name) VALUES
    (1, 'Java'),
    (2, 'DBMS'),
    (3, 'SQL'),
    (4, 'Aptitude');

-- 8. Optional: Uncomment the line below to create an initial Admin account
-- INSERT IGNORE INTO users (username, password, role) VALUES ('admin', 'admin123', 'admin');

-- 9. Verification queries (uncomment if you want to inspect after execution)
-- SHOW TABLE STATUS FROM online_exam_db;
-- SELECT * FROM subjects ORDER BY id;
USE online_exam_db;
SHOW TABLE STATUS;
SELECT * FROM subjects ORDER BY id;
DESCRIBE exam_attempts;
DESCRIBE exam_attempt_answers;


-- ============================================================================
-- Automatic Schema Migrations Notice
-- ============================================================================
-- Additional tables and columns introduced by newer features are created
-- automatically and idempotently at application startup by
-- com.onlineexam.dao.SchemaMigrator (which checks INFORMATION_SCHEMA before
-- applying any CREATE TABLE or ALTER TABLE ADD COLUMN statements).
-- ============================================================================

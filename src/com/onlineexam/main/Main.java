package com.onlineexam.main;

import javax.swing.SwingUtilities;
import com.onlineexam.ui.LoginUI;
import com.onlineexam.ui.UITheme;
import com.onlineexam.dao.QuestionDAO;
import com.onlineexam.dao.AttemptDAO;
import com.onlineexam.dao.SchemaMigrator;
import com.onlineexam.dao.SessionHeartbeat;

public class Main {
    public static void main(String[] args) {

        // Initialize base tables and run idempotent schema migrations BEFORE loading questions
        AttemptDAO.initializeAttemptTable();
        SchemaMigrator.run();
        QuestionDAO.loadQuestionsFromDB();
        AttemptDAO.loadAttemptsFromDB();
        SessionHeartbeat.installShutdownHook();

        // Start UI
        SwingUtilities.invokeLater(() -> {
            UITheme.initGlobalLookAndFeel();
            new LoginUI();
        });
    }
}

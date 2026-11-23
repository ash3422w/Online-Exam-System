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
            try {
                System.out.println("[Main] Initializing UITheme...");
                UITheme.initGlobalLookAndFeel();
                System.out.println("[Main] Creating LoginUI...");
                new LoginUI();
                System.out.println("[Main] LoginUI created and visible!");
            } catch (Throwable t) {
                System.err.println("[Main] FATAL UI INITIALIZATION EXCEPTION:");
                t.printStackTrace();
            }
        });
    }
}

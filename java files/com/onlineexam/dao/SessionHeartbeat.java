package com.onlineexam.dao;

import com.onlineexam.data.ExamStore;
import com.onlineexam.ui.LoginUI;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import java.awt.Window;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Daemon background heartbeat manager for student sessions.
 * Pings SessionDAO.heartbeat every 15 seconds off the EDT; if another device takes over
 * while no exam is running, notifies the user and returns cleanly to LoginUI.
 */
public final class SessionHeartbeat {

    public static final long HEARTBEAT_INTERVAL_SECONDS = 15L;

    private static final Object LOCK = new Object();
    private static ScheduledExecutorService executor;
    private static ScheduledFuture<?> task;
    private static volatile String activeUserId;
    private static volatile String activeToken;
    private static final AtomicBoolean shutdownHookInstalled = new AtomicBoolean(false);
    private static final AtomicBoolean evictionInProgress = new AtomicBoolean(false);

    private SessionHeartbeat() {
    }

    public static void installShutdownHook() {
        if (shutdownHookInstalled.compareAndSet(false, true)) {
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                String uid = ExamStore.getCurrentUserId();
                String tok = ExamStore.getSessionToken();
                stop();
                if (uid != null && !uid.isEmpty() && tok != null && !tok.isEmpty()) {
                    try {
                        SessionDAO.logout(uid, tok);
                    } catch (Exception ignored) {}
                }
            }, "exam-session-shutdown"));
        }
    }

    public static void start(String userId, String token) {
        if (userId == null || userId.trim().isEmpty() || token == null || token.trim().isEmpty()) {
            return;
        }
        installShutdownHook();
        synchronized (LOCK) {
            stopInternal();
            evictionInProgress.set(false);
            activeUserId = userId.trim();
            activeToken = token.trim();

            executor = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "student-session-heartbeat");
                t.setDaemon(true);
                return t;
            });

            final String runUser = activeUserId;
            final String runToken = activeToken;
            task = executor.scheduleWithFixedDelay(() -> {
                // Verify we are still tracking the active local session
                if (!runToken.equals(ExamStore.getSessionToken())) {
                    stop();
                    return;
                }
                boolean valid = SessionDAO.heartbeat(runUser, runToken);
                if (!valid) {
                    // If an exam is running, session cannot be taken over by design -> continue
                    if (ExamStore.isCurrentlyInExam()) {
                        return;
                    }
                    // Displaced by a second login while idle -> sign out cleanly on EDT
                    if (evictionInProgress.compareAndSet(false, true)) {
                        stop();
                        SwingUtilities.invokeLater(() -> {
                            ExamStore.clearSession();
                            for (Window w : Window.getWindows()) {
                                if (w != null && w.isDisplayable()) {
                                    w.dispose();
                                }
                            }
                            JOptionPane.showMessageDialog(
                                    null,
                                    "You were signed in on another device.",
                                    "Session Ended",
                                    JOptionPane.WARNING_MESSAGE
                            );
                            new LoginUI();
                        });
                    }
                }
            }, HEARTBEAT_INTERVAL_SECONDS, HEARTBEAT_INTERVAL_SECONDS, TimeUnit.SECONDS);
        }
    }

    public static void stop() {
        synchronized (LOCK) {
            stopInternal();
        }
    }

    private static void stopInternal() {
        if (task != null) {
            task.cancel(false);
            task = null;
        }
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
        activeUserId = null;
        activeToken = null;
    }

    /**
     * Stops the heartbeat, clears ExamStore, deletes the active_sessions row off the EDT,
     * and invokes onComplete on the EDT.
     */
    public static void logoutAsync(Runnable onComplete) {
        final String uid = ExamStore.getCurrentUserId();
        final String tok = ExamStore.getSessionToken();
        stop();
        ExamStore.clearSession();

        if (uid == null || uid.isEmpty() || tok == null || tok.isEmpty()) {
            if (onComplete != null) onComplete.run();
            return;
        }

        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                SessionDAO.logout(uid, tok);
                return null;
            }

            @Override
            protected void done() {
                if (onComplete != null) onComplete.run();
            }
        }.execute();
    }
}

package com.studyagent;

import com.studyagent.ai.GeminiService;
import com.studyagent.repository.DatabaseManager;
import com.studyagent.repository.StudyRepository;
import com.studyagent.scheduler.StudyOptimizer;
import com.studyagent.ui.StudyDesktopUI;
import javax.swing.SwingUtilities;

/** Development entry point; the native EXE uses DesktopLauncher. */
public final class Main {
    private Main() {}
    public static void main(String[] args) {
        DatabaseManager.initializeDatabase();
        var ai = new GeminiService(ConfigLoader.getGeminiApiKey());
        var repository = new StudyRepository();
        var optimizer = new StudyOptimizer(ai);
        SwingUtilities.invokeLater(() -> new StudyDesktopUI(ai, repository, optimizer).showWindow());
    }
}
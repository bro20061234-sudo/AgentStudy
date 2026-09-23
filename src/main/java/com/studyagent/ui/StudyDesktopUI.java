package com.studyagent.ui;

import com.studyagent.ai.GeminiService;
import com.studyagent.model.StudySession;
import com.studyagent.model.SubjectSyllabus;
import com.studyagent.model.TimetableEntry;
import com.studyagent.repository.StudyRepository;
import com.studyagent.scheduler.StudyOptimizer;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.io.File;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class StudyDesktopUI extends JFrame {
    private static final long DESKTOP_CHAT_ID = 1L;

    private final GeminiService geminiService;
    private final StudyRepository repository;
    private final StudyOptimizer optimizer;
    private final JTextArea timetableInput = new JTextArea();
    private final JTextArea classScheduleOutput = createOutputArea();
    private final JTextArea studyScheduleOutput = createOutputArea();
    private final JTextArea syllabusOutput = createOutputArea();
    private final JLabel pomodoroModeLabel = new JLabel("TẬP TRUNG");
    private final JLabel pomodoroTimerLabel = new JLabel("25:00");
    private final JLabel pomodoroSessionLabel = new JLabel("Phiên 1 / 4");
    private final JButton pomodoroStartButton = new JButton("Bắt đầu");
    private final JButton pomodoroResetButton = new JButton("Đặt lại");
    private Timer pomodoroTimer;
    private int remainingSeconds = 25 * 60;
    private int pomodoroSession = 1;
    private boolean focusMode = true;
    private final JLabel statusLabel = new JLabel("Sẵn sàng nhận thời khóa biểu");
    private final JButton analyzeButton = new JButton("Phân tích thời khóa biểu");
    private final JButton imageButton = new JButton("Đọc ảnh TKB");

    public StudyDesktopUI(GeminiService geminiService, StudyRepository repository, StudyOptimizer optimizer) {
        super("Smart Study Agent");
        this.geminiService = geminiService;
        this.repository = repository;
        this.optimizer = optimizer;
        buildWindow();
    }

    public void showWindow() {
        setVisible(true);
        refreshSavedData();
    }

    private void buildWindow() {
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setMinimumSize(new Dimension(1000, 720));
        setSize(1180, 780);
        setLocationByPlatform(true);

        JPanel root = new JPanel(new BorderLayout(18, 18));
        root.setBorder(BorderFactory.createEmptyBorder(18, 22, 18, 22));
        root.setBackground(new Color(245, 247, 251));
        setContentPane(root);

        root.add(createHeader(), BorderLayout.NORTH);
        root.add(createMainContent(), BorderLayout.CENTER);
        root.add(createStatusBar(), BorderLayout.SOUTH);
        wireActions();
    }

    private JPanel createHeader() {
        JPanel header = new JPanel(new BorderLayout(8, 8));
        header.setOpaque(false);

        JLabel title = new JLabel("Smart Study Agent");
        title.setFont(new Font("Segoe UI", Font.BOLD, 30));
        title.setForeground(new Color(28, 43, 72));

        JLabel subtitle = new JLabel("Biến thời khóa biểu thành lịch tự học và đề cương ôn tập cho cả kỳ");
        subtitle.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        subtitle.setForeground(new Color(92, 105, 128));

        JPanel text = new JPanel(new GridLayout(2, 1));
        text.setOpaque(false);
        text.add(title);
        text.add(subtitle);
        header.add(text, BorderLayout.WEST);

        JLabel mode = new JLabel(geminiService.hasApiKey() ? "AI GEMINI" : "MOCK MODE");
        mode.setOpaque(true);
        mode.setBackground(geminiService.hasApiKey() ? new Color(216, 244, 229) : new Color(255, 238, 197));
        mode.setForeground(new Color(45, 75, 75));
        mode.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
        header.add(mode, BorderLayout.EAST);
        return header;
    }

    private JPanel createMainContent() {
        JPanel content = new JPanel(new BorderLayout(16, 16));
        content.setOpaque(false);
        content.add(createInputPanel(), BorderLayout.NORTH);

        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(new Font("Segoe UI", Font.BOLD, 14));
        tabs.addTab("Lịch trên lớp", createOutputPanel(classScheduleOutput));
        tabs.addTab("Lịch tự học", createOutputPanel(studyScheduleOutput));
        tabs.addTab("Đề cương ôn tập", createOutputPanel(syllabusOutput));
        tabs.addTab("Pomodoro", createPomodoroPanel());
        content.add(tabs, BorderLayout.CENTER);
        return content;
    }

    private JPanel createInputPanel() {
        JPanel panel = new JPanel(new BorderLayout(12, 12));
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(222, 227, 236)),
                BorderFactory.createEmptyBorder(14, 16, 14, 16)));

        JLabel label = new JLabel("Dán nội dung thời khóa biểu");
        label.setFont(new Font("Segoe UI", Font.BOLD, 16));
        label.setForeground(new Color(28, 43, 72));
        panel.add(label, BorderLayout.NORTH);

        timetableInput.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        timetableInput.setLineWrap(true);
        timetableInput.setWrapStyleWord(true);
        timetableInput.setRows(5);
        timetableInput.setText("Thứ 2: Sáng 7h-9h Lập trình Python (Phòng A2-301), 9h-12h Mạng máy tính (Phòng B1-204)\n" +
                "Thứ 3: Chiều 13h-16h Cơ sở dữ liệu (Phòng C3-102)\n" +
                "Thứ 5: Sáng 7h-11h Kiến trúc máy tính (Phòng A1-105)");
        panel.add(new JScrollPane(timetableInput), BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        JButton clearButton = new JButton("Xóa");
        clearButton.addActionListener(event -> timetableInput.setText(""));
        JButton sampleButton = new JButton("Dùng mẫu");
        sampleButton.addActionListener(event -> timetableInput.setText(
                "Thứ 2: Sáng 7h-9h Lập trình Python (Phòng A2-301), 9h-12h Mạng máy tính (Phòng B1-204)\n" +
                "Thứ 3: Chiều 13h-16h Cơ sở dữ liệu (Phòng C3-102)\n" +
                "Thứ 5: Sáng 7h-11h Kiến trúc máy tính (Phòng A1-105)"));
        actions.add(clearButton);
        actions.add(sampleButton);
        actions.add(imageButton);
        analyzeButton.setBackground(new Color(39, 93, 173));
        analyzeButton.setForeground(Color.WHITE);
        analyzeButton.setFocusPainted(false);
        actions.add(analyzeButton);
        panel.add(actions, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel createOutputPanel(JTextArea output) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        panel.add(new JScrollPane(output), BorderLayout.CENTER);
        return panel;
    }

    private JPanel createPomodoroPanel() {
        JPanel panel = new JPanel(new BorderLayout(12, 12));
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createEmptyBorder(28, 28, 28, 28));

        JLabel title = new JLabel("Tập trung học theo Pomodoro", JLabel.CENTER);
        title.setFont(new Font("Segoe UI", Font.BOLD, 22));
        title.setForeground(new Color(28, 43, 72));
        panel.add(title, BorderLayout.NORTH);

        JPanel center = new JPanel(new GridLayout(3, 1, 8, 8));
        center.setOpaque(false);
        pomodoroModeLabel.setHorizontalAlignment(JLabel.CENTER);
        pomodoroModeLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));
        pomodoroModeLabel.setForeground(new Color(39, 93, 173));
        pomodoroTimerLabel.setHorizontalAlignment(JLabel.CENTER);
        pomodoroTimerLabel.setFont(new Font("Consolas", Font.BOLD, 72));
        pomodoroTimerLabel.setForeground(new Color(28, 43, 72));
        pomodoroSessionLabel.setHorizontalAlignment(JLabel.CENTER);
        pomodoroSessionLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        center.add(pomodoroModeLabel);
        center.add(pomodoroTimerLabel);
        center.add(pomodoroSessionLabel);
        panel.add(center, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        actions.setOpaque(false);
        pomodoroStartButton.setBackground(new Color(39, 93, 173));
        pomodoroStartButton.setForeground(Color.WHITE);
        pomodoroStartButton.setFocusPainted(false);
        pomodoroResetButton.setFocusPainted(false);
        pomodoroStartButton.addActionListener(event -> togglePomodoro());
        pomodoroResetButton.addActionListener(event -> resetPomodoro());
        actions.add(pomodoroStartButton);
        actions.add(pomodoroResetButton);
        panel.add(actions, BorderLayout.SOUTH);

        pomodoroTimer = new Timer(1000, event -> tickPomodoro());
        return panel;
    }

    private JPanel createStatusBar() {
        JPanel status = new JPanel(new BorderLayout());
        status.setOpaque(false);
        statusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        statusLabel.setForeground(new Color(92, 105, 128));
        status.add(statusLabel, BorderLayout.WEST);
        return status;
    }

    private void wireActions() {
        analyzeButton.addActionListener(event -> analyzeText());
        imageButton.addActionListener(event -> chooseImage());
    }

    private void analyzeText() {
        String input = timetableInput.getText().trim();
        if (input.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Vui lòng nhập nội dung thời khóa biểu.", "Thiếu dữ liệu", JOptionPane.WARNING_MESSAGE);
            return;
        }
        runAnalysis(() -> geminiService.extractTimetableFromText(input));
    }

    private void chooseImage() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Chọn ảnh thời khóa biểu");
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File image = chooser.getSelectedFile();
        runAnalysis(() -> geminiService.extractTimetableFromImage(Files.readAllBytes(image.toPath()), detectMimeType(image)));
    }

    private void runAnalysis(EntryLoader loader) {
        setBusy(true, "Đang phân tích thời khóa biểu và tạo kế hoạch học tập...");
        new SwingWorker<AnalysisResult, Void>() {
            @Override
            protected AnalysisResult doInBackground() throws Exception {
                List<TimetableEntry> entries = loader.load();
                if (entries == null || entries.isEmpty()) {
                    throw new IllegalStateException("Không tìm thấy buổi học nào trong dữ liệu đầu vào.");
                }
                repository.saveTimetableEntries(DESKTOP_CHAT_ID, entries);
                List<StudySession> sessions = optimizer.generateOptimalSchedule(entries);
                repository.saveStudySessions(DESKTOP_CHAT_ID, sessions);

                Set<String> subjects = new HashSet<>();
                for (TimetableEntry entry : entries) {
                    if (entry.getSubjectName() != null && !entry.getSubjectName().isBlank()) {
                        subjects.add(entry.getSubjectName().trim());
                    }
                }
                for (String subject : subjects) {
                    SubjectSyllabus syllabus = geminiService.generateSyllabus(subject);
                    syllabus.setChatId(DESKTOP_CHAT_ID);
                    repository.saveSyllabus(syllabus);
                }
                return new AnalysisResult(entries, sessions, subjects.size());
            }

            @Override
            protected void done() {
                try {
                    AnalysisResult result = get();
                    render(result);
                    setBusy(false, String.format("Hoàn tất: %d buổi học, %d phiên tự học, %d đề cương.",
                            result.entries.size(), result.sessions.size(), result.subjectCount));
                } catch (Exception error) {
                    setBusy(false, "Phân tích thất bại");
                    JOptionPane.showMessageDialog(StudyDesktopUI.this, error.getMessage(), "Không thể phân tích", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void refreshSavedData() {
        List<TimetableEntry> entries = repository.getTimetableEntries(DESKTOP_CHAT_ID);
        List<StudySession> sessions = repository.getStudySessions(DESKTOP_CHAT_ID);
        List<SubjectSyllabus> syllabuses = repository.getAllSyllabuses(DESKTOP_CHAT_ID);
        if (!entries.isEmpty() || !sessions.isEmpty() || !syllabuses.isEmpty()) {
            render(new AnalysisResult(entries, sessions, syllabuses.size()));
            statusLabel.setText("Đã tải dữ liệu đã lưu từ SQLite");
        }
    }

    private void render(AnalysisResult result) {
        StringBuilder classes = new StringBuilder("LỊCH HỌC TRÊN LỚP\n\n");
        for (TimetableEntry entry : result.entries) {
            classes.append(String.format("%-10s  %s - %s  |  %s  |  %s%n", entry.getDayOfWeek(), entry.getStartTime(),
                    entry.getEndTime(), entry.getSubjectName(), entry.getRoom()));
        }
        classScheduleOutput.setText(classes.toString());

        StringBuilder studies = new StringBuilder("LỊCH TỰ HỌC ĐƯỢC GỢI Ý\n\n");
        for (StudySession session : result.sessions) {
            studies.append(String.format("%-10s  %s - %s  |  %s%n", session.getDayOfWeek(), session.getStartTime(),
                    session.getEndTime(), session.getSubjectName()));
            studies.append("   Trọng tâm: ").append(session.getTopicOverview()).append("\n");
            studies.append("   Chuẩn bị: ").append(session.getPreparationMaterials()).append("\n\n");
        }
        studyScheduleOutput.setText(studies.toString());

        StringBuilder syllabus = new StringBuilder("ĐỀ CƯƠNG ÔN TẬP\n\n");
        for (SubjectSyllabus item : repository.getAllSyllabuses(DESKTOP_CHAT_ID)) {
            syllabus.append("════════  ").append(item.getSubjectName()).append("  ════════\n");
            syllabus.append("Tổng quan: ").append(item.getOverview()).append("\n\n");
            syllabus.append("Nội dung:\n").append(item.getSyllabusContent()).append("\n\n");
            syllabus.append("Mẹo ôn thi: ").append(item.getExamTips()).append("\n");
            syllabus.append("Tài liệu: ").append(item.getRecommendedMaterials()).append("\n\n");
        }
        syllabusOutput.setText(syllabus.toString());
    }

    private void setBusy(boolean busy, String message) {
        analyzeButton.setEnabled(!busy);
        imageButton.setEnabled(!busy);
        statusLabel.setText(message);
    }

    private void togglePomodoro() {
        if (pomodoroTimer.isRunning()) {
            pomodoroTimer.stop();
            pomodoroStartButton.setText("Tiếp tục");
            statusLabel.setText("Pomodoro đang tạm dừng");
        } else {
            pomodoroTimer.start();
            pomodoroStartButton.setText("Tạm dừng");
            statusLabel.setText(focusMode ? "Đang tập trung học" : "Đang nghỉ");
        }
    }

    private void resetPomodoro() {
        pomodoroTimer.stop();
        remainingSeconds = 25 * 60;
        pomodoroSession = 1;
        focusMode = true;
        pomodoroStartButton.setText("Bắt đầu");
        updatePomodoroLabels();
        statusLabel.setText("Pomodoro đã được đặt lại");
    }

    private void tickPomodoro() {
        if (remainingSeconds > 0) {
            remainingSeconds--;
            updatePomodoroLabels();
            return;
        }

        Toolkit.getDefaultToolkit().beep();
        if (focusMode) {
            focusMode = false;
            remainingSeconds = pomodoroSession % 4 == 0 ? 15 * 60 : 5 * 60;
            statusLabel.setText("Đã hết phiên học, bắt đầu giờ nghỉ");
        } else {
            focusMode = true;
            pomodoroSession = pomodoroSession == 4 ? 1 : pomodoroSession + 1;
            remainingSeconds = 25 * 60;
            statusLabel.setText("Hết giờ nghỉ, bắt đầu phiên học mới");
        }
        updatePomodoroLabels();
    }

    private void updatePomodoroLabels() {
        int minutes = remainingSeconds / 60;
        int seconds = remainingSeconds % 60;
        pomodoroTimerLabel.setText(String.format("%02d:%02d", minutes, seconds));
        pomodoroModeLabel.setText(focusMode ? "TẬP TRUNG" : "NGHỈ GIẢI LAO");
        pomodoroSessionLabel.setText("Phiên " + pomodoroSession + " / 4");
    }

    private static JTextArea createOutputArea() {
        JTextArea area = new JTextArea();
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setFont(new Font("Consolas", Font.PLAIN, 14));
        area.setForeground(new Color(42, 52, 70));
        area.setBackground(Color.WHITE);
        area.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        return area;
    }

    private static String detectMimeType(File file) {
        String name = file.getName().toLowerCase();
        if (name.endsWith(".png")) return "image/png";
        if (name.endsWith(".webp")) return "image/webp";
        return "image/jpeg";
    }

    @FunctionalInterface
    private interface EntryLoader {
        List<TimetableEntry> load() throws Exception;
    }

    private record AnalysisResult(List<TimetableEntry> entries, List<StudySession> sessions, int subjectCount) {}
}

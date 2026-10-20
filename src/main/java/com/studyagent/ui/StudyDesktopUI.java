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
import java.text.Normalizer;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class StudyDesktopUI extends JFrame {
    private static final long DESKTOP_CHAT_ID = 1L;

    private final GeminiService geminiService;
    private final StudyRepository repository;
    private final StudyOptimizer optimizer;
    private final JTextArea timetableInput = new JTextArea();
    private final JTextArea syllabusOutput = createOutputArea();
    private final JTextArea weeklyCurriculumOutput = createOutputArea();
    private final JTextArea quizOutput = createOutputArea();
    private final JLabel pomodoroModeLabel = new JLabel("TẬP TRUNG");
    private final JLabel pomodoroTimerLabel = new JLabel("25:00");
    private final JLabel pomodoroSessionLabel = new JLabel("Phiên 1 / 4");
    private final JButton pomodoroStartButton = new JButton("Bắt đầu");
    private final JButton pomodoroResetButton = new JButton("Đặt lại");
    private Timer pomodoroTimer;
    private int remainingSeconds = 25 * 60;
    private int pomodoroSession = 1;
    private boolean focusMode = true;
    private final JLabel statusLabel = new JLabel("Sẵn sàng nhận thời khóa biểu & giáo trình");
    private final JButton analyzeButton = new JButton("Phân tích thời khóa biểu");
    private final JButton imageButton = new JButton("Đọc TKB từ Ảnh / PDF");
    private final JButton textbookButton = new JButton("Nạp giáo trình (PDF/Word)");
    private final JButton contestButton = new JButton("Bắt đầu Contest / Ôn tập");

    private final JTabbedPane mainTabs = new JTabbedPane();
    private final JTabbedPane scheduleTabs = new JTabbedPane();
    private final JLabel calendarRangeLabel = new JLabel("", JLabel.CENTER);
    private final JPanel classCalendarDays = createCalendarDaysPanel();
    private final JPanel studyCalendarDays = createCalendarDaysPanel();
    private LocalDate displayedWeekStart = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    private final JButton viewTextbookButton = new JButton("Xem giáo trình đã nạp");
    private final JButton deleteTextbookButton = new JButton("Xóa giáo trình cũ");
    private List<TimetableEntry> currentTimetableEntries = new ArrayList<>();
    private List<StudySession> currentSessions = new ArrayList<>();

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

        JLabel mode = new JLabel(geminiService.hasApiKey() ? "AI GEMINI" : "API KEY REQUIRED");
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

        mainTabs.setFont(new Font("Segoe UI", Font.BOLD, 14));
        mainTabs.addTab("Lịch", createScheduleCalendarPanel());
        mainTabs.addTab("Đề cương tổng quát", createOutputPanel(syllabusOutput));
        mainTabs.addTab("Tiến độ theo buổi (Giáo trình)", createOutputPanel(weeklyCurriculumOutput));
        mainTabs.addTab("Ôn tập & Contest", createOutputPanel(quizOutput));
        mainTabs.addTab("Pomodoro", createPomodoroPanel());
        content.add(mainTabs, BorderLayout.CENTER);
        return content;
    }

    private JPanel createScheduleCalendarPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JPanel navigation = new JPanel(new BorderLayout(12, 0));
        navigation.setOpaque(false);
        JButton previousWeekButton = new JButton("‹");
        JButton nextWeekButton = new JButton("›");
        for (JButton button : List.of(previousWeekButton, nextWeekButton)) {
            button.setFont(new Font("Segoe UI", Font.BOLD, 24));
            button.setFocusPainted(false);
        }
        calendarRangeLabel.setFont(new Font("Segoe UI", Font.BOLD, 19));
        calendarRangeLabel.setForeground(new Color(177, 34, 42));
        previousWeekButton.addActionListener(event -> {
            displayedWeekStart = displayedWeekStart.minusWeeks(1);
            renderWeeklyCalendar();
        });
        nextWeekButton.addActionListener(event -> {
            displayedWeekStart = displayedWeekStart.plusWeeks(1);
            renderWeeklyCalendar();
        });
        navigation.add(previousWeekButton, BorderLayout.WEST);
        navigation.add(calendarRangeLabel, BorderLayout.CENTER);
        navigation.add(nextWeekButton, BorderLayout.EAST);
        panel.add(navigation, BorderLayout.NORTH);

        scheduleTabs.setFont(new Font("Segoe UI", Font.BOLD, 14));
        scheduleTabs.addTab("Lịch trên lớp", new JScrollPane(classCalendarDays));
        scheduleTabs.addTab("Lịch học thêm", new JScrollPane(studyCalendarDays));
        panel.add(scheduleTabs, BorderLayout.CENTER);
        renderWeeklyCalendar();
        return panel;
    }

    private static JPanel createCalendarDaysPanel() {
        JPanel days = new JPanel();
        days.setLayout(new javax.swing.BoxLayout(days, javax.swing.BoxLayout.Y_AXIS));
        days.setBackground(new Color(248, 249, 251));
        days.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        return days;
    }

    private void renderWeeklyCalendar() {
        LocalDate weekEnd = displayedWeekStart.plusDays(6);
        calendarRangeLabel.setText(String.format("Tháng %02d/%d  |  %s - %s",
                displayedWeekStart.getMonthValue(), displayedWeekStart.getYear(),
                displayedWeekStart.format(DateTimeFormatter.ofPattern("dd/MM")),
                weekEnd.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))));

        classCalendarDays.removeAll();
        studyCalendarDays.removeAll();
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        for (int offset = 0; offset < 7; offset++) {
            LocalDate date = displayedWeekStart.plusDays(offset);
            classCalendarDays.add(createDaySection(date, dateFormatter, true));
            studyCalendarDays.add(createDaySection(date, dateFormatter, false));
        }
        classCalendarDays.add(javax.swing.Box.createVerticalGlue());
        studyCalendarDays.add(javax.swing.Box.createVerticalGlue());
        classCalendarDays.revalidate();
        classCalendarDays.repaint();
        studyCalendarDays.revalidate();
        studyCalendarDays.repaint();
    }

    private JPanel createDaySection(LocalDate date, DateTimeFormatter dateFormatter, boolean classSchedule) {
        JPanel section = new JPanel();
        section.setLayout(new javax.swing.BoxLayout(section, javax.swing.BoxLayout.Y_AXIS));
        section.setOpaque(false);
        section.setBorder(BorderFactory.createEmptyBorder(8, 4, 12, 4));

        JLabel heading = new JLabel(String.format("%s, %s", vietnameseDayName(date.getDayOfWeek()),
                date.format(dateFormatter)));
        heading.setFont(new Font("Segoe UI", Font.BOLD, 20));
        heading.setForeground(new Color(28, 34, 46));
        heading.setBorder(BorderFactory.createEmptyBorder(4, 2, 8, 2));
        heading.setAlignmentX(LEFT_ALIGNMENT);
        section.add(heading);

        int eventCount = 0;
        if (classSchedule) {
            List<TimetableEntry> entries = currentTimetableEntries.stream()
                    .filter(entry -> dayOfWeekMatches(entry.getDayOfWeek(), date.getDayOfWeek())
                            && StudyOptimizer.isEntryActiveOnDate(entry, date))
                    .sorted(java.util.Comparator.comparing(TimetableEntry::getStartTime,
                            java.util.Comparator.nullsLast(String::compareTo)))
                    .toList();
            for (TimetableEntry entry : entries) {
                String details = entry.getRoom() == null || entry.getRoom().isBlank()
                        ? entry.getNote() : entry.getRoom();
                if (entry.getNote() != null && !entry.getNote().isBlank()
                        && (details == null || !details.contains(entry.getNote()))) {
                    details = details == null || details.isBlank()
                            ? entry.getNote() : details + " | " + entry.getNote();
                }
                section.add(createCalendarEventCard(entry.getSubjectName(),
                        entry.getStartTime() + " - " + entry.getEndTime(), details, false, null));
                eventCount++;
            }
        } else {
            List<StudySession> sessions = currentSessions.stream()
                    .filter(session -> dayOfWeekMatches(session.getDayOfWeek(), date.getDayOfWeek())
                            && (session.getScheduledDate() == null
                            || session.getScheduledDate().equals(date.toString())))
                    .sorted(java.util.Comparator.comparing(StudySession::getStartTime,
                            java.util.Comparator.nullsLast(String::compareTo)))
                    .toList();
            for (StudySession session : sessions) {
                section.add(createCalendarEventCard(session.getSubjectName(),
                        session.getStartTime() + " - " + session.getEndTime(),
                        session.getTopicOverview(), true, session));
                eventCount++;
            }
        }
        if (eventCount == 0) {
            JLabel empty = new JLabel("Không có lịch trong ngày này");
            empty.setFont(new Font("Segoe UI", Font.ITALIC, 13));
            empty.setForeground(new Color(130, 138, 150));
            empty.setBorder(BorderFactory.createEmptyBorder(4, 12, 8, 12));
            empty.setAlignmentX(LEFT_ALIGNMENT);
            section.add(empty);
        }
        section.setAlignmentX(LEFT_ALIGNMENT);
        return section;
    }

    private JPanel createCalendarEventCard(String title, String time, String details,
                                           boolean studySession, StudySession session) {
        Color accent = studySession ? new Color(28, 166, 164) : new Color(34, 91, 211);
        Color background = studySession ? new Color(232, 246, 247) : new Color(230, 247, 251);
        JPanel card = new JPanel(new BorderLayout(10, 0));
        card.setBackground(background);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(222, 233, 240), 1, true),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, studySession ? 170 : 110));
        card.setAlignmentX(LEFT_ALIGNMENT);

        JPanel marker = new JPanel();
        marker.setBackground(accent);
        marker.setPreferredSize(new Dimension(5, 40));
        card.add(marker, BorderLayout.WEST);

        JPanel content = new JPanel();
        content.setLayout(new javax.swing.BoxLayout(content, javax.swing.BoxLayout.Y_AXIS));
        content.setOpaque(false);
        JLabel titleLabel = new JLabel(title == null ? "Buổi học" : title);
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));
        titleLabel.setForeground(accent);
        titleLabel.setAlignmentX(LEFT_ALIGNMENT);
        content.add(titleLabel);

        JLabel timeLabel = new JLabel(time);
        timeLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        timeLabel.setBorder(BorderFactory.createEmptyBorder(3, 0, 2, 0));
        timeLabel.setAlignmentX(LEFT_ALIGNMENT);
        content.add(timeLabel);

        if (details != null && !details.isBlank()) {
            JLabel detailsLabel = new JLabel(details);
            detailsLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));
            detailsLabel.setForeground(new Color(55, 65, 81));
            detailsLabel.setAlignmentX(LEFT_ALIGNMENT);
            content.add(detailsLabel);
        }
        card.add(content, BorderLayout.CENTER);

        if (studySession && session != null) {
            JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
            actions.setOpaque(false);
            JButton statusButton = new JButton(session.isCompleted() ? "Đã hoàn thành" : "Chưa hoàn thành");
            statusButton.addActionListener(event -> toggleSessionStatus(session));
            JButton startButton = new JButton("Vào học trước");
            startButton.addActionListener(event -> startStudyEarly(session));
            JButton outlineButton = new JButton("Xem đề cương");
            outlineButton.addActionListener(event -> showSessionOutlineDialog(session));
            JButton editButton = new JButton("Chỉnh lịch");
            editButton.addActionListener(event -> editStudySession(session));
            actions.add(statusButton);
            actions.add(startButton);
            actions.add(outlineButton);
            actions.add(editButton);
            card.add(actions, BorderLayout.SOUTH);
        }
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 5, 0, 0, accent),
                BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(new Color(222, 233, 240), 1, true),
                        BorderFactory.createEmptyBorder(9, 10, 9, 10))));
        return card;
    }

    private static String vietnameseDayName(DayOfWeek day) {
        return switch (day) {
            case MONDAY -> "Thứ hai";
            case TUESDAY -> "Thứ ba";
            case WEDNESDAY -> "Thứ tư";
            case THURSDAY -> "Thứ năm";
            case FRIDAY -> "Thứ sáu";
            case SATURDAY -> "Thứ bảy";
            case SUNDAY -> "Chủ nhật";
        };
    }

    private static boolean dayOfWeekMatches(String dayName, DayOfWeek day) {
        if (dayName == null || dayName.isBlank()) return false;
        String normalized = Normalizer.normalize(dayName, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .trim();
        return switch (day) {
            case MONDAY -> normalized.equals("thu 2") || normalized.equals("thu hai");
            case TUESDAY -> normalized.equals("thu 3") || normalized.equals("thu ba");
            case WEDNESDAY -> normalized.equals("thu 4") || normalized.equals("thu tu");
            case THURSDAY -> normalized.equals("thu 5") || normalized.equals("thu nam");
            case FRIDAY -> normalized.equals("thu 6") || normalized.equals("thu sau");
            case SATURDAY -> normalized.equals("thu 7") || normalized.equals("thu bay");
            case SUNDAY -> normalized.equals("chu nhat") || normalized.equals("cn");
        };
    }

    private JPanel createInputPanel() {
        JPanel panel = new JPanel(new BorderLayout(12, 12));
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(222, 227, 236)),
                BorderFactory.createEmptyBorder(14, 16, 14, 16)));

        JLabel label = new JLabel("Dán nội dung thời khóa biểu hoặc nạp ảnh / giáo trình");
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

        JPanel actions = new JPanel(new GridLayout(2, 4, 8, 6));
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
        actions.add(textbookButton);
        actions.add(viewTextbookButton);
        actions.add(deleteTextbookButton);
        actions.add(contestButton);
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
        imageButton.addActionListener(event -> chooseImages());
        textbookButton.addActionListener(event -> chooseTextbook());
        viewTextbookButton.addActionListener(event -> viewTextbooks());
        deleteTextbookButton.addActionListener(event -> deleteTextbook());
        contestButton.addActionListener(event -> startDesktopContest());
    }

    private void analyzeText() {
        String input = timetableInput.getText().trim();
        if (input.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Vui lòng nhập nội dung thời khóa biểu.", "Thiếu dữ liệu", JOptionPane.WARNING_MESSAGE);
            return;
        }
        runAnalysis(() -> geminiService.extractTimetableFromText(input));
    }

    private void chooseImages() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Chọn file PDF hoặc ảnh thời khóa biểu (Giữ Ctrl để chọn nhiều file)");
        chooser.setMultiSelectionEnabled(true);
        javax.swing.filechooser.FileNameExtensionFilter filter = new javax.swing.filechooser.FileNameExtensionFilter(
                "Ảnh hoặc PDF Thời khóa biểu (*.pdf, *.png, *.jpg, *.jpeg)", "pdf", "png", "jpg", "jpeg");
        chooser.setFileFilter(filter);

        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File[] selectedFiles = chooser.getSelectedFiles();
        if (selectedFiles == null || selectedFiles.length == 0) {
            File single = chooser.getSelectedFile();
            if (single != null) selectedFiles = new File[]{single};
            else return;
        }

        final File[] filesToProcess = selectedFiles;
        runAnalysis(() -> {
            List<TimetableEntry> allEntries = new ArrayList<>();
            List<byte[]> imageBytesList = new ArrayList<>();

            for (File file : filesToProcess) {
                String fname = file.getName().toLowerCase();
                byte[] bytes = Files.readAllBytes(file.toPath());
                if (fname.endsWith(".pdf")) {
                    List<TimetableEntry> pdfEntries = geminiService.extractTimetableFromPdf(bytes);
                    if (pdfEntries != null) allEntries.addAll(pdfEntries);
                } else {
                    imageBytesList.add(bytes);
                }
            }

            if (!imageBytesList.isEmpty()) {
                List<TimetableEntry> imgEntries = geminiService.extractTimetableFromMultipleImages(imageBytesList, "image/jpeg");
                if (imgEntries != null) allEntries.addAll(imgEntries);
            }

            return allEntries;
        });
    }

    private void chooseTextbook() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Chọn file giáo trình môn học (.pdf hoặc .docx)");
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File file = chooser.getSelectedFile();
        String text = com.studyagent.parser.DocumentParser.extractText(file);
        if (text == null || text.isBlank()) {
            JOptionPane.showMessageDialog(this, "Không thể đọc nội dung file giáo trình.", "Lỗi đọc file", JOptionPane.ERROR_MESSAGE);
            return;
        }

        List<String> distinctSubjects = repository.getDistinctSubjects(DESKTOP_CHAT_ID);
        if (distinctSubjects.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Chưa có môn học trong Thời khóa biểu. Hãy phân tích TKB trước khi nạp giáo trình.",
                    "Chưa có môn học", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String selectedSubject = (String) JOptionPane.showInputDialog(this,
                "Chọn môn học từ Thời khóa biểu:", "Gán giáo trình cho môn học",
                JOptionPane.QUESTION_MESSAGE, null, distinctSubjects.toArray(new String[0]), distinctSubjects.get(0));
        if (selectedSubject == null || selectedSubject.isBlank()) return;
        final String subject = selectedSubject;

        List<StudySession> allSessions = repository.getStudySessions(DESKTOP_CHAT_ID);
        int sessionCount = (int) allSessions.stream()
                .filter(s -> s.getSubjectName() != null && s.getSubjectName().equalsIgnoreCase(subject))
                .count();

        int targetSessionCount = sessionCount;
        if (targetSessionCount <= 0) {
            long classCount = repository.getTimetableEntries(DESKTOP_CHAT_ID).stream()
                    .filter(t -> t.getSubjectName() != null && t.getSubjectName().equalsIgnoreCase(subject))
                    .count();
            String defaultCountStr = String.valueOf(classCount > 0 ? Math.max(10, (int) classCount) : 12);
            String input = (String) JOptionPane.showInputDialog(this,
                    String.format("Môn \"%s\" chưa có lịch học thêm được tạo sẵn.\nBạn muốn AI phân tích giáo trình và chia đều vào bao nhiêu buổi học?", subject),
                    "Phân bổ số buổi học", JOptionPane.QUESTION_MESSAGE, null, null, defaultCountStr);
            if (input == null || input.isBlank()) return;
            try {
                targetSessionCount = Integer.parseInt(input.trim());
                if (targetSessionCount <= 0) targetSessionCount = 12;
            } catch (Exception ex) {
                targetSessionCount = 12;
            }
        }
        final int finalSessionCount = targetSessionCount;

        setBusy(true, String.format("Đang phân tích giáo trình và soạn bài giảng chi tiết cho %d buổi môn %s...", finalSessionCount, subject));

        new SwingWorker<List<com.studyagent.model.WeeklyCurriculum>, Void>() {
            @Override
            protected List<com.studyagent.model.WeeklyCurriculum> doInBackground() {
                if (!repository.saveTextbook(DESKTOP_CHAT_ID, subject, file.getName(), text)) {
                    throw new IllegalStateException("Không thể lưu giáo trình vào cơ sở dữ liệu.");
                }

                List<com.studyagent.model.WeeklyCurriculum> plans =
                        geminiService.generateCurriculumForSessions(subject, text, finalSessionCount);
                if (!repository.saveWeeklyCurricula(DESKTOP_CHAT_ID, subject, plans)) {
                    throw new IllegalStateException("Đã tạo bài giảng nhưng không thể cập nhật đề cương vào cơ sở dữ liệu.");
                }

                // Cập nhật cả đề cương tổng quát (Syllabus)
                try {
                    SubjectSyllabus syllabus = new SubjectSyllabus();
                    syllabus.setChatId(DESKTOP_CHAT_ID);
                    syllabus.setSubjectName(subject);
                    syllabus.setOverview("Đề cương môn học " + subject + " được bóc tách và phân bổ từ giáo trình: " + file.getName());
                    StringBuilder sb = new StringBuilder();
                    for (com.studyagent.model.WeeklyCurriculum p : plans) {
                        sb.append("• Buổi ").append(p.getWeekNumber()).append(": ").append(p.getChapterTitle()).append("\n");
                    }
                    syllabus.setSyllabusContent(sb.toString());
                    syllabus.setExamTips("Ôn tập kỹ các ví dụ minh họa và bài tập thực hành theo từng buổi học trong giáo trình.");
                    syllabus.setRecommendedMaterials("Giáo trình chính: " + file.getName());
                    repository.saveSyllabus(syllabus);
                } catch (Exception ignored) {}

                // Cập nhật trọng tâm cho các buổi tự học tương ứng nếu có
                try {
                    List<StudySession> sessions = repository.getStudySessions(DESKTOP_CHAT_ID);
                    int pIdx = 0;
                    for (StudySession s : sessions) {
                        if (s.getSubjectName() != null && s.getSubjectName().equalsIgnoreCase(subject)) {
                            if (pIdx < plans.size()) {
                                com.studyagent.model.WeeklyCurriculum p = plans.get(pIdx++);
                                s.setTopicOverview(p.getChapterTitle());
                                s.setPreparationMaterials("Đọc bài giảng buổi " + p.getWeekNumber() + " từ giáo trình " + file.getName());
                                repository.updateStudySession(s);
                            }
                        }
                    }
                } catch (Exception ignored) {}

                return plans;
            }

            @Override
            protected void done() {
                try {
                    List<com.studyagent.model.WeeklyCurriculum> plans = get();
                    setBusy(false, "Đã nạp thành công giáo trình môn " + subject);
                    renderWeeklyCurriculum();
                    renderSyllabuses();
                    currentSessions = repository.getStudySessions(DESKTOP_CHAT_ID);
                    renderWeeklyCalendar();
                    mainTabs.setSelectedIndex(2); // Chuyển sang tab Tiến độ theo buổi (Giáo trình)
                    JOptionPane.showMessageDialog(StudyDesktopUI.this,
                            String.format("Đã nạp giáo trình và chia đều thành công vào %d buổi học cho môn %s!", plans.size(), subject),
                            "Thành công", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception e) {
                    setBusy(false, "Lỗi phân tích giáo trình");
                    renderWeeklyCurriculum();
                    Throwable cause = e.getCause() == null ? e : e.getCause();
                    JOptionPane.showMessageDialog(StudyDesktopUI.this, cause.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void viewTextbooks() {
        List<StudyRepository.TextbookItem> textbooks = repository.getAllTextbooks(DESKTOP_CHAT_ID);
        if (textbooks.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Chưa có giáo trình nào được nạp vào hệ thống.", "Thông báo", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        String[] subjectNames = textbooks.stream().map(StudyRepository.TextbookItem::getSubjectName).toArray(String[]::new);
        String picked = (String) JOptionPane.showInputDialog(this,
                "Chọn môn học để xem lại giáo trình:", "Xem giáo trình đã nạp",
                JOptionPane.QUESTION_MESSAGE, null, subjectNames, subjectNames[0]);
        if (picked == null) return;

        StudyRepository.TextbookItem item = repository.getTextbook(DESKTOP_CHAT_ID, picked);
        if (item == null) return;

        JFrame viewerFrame = new JFrame("Giáo trình môn: " + item.getSubjectName() + " (" + item.getFileName() + ")");
        viewerFrame.setSize(800, 600);
        viewerFrame.setLocationRelativeTo(this);

        JTextArea contentArea = new JTextArea();
        contentArea.setFont(new Font("Consolas", Font.PLAIN, 13));
        contentArea.setLineWrap(true);
        contentArea.setWrapStyleWord(true);
        contentArea.setEditable(false);
        contentArea.setText(String.format("=== TÀI LIỆU GIÁO TRÌNH: %s ===\nFile gốc: %s | Ngày nạp: %s\n\n%s",
                item.getSubjectName().toUpperCase(), item.getFileName(), item.getCreatedAt(), item.getContent()));
        contentArea.setCaretPosition(0);

        viewerFrame.setContentPane(new JScrollPane(contentArea));
        viewerFrame.setVisible(true);
    }

    private void deleteTextbook() {
        List<StudyRepository.TextbookItem> textbooks = repository.getAllTextbooks(DESKTOP_CHAT_ID);
        if (textbooks.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Chưa có giáo trình nào để xóa.", "Thông báo", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        String[] subjectNames = textbooks.stream().map(StudyRepository.TextbookItem::getSubjectName).toArray(String[]::new);
        String picked = (String) JOptionPane.showInputDialog(this,
                "Chọn môn học cần xóa giáo trình:", "Xóa giáo trình cũ",
                JOptionPane.WARNING_MESSAGE, null, subjectNames, subjectNames[0]);
        if (picked == null) return;

        int confirmation = JOptionPane.showConfirmDialog(this,
                "Xóa giáo trình và đề cương các buổi đã tạo cho môn " + picked + "?",
                "Xác nhận xóa", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirmation != JOptionPane.YES_OPTION) return;

        if (!repository.deleteTextbook(DESKTOP_CHAT_ID, picked)) {
            JOptionPane.showMessageDialog(this, "Không thể xóa giáo trình. Vui lòng thử lại.",
                    "Lỗi xóa giáo trình", JOptionPane.ERROR_MESSAGE);
            return;
        }
        renderWeeklyCurriculum();
        statusLabel.setText("Đã xóa giáo trình và đề cương môn " + picked);
        JOptionPane.showMessageDialog(this, "Đã xóa giáo trình và đề cương môn " + picked + ".",
                "Đã xóa", JOptionPane.INFORMATION_MESSAGE);
    }

    private void startStudyEarly(StudySession session) {
        // Tính thời lượng của buổi học thêm (phút)
        int durationMinutes = 25; // mặc định
        try {
            int startMin = parseTimeToMinutes(session.getStartTime());
            int endMin = parseTimeToMinutes(session.getEndTime());
            if (endMin > startMin) {
                durationMinutes = Math.min(120, endMin - startMin);
            }
        } catch (Exception ignored) {}

        // Kích hoạt đồng hồ Pomodoro
        remainingSeconds = durationMinutes * 60;
        focusMode = true;
        pomodoroSession = 1;
        pomodoroModeLabel.setText("ĐANG HỌC TRƯỚC: " + session.getSubjectName().toUpperCase());
        updatePomodoroLabels();

        // Chuyển sang tab Pomodoro
        mainTabs.setSelectedIndex(mainTabs.indexOfTab("Pomodoro"));
        if (!pomodoroTimer.isRunning()) {
            pomodoroTimer.start();
            pomodoroStartButton.setText("Tạm dừng");
        }
        statusLabel.setText("Đang vào học trước môn " + session.getSubjectName() + " (" + durationMinutes + " phút)");

        // Mở thông tin đề cương buổi học
        showSessionOutlineDialog(session);
    }

    private int parseTimeToMinutes(String timeStr) {
        if (timeStr == null) return 0;
        String[] parts = timeStr.trim().split("[:hH]");
        if (parts.length >= 2) {
            return Integer.parseInt(parts[0].trim()) * 60 + Integer.parseInt(parts[1].trim());
        } else if (parts.length == 1) {
            return Integer.parseInt(parts[0].trim()) * 60;
        }
        return 0;
    }

    private void showSessionOutlineDialog(StudySession session) {
        String subject = session.getSubjectName();
        int sessionIndex = session.getSessionIndex();

        List<com.studyagent.model.WeeklyCurriculum> curricula = repository.getWeeklyCurricula(DESKTOP_CHAT_ID, subject);
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("BÀI GIẢNG TỰ HỌC: %s (Buổi %d)\n", subject.toUpperCase(), sessionIndex));
        sb.append(String.format("Lịch: %s %s - %s\n\n", session.getDayOfWeek(), session.getStartTime(), session.getEndTime()));

        if (curricula == null || curricula.isEmpty()) {
            sb.append("Môn này chưa có giáo trình được phân tích thành bài giảng.\n\n");
            sb.append("Trọng tâm dự kiến: ").append(session.getTopicOverview()).append("\n");
            sb.append("Tài liệu cần chuẩn bị: ").append(session.getPreparationMaterials()).append("\n\n");
            sb.append("Hãy nạp giáo trình để tạo bài giảng chi tiết theo từng buổi.");
        } else {
            // Lấy chương phân bổ cho buổi thứ sessionIndex
            int targetWeek = Math.min(sessionIndex, curricula.size());
            com.studyagent.model.WeeklyCurriculum curr = null;
            for (com.studyagent.model.WeeklyCurriculum c : curricula) {
                if (c.getWeekNumber() == targetWeek) { curr = c; break; }
            }
            if (curr == null) curr = curricula.get(curricula.size() - 1);

            String lessonIssue = curr.getDetailedLessonIssue();
            if (lessonIssue != null) {
                sb.append("LƯU Ý: ").append(lessonIssue).append("\n\n");
            }
            sb.append("NỘI DUNG TỪ GIÁO TRÌNH:\n");
            sb.append(String.format("Chương / Phần: %s\n\n", curr.getChapterTitle()));
            sb.append(curr.getDetailedSyllabus()).append("\n\n");
            if (session.getTopicOverview() != null && !session.getTopicOverview().isBlank()) {
                sb.append("Gợi ý trọng tâm theo lịch: ").append(session.getTopicOverview()).append("\n");
            }
        }

        JTextArea area = new JTextArea(sb.toString());
        area.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);

        JScrollPane sp = new JScrollPane(area);
        sp.setPreferredSize(new Dimension(760, 560));
        JOptionPane.showMessageDialog(this, sp, "Bài giảng buổi học - " + subject, JOptionPane.INFORMATION_MESSAGE);
    }

    private void editStudySession(StudySession session) {
        JPanel editPanel = new JPanel(new GridLayout(4, 2, 8, 8));
        String[] days = new String[]{"Thứ 2", "Thứ 3", "Thứ 4", "Thứ 5", "Thứ 6", "Thứ 7", "Chủ nhật"};
        javax.swing.JComboBox<String> dayCombo = new javax.swing.JComboBox<>(days);
        dayCombo.setSelectedItem(session.getDayOfWeek());

        javax.swing.JTextField startField = new javax.swing.JTextField(session.getStartTime());
        javax.swing.JTextField endField = new javax.swing.JTextField(session.getEndTime());
        javax.swing.JTextField topicField = new javax.swing.JTextField(session.getTopicOverview());

        editPanel.add(new JLabel("Thứ trong tuần:"));
        editPanel.add(dayCombo);
        editPanel.add(new JLabel("Giờ bắt đầu (VD: 19:30):"));
        editPanel.add(startField);
        editPanel.add(new JLabel("Giờ kết thúc (VD: 21:00):"));
        editPanel.add(endField);
        editPanel.add(new JLabel("Trọng tâm buổi học:"));
        editPanel.add(topicField);

        int res = JOptionPane.showConfirmDialog(this, editPanel, "Chỉnh sửa lịch học thêm: " + session.getSubjectName(), JOptionPane.OK_CANCEL_OPTION);
        if (res == JOptionPane.OK_OPTION) {
            String startTime = startField.getText().trim();
            String endTime = endField.getText().trim();
            if (!startTime.matches("(?:[01]?\\d|2[0-3]):[0-5]\\d")
                    || !endTime.matches("(?:[01]?\\d|2[0-3]):[0-5]\\d")
                    || parseTimeToMinutes(endTime) <= parseTimeToMinutes(startTime)) {
                JOptionPane.showMessageDialog(this, "Giờ không hợp lệ. Hãy nhập giờ dạng HH:mm và đảm bảo giờ kết thúc sau giờ bắt đầu.",
                        "Lịch học không hợp lệ", JOptionPane.ERROR_MESSAGE);
                return;
            }
            String updatedDay = (String) dayCombo.getSelectedItem();
            if (session.getScheduledDate() != null && !session.getScheduledDate().isBlank()) {
                LocalDate originalDate = LocalDate.parse(session.getScheduledDate());
                int selectedDayIndex = java.util.Arrays.asList(days).indexOf(updatedDay);
                session.setScheduledDate(originalDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                        .plusDays(selectedDayIndex).toString());
            }
            session.setDayOfWeek(updatedDay);
            session.setStartTime(startTime);
            session.setEndTime(endTime);
            session.setTopicOverview(topicField.getText().trim());

            repository.updateStudySession(session);
            renderWeeklyCalendar();
            JOptionPane.showMessageDialog(this, "Đã cập nhật lịch học thêm thành công!", "Thành công", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void toggleSessionStatus(StudySession session) {
        boolean newStatus = !session.isCompleted();
        repository.toggleStudySessionCompleted(session.getId(), newStatus);
        session.setCompleted(newStatus);
        renderWeeklyCalendar();
    }

    private void startDesktopContest() {
        List<String> distinctSubjects = repository.getDistinctSubjects(DESKTOP_CHAT_ID);
        List<String> contestSubjects = repository.getAllContestSubjects(DESKTOP_CHAT_ID);
        for (String cs : contestSubjects) {
            if (!distinctSubjects.contains(cs)) distinctSubjects.add(cs);
        }
        if (distinctSubjects.isEmpty()) distinctSubjects.add("Cơ sở dữ liệu");

        String[] options = {"Xem lại Contest đã tạo", "Tạo Contest mới bằng AI"};
        int choice = JOptionPane.showOptionDialog(this,
                "Chọn chức năng Contest:", "Quản lý & Ôn tập Contest",
                JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[0]);

        if (choice == 0) {
            // Xem lại các contest đã tạo
            Map<String, Map<Integer, List<com.studyagent.model.QuizQuestion>>> savedContests =
                    repository.getAllContestsGroupedBySubject(DESKTOP_CHAT_ID);
            if (savedContests.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Chưa có Contest nào được lưu trong hệ thống. Hãy tạo Contest mới trước!", "Chưa có dữ liệu", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            contestSubjects = new ArrayList<>(savedContests.keySet());
            String pickedSub = (String) JOptionPane.showInputDialog(this, "Chọn môn học để xem lại Contest:", "Xem lại Contest",
                    JOptionPane.QUESTION_MESSAGE, null, contestSubjects.toArray(new String[0]), contestSubjects.get(0));
            if (pickedSub == null) return;

            Map<Integer, List<com.studyagent.model.QuizQuestion>> subjectContests = savedContests.get(pickedSub);
            List<Integer> contestNumbers = new ArrayList<>(subjectContests.keySet());
            String[] contestLabels = contestNumbers.stream().map(number -> "Contest #" + number).toArray(String[]::new);
            int selectedIndex = JOptionPane.showOptionDialog(this, "Chọn Contest muốn xem hoặc làm thử:",
                    "Contest môn " + pickedSub, JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE,
                    null, contestLabels, contestLabels[0]);
            if (selectedIndex < 0) return;
            List<com.studyagent.model.QuizQuestion> questions = subjectContests.get(contestNumbers.get(selectedIndex));
            String[] actions = {"Xem đáp án & lời giải", "Làm bài thi thử"};
            int action = JOptionPane.showOptionDialog(this, "Chọn thao tác:", "Contest môn " + pickedSub,
                    JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, actions, actions[0]);
            if (action == 0) {
                showContestReview(pickedSub, questions);
            } else if (action == 1) {
                takeContest(pickedSub, questions);
            }
            return;
        }

        if (choice != 1) return;

        javax.swing.JComboBox<String> subCombo = new javax.swing.JComboBox<>(distinctSubjects.toArray(new String[0]));
        subCombo.setEditable(true);
        int opt = JOptionPane.showConfirmDialog(this, subCombo, "Chọn hoặc nhập môn học muốn làm Contest:", JOptionPane.OK_CANCEL_OPTION);
        if (opt != JOptionPane.OK_OPTION) return;

        String subject = subCombo.getSelectedItem() != null ? subCombo.getSelectedItem().toString().trim() : "";
        if (subject.isBlank()) return;

        setBusy(true, "AI đang tạo bộ câu hỏi trắc nghiệm & contest cho môn " + subject + "...");
        new SwingWorker<Void, Void>() {
            private List<com.studyagent.model.QuizQuestion> startQuiz;
            private List<com.studyagent.model.QuizQuestion> contestQuiz;

            @Override
            protected Void doInBackground() {
                startQuiz = geminiService.generateQuizQuestions(subject, 1, "START_SESSION", "Ôn tập kiến thức tuần trước");
                contestQuiz = geminiService.generateQuizQuestions(subject, 1, "CONTEST", "Bài thi trắc nghiệm tổng hợp");
                repository.saveQuizQuestions(DESKTOP_CHAT_ID, subject, "START_SESSION", 1, startQuiz);
                repository.saveQuizQuestions(DESKTOP_CHAT_ID, subject, "CONTEST", 1, contestQuiz);
                return null;
            }

            @Override
            protected void done() {
                setBusy(false, "Sẵn sàng làm bài trắc nghiệm");
                renderQuizQuestions(subject, startQuiz, contestQuiz);
                mainTabs.setSelectedIndex(4);
            }
        }.execute();
    }

    private void showContestReview(String subject, List<com.studyagent.model.QuizQuestion> questions) {
        JTextArea area = new JTextArea();
        area.setFont(new Font("Consolas", Font.PLAIN, 13));
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setEditable(false);
        StringBuilder content = new StringBuilder("CONTEST MÔN: ").append(subject).append("\n\n");
        for (int i = 0; i < questions.size(); i++) {
            com.studyagent.model.QuizQuestion question = questions.get(i);
            content.append(String.format("Câu %d: %s%n", i + 1, question.getQuestion()));
            if (question.getOptions() != null) {
                for (String option : question.getOptions()) content.append("  ").append(option).append('\n');
            }
            content.append("Đáp án: ").append(question.getCorrectAnswer())
                    .append("\nGiải thích: ").append(question.getExplanation()).append("\n\n");
        }
        area.setText(content.toString());
        area.setCaretPosition(0);
        JScrollPane scroll = new JScrollPane(area);
        scroll.setPreferredSize(new Dimension(650, 450));
        JOptionPane.showMessageDialog(this, scroll, "Ôn lại Contest - " + subject, JOptionPane.INFORMATION_MESSAGE);
    }

    private void takeContest(String subject, List<com.studyagent.model.QuizQuestion> questions) {
        int correct = 0;
        int answered = 0;
        for (int i = 0; i < questions.size(); i++) {
            com.studyagent.model.QuizQuestion question = questions.get(i);
            List<String> options = question.getOptions();
            if (options == null || options.isEmpty()) continue;
            String[] choices = options.toArray(new String[0]);
            int selected = JOptionPane.showOptionDialog(this,
                    String.format("Câu %d/%d: %s", i + 1, questions.size(), question.getQuestion()),
                    "Thi thử Contest - " + subject, JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE,
                    null, choices, choices[0]);
            if (selected < 0) break;
            answered++;
            String selectedAnswer = answerLetter(choices[selected]);
            if (selectedAnswer.equals(answerLetter(question.getCorrectAnswer()))) correct++;
        }
        int score = answered == 0 ? 0 : Math.round(correct * 100f / answered);
        JOptionPane.showMessageDialog(this,
                String.format("Kết quả Contest môn %s: %d/%d câu đúng (%d/100 điểm).%nĐã trả lời: %d/%d câu.",
                        subject, correct, questions.size(), score, answered, questions.size()),
                "Kết quả thi thử", JOptionPane.INFORMATION_MESSAGE);
    }

    private static String answerLetter(String answer) {
        return answer == null || answer.isBlank() ? "" : answer.trim().substring(0, 1).toUpperCase(java.util.Locale.ROOT);
    }

    private void runAnalysis(EntryLoader loader) {
        setBusy(true, "Đang phân tích thời khóa biểu và tạo kế hoạch học tập...");
        new SwingWorker<AnalysisResult, Void>() {
            @Override
            protected AnalysisResult doInBackground() throws Exception {
                List<TimetableEntry> entries = loader.load();
                if (entries == null || entries.isEmpty()) {
                    if (!geminiService.hasApiKey()) {
                        throw new IllegalStateException("Chưa cấu hình Gemini API key. Hãy thêm key vào config.properties (gemini.api.key) hoặc biến môi trường GEMINI_API_KEY rồi thử lại.");
                    }
                    throw new IllegalStateException("Không tìm thấy buổi học nào trong dữ liệu đầu vào.");
                }
                repository.saveTimetableEntries(DESKTOP_CHAT_ID, entries);
                List<StudySession> sessions = optimizer.generateSemesterSchedule(entries);
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
                    setBusy(false, String.format("Hoàn tất: %d buổi trên lớp, %d phiên tự học trong kỳ, %d đề cương.",
                            result.entries.size(), result.sessions.size(), result.subjectCount));
                } catch (Exception error) {
                    setBusy(false, "Phân tích thất bại");
                    Throwable cause = error.getCause() == null ? error : error.getCause();
                    JOptionPane.showMessageDialog(StudyDesktopUI.this, cause.getMessage(), "Không thể phân tích", JOptionPane.ERROR_MESSAGE);
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
        renderWeeklyCurriculum();
    }

    private void renderWeeklyCurriculum() {
        List<com.studyagent.model.WeeklyCurriculum> allCurricula = repository.getAllWeeklyCurricula(DESKTOP_CHAT_ID);
        if (allCurricula.isEmpty()) {
            weeklyCurriculumOutput.setText("Chưa có giáo trình nào được nạp.\n\nHãy nhấn nút 'Nạp giáo trình (PDF/Word)' ở trên để AI tự động chia đều các chương theo số buổi tự học đã lên lịch.");
            return;
        }

        StringBuilder sb = new StringBuilder("TIẾN ĐỘ HỌC TẬP THEO BUỔI (PHÂN BỔ TỪ GIÁO TRÌNH)\n\n");
        String currentSubject = "";
        for (com.studyagent.model.WeeklyCurriculum c : allCurricula) {
            if (!c.getSubjectName().equalsIgnoreCase(currentSubject)) {
                currentSubject = c.getSubjectName();
                sb.append("====================================================\n");
                sb.append("MÔN: ").append(currentSubject.toUpperCase()).append("\n");
                sb.append("====================================================\n\n");
            }
            sb.append(String.format("BUỔI %d: %s%n", c.getWeekNumber(), c.getChapterTitle()));
            sb.append("----------------------------------------------------\n");
            String issue = c.getDetailedLessonIssue();
            if (issue != null) {
                sb.append("[Ghi chú chất lượng: ").append(issue).append("]\n\n");
            }
            sb.append(c.getDetailedSyllabus()).append("\n\n");
        }
        weeklyCurriculumOutput.setText(sb.toString());
        weeklyCurriculumOutput.setCaretPosition(0);
    }

    private void renderQuizQuestions(String subject, List<com.studyagent.model.QuizQuestion> startQuiz, List<com.studyagent.model.QuizQuestion> contestQuiz) {
        StringBuilder sb = new StringBuilder();
        sb.append("CÂU HỎI ÔN TẬP & CONTEST CHO MÔN: ").append(subject.toUpperCase()).append("\n\n");

        if (startQuiz != null && !startQuiz.isEmpty()) {
            sb.append("----------------------------------------------------\n");
            sb.append("1. ĐẦU CA HỌC - ÔN TẬP BÀI TUẦN TRƯỚC (WARM-UP)\n");
            sb.append("----------------------------------------------------\n");
            for (int i = 0; i < startQuiz.size(); i++) {
                var q = startQuiz.get(i);
                sb.append(String.format("Câu %d: %s%n", i + 1, q.getQuestion()));
                for (String opt : q.getOptions()) sb.append("   ").append(opt).append("\n");
                sb.append("   >> Đáp án: ").append(q.getCorrectAnswer()).append(" - ").append(q.getExplanation()).append("\n\n");
            }
        }

        if (contestQuiz != null && !contestQuiz.isEmpty()) {
            sb.append("----------------------------------------------------\n");
            sb.append("2. BÀI THI CONTEST TRẮC NGHIỆM ĐÁNH GIÁ (5 CÂU HỎI)\n");
            sb.append("----------------------------------------------------\n");
            for (int i = 0; i < contestQuiz.size(); i++) {
                var q = contestQuiz.get(i);
                sb.append(String.format("Câu %d: %s%n", i + 1, q.getQuestion()));
                for (String opt : q.getOptions()) sb.append("   ").append(opt).append("\n");
                sb.append("   >> Đáp án: ").append(q.getCorrectAnswer()).append(" - ").append(q.getExplanation()).append("\n\n");
            }
        }

        quizOutput.setText(sb.toString());
    }

    private void render(AnalysisResult result) {
        this.currentTimetableEntries = result.entries != null
                ? new ArrayList<>(result.entries) : new ArrayList<>();
        this.currentSessions = result.sessions != null ? new ArrayList<>(result.sessions) : new ArrayList<>();
        Map<String, Integer> indexes = new java.util.HashMap<>();
        for (StudySession session : currentSessions) {
            String subjectKey = session.getSubjectName() == null ? "" : session.getSubjectName().toLowerCase(java.util.Locale.ROOT);
            int index = indexes.getOrDefault(subjectKey, 0) + 1;
            indexes.put(subjectKey, index);
            session.setSessionIndex(index);
        }
        renderWeeklyCalendar();
        renderSyllabuses();
    }

    private void renderSyllabuses() {
        StringBuilder syllabus = new StringBuilder("ĐỀ CƯƠNG ÔN TẬP\n\n");
        for (SubjectSyllabus item : repository.getAllSyllabuses(DESKTOP_CHAT_ID)) {
            syllabus.append("════════  ").append(item.getSubjectName()).append("  ════════\n");
            syllabus.append("Tổng quan: ").append(item.getOverview()).append("\n\n");
            syllabus.append("Nội dung:\n").append(item.getSyllabusContent()).append("\n\n");
            syllabus.append("Mẹo ôn thi: ").append(item.getExamTips()).append("\n");
            syllabus.append("Tài liệu: ").append(item.getRecommendedMaterials()).append("\n\n");
        }
        syllabusOutput.setText(syllabus.toString());
        syllabusOutput.setCaretPosition(0);
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

package com.studyagent.ui;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.GridLayout;
import java.awt.RenderingHints;
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

import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.filechooser.FileNameExtensionFilter;

import com.studyagent.ai.GeminiService;
import com.studyagent.model.StudySession;

import com.studyagent.model.TimetableEntry;
import com.studyagent.repository.StudyRepository;
import com.studyagent.scheduler.StudyOptimizer;

public class StudyDesktopUI extends JFrame {
    private static final long DESKTOP_CHAT_ID = 1L;
    static { UiTheme.install(); }

    // ── Design tokens (modern indigo / slate palette) ──
    private static Color BG_APP       = new Color(241, 245, 249);   // slate-100
    private static Color BG_CARD      = Color.WHITE;
    private static Color BG_SOFT      = new Color(248, 250, 252);   // slate-50
    private static Color BORDER       = new Color(226, 232, 240);   // slate-200
    private static Color TEXT_PRIMARY = new Color(15, 23, 42);      // slate-900
    private static Color TEXT_SECONDARY = new Color(100, 116, 139); // slate-500
    private static Color TEXT_MUTED   = new Color(148, 163, 184);   // slate-400
    private static Color ACCENT       = new Color(79, 70, 229);     // indigo-600
    private static Color ACCENT_HOVER = new Color(67, 56, 202);     // indigo-700
    private static Color ACCENT_SOFT  = new Color(238, 242, 255);   // indigo-50
    private static Color TEAL         = new Color(13, 148, 136);    // teal-600
    private static Color TEAL_SOFT    = new Color(240, 253, 250);   // teal-50
    private static Color SUCCESS_BG   = new Color(220, 252, 231);   // green-100
    private static Color SUCCESS_FG   = new Color(22, 101, 52);     // green-800
    private static Color WARN_BG      = new Color(254, 243, 199);   // amber-100
    private static Color WARN_FG      = new Color(146, 64, 14);     // amber-800
    private static Color DANGER       = new Color(220, 38, 38);     // red-600
    private static final Font  FONT_TITLE   = safeFont("Segoe UI", Font.BOLD, 28);
    private static final Font  FONT_HEADING = safeFont("Segoe UI", Font.BOLD, 16);
    private static final Font  FONT_BODY    = safeFont("Segoe UI", Font.PLAIN, 14);
    private static final Font  FONT_SMALL   = safeFont("Segoe UI", Font.PLAIN, 12);
    private static final Font  FONT_TIMER   = safeMonospaceFont(Font.BOLD, 68);

    private static Font safeFont(String preferredFamily, int style, int size) {
        String[] candidates = preferredFamily == null || preferredFamily.isBlank()
                ? new String[]{"Segoe UI", "Tahoma", "Arial", "Helvetica", "Dialog", "SansSerif"}
                : new String[]{preferredFamily, "Segoe UI", "Tahoma", "Arial", "Helvetica", "Dialog", "SansSerif"};
        for (String candidate : candidates) {
            if (candidate != null && isFontAvailable(candidate)) {
                return new Font(candidate, style, size);
            }
        }
        return new Font(Font.SANS_SERIF, style, size);
    }

    private static Font safeMonospaceFont(int style, int size) {
        String[] candidates = {"Consolas", "Cascadia Code", "JetBrains Mono", "SFMono-Regular", "Menlo",
                "Monaco", "Liberation Mono", "Courier New", "Courier", "monospace"};
        for (String candidate : candidates) {
            if (isFontAvailable(candidate)) {
                return new Font(candidate, style, size);
            }
        }
        return new Font(Font.MONOSPACED, style, size);
    }

    private static boolean isFontAvailable(String familyName) {
        if (familyName == null || familyName.isBlank()) {
            return false;
        }
        for (String available : GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()) {
            if (available.equalsIgnoreCase(familyName)) {
                return true;
            }
        }
        return false;
    }

    private final GeminiService geminiService;
    private final StudyRepository repository;
    private final StudyOptimizer optimizer;
    private final JTextArea timetableInput = new JTextArea();
    private SyllabusPanel syllabusPanel;
    private HomePanel homePanel;
    private final JLabel breadcrumb = new JLabel("Không gian cá nhân / Tổng quan");
    private final JButton themeButton = new JButton();

    private LessonReviewPanel lessonReviewPanel;
    private ExamPanel examPanel;

    private final JLabel pomodoroModeLabel = new JLabel("TẬP TRUNG");
    private final JLabel pomodoroTimerLabel = new JLabel("25:00");
    private final JLabel pomodoroSessionLabel = new JLabel("Phiên 1 / 4");
    private final JButton pomodoroStartButton = new JButton("Bắt đầu");
    private final JButton pomodoroResetButton = new JButton("Đặt lại");
    private Timer pomodoroTimer;
    private final com.studyagent.model.FocusTimeTracker focusTime;
    private final com.studyagent.calendar.GoogleCalendarCoordinator googleCalendar;
    private GoogleCalendarPanel googleCalendarPanel;
    private Timer calendarRefreshTimer;
    private final JPanel studyStatsPanel=UiKit.transparent(new BorderLayout(12,5));
    private final JLabel studyStats=UiKit.label(" ",14,true,"text"),studyCounts=UiKit.label(" ",12,false,"muted");
    private final javax.swing.JProgressBar studyProgress=new javax.swing.JProgressBar();
    private List<String> activeStudyIds=List.of();
    private final JButton finishStudyButton=new JButton("Hoàn thành ca học");
    private int remainingSeconds = 25 * 60;
    private int pomodoroSession = 1;
    private boolean focusMode = true;
    private final JLabel statusLabel = new JLabel("Sẵn sàng nhận thời khóa biểu & giáo trình");
    private final JButton analyzeButton = new JButton("Phân tích thời khóa biểu");
    private final JButton imageButton = new JButton("Chọn ảnh / PDF");
    private final JButton textbookButton = new JButton("+ Thêm giáo trình");

    /** Ô chỉnh biên kỳ trên màn Lịch học (duy nhất — không còn trên Nhập liệu). */
    private final DatePicker boundFromField = new DatePicker();
    private final DatePicker boundToField = new DatePicker();
    private final JButton applyBoundButton = new JButton("Áp dụng");
    private final JButton regenerateBoundButton = new JButton("Tạo lại lịch tự học");

    private final JTabbedPane scheduleTabs = new JTabbedPane();
    private final JLabel calendarRangeLabel = new JLabel("", JLabel.CENTER);
    private final WeeklyCalendarPanel classCalendarDays = new WeeklyCalendarPanel(false,this::startStudyEarly,this::editStudySession,this::toggleSessionStatus);
    private final WeeklyCalendarPanel studyCalendarDays = new WeeklyCalendarPanel(true,this::startStudyEarly,this::editStudySession,this::toggleSessionStatus);
    private LocalDate displayedWeekStart = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));


    private List<TimetableEntry> currentTimetableEntries = new ArrayList<>();
    private List<StudySession> currentSessions = new ArrayList<>();
    /** Khoảng ngày kỳ học đang áp dụng — lịch chỉ hiện trong khoảng này, không lặp vô hạn. */
    private LocalDate scheduleBoundStart;
    private LocalDate scheduleBoundEnd;

    private final JPanel contentCards = new JPanel(new java.awt.CardLayout());
    private final List<JButton> navButtons = new ArrayList<>();
    private String activeNavKey = "input";

    public StudyDesktopUI(GeminiService geminiService, StudyRepository repository, StudyOptimizer optimizer) {
        this(geminiService,repository,optimizer,new com.studyagent.model.FocusTimeTracker(DESKTOP_CHAT_ID,new com.studyagent.repository.FocusTimeRepository()));
    }
    StudyDesktopUI(GeminiService geminiService, StudyRepository repository, StudyOptimizer optimizer,com.studyagent.model.FocusTimeTracker focusTime) {
        super("Smart Study Agent");
        this.geminiService = geminiService;
        this.repository = repository;
        this.optimizer = optimizer;
        this.focusTime=focusTime;
        this.googleCalendar=new com.studyagent.calendar.GoogleCalendarCoordinator(DESKTOP_CHAT_ID,repository);
        syncThemeColors();
        buildWindow();
    }

    public void showWindow() {
        setVisible(true);
        refreshSavedData();
        googleCalendar.start();
        calendarRefreshTimer=new Timer(10000,e->{if("schedule".equals(activeNavKey))renderWeeklyCalendar();if(googleCalendarPanel!=null)googleCalendarPanel.refresh();});calendarRefreshTimer.start();
    }

    private static void syncThemeColors() {
        BG_APP=UiTheme.color("app");BG_CARD=UiTheme.color("card");BG_SOFT=UiTheme.color("soft");BORDER=UiTheme.color("line");
        TEXT_PRIMARY=UiTheme.color("text");TEXT_SECONDARY=UiTheme.color("muted");TEXT_MUTED=UiTheme.color("muted");
        ACCENT=UiTheme.color("accent");ACCENT_HOVER=UiTheme.color("primary");ACCENT_SOFT=UiTheme.color("tint");
        TEAL=UiTheme.color("green");TEAL_SOFT=UiTheme.color("greenSoft");SUCCESS_BG=UiTheme.color("greenSoft");SUCCESS_FG=UiTheme.color("green");
        WARN_BG=UiTheme.color("warningSoft");WARN_FG=UiTheme.color("warning");DANGER=UiTheme.color("danger");
    }

    private void buildWindow() {
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setMinimumSize(new Dimension(1120,760));setSize(1440,940);setLocationByPlatform(true);
        JPanel root=new JPanel(new BorderLayout());UiTheme.role(root,"app","text");setContentPane(root);
        root.add(createSidebar(),BorderLayout.WEST);
        JPanel workspace=UiKit.transparent(new BorderLayout(0,20));workspace.setBorder(BorderFactory.createEmptyBorder(18,26,14,26));
        workspace.add(createHeader(),BorderLayout.NORTH);workspace.add(createMainContent(),BorderLayout.CENTER);workspace.add(createStatusBar(),BorderLayout.SOUTH);root.add(workspace);
        wireActions();styleAllButtons();UiTheme.apply(root);
    }

    private JPanel createHeader() {
        JPanel header=UiKit.transparent(new BorderLayout(12,0));header.setPreferredSize(new Dimension(400,46));
        breadcrumb.setFont(FONT_SMALL);UiTheme.role(breadcrumb,null,"muted");header.add(breadcrumb);
        JPanel actions=UiKit.transparent(new FlowLayout(FlowLayout.RIGHT,10,0));
        JLabel mode=UiKit.label(geminiService.hasApiKey()?"AI đã cấu hình":"Chưa cấu hình AI",12,true,geminiService.hasApiKey()?"green":"warning");
        mode.setToolTipText("Kết nối và hạn mức được kiểm tra khi thực hiện yêu cầu.");actions.add(mode);
        themeButton.setText(UiTheme.isDark()?"Chế độ sáng":"Chế độ tối");themeButton.addActionListener(e->switchTheme());actions.add(themeButton);header.add(actions,BorderLayout.EAST);return header;
    }

    private void switchTheme() {
        try {UiTheme.setDark(!UiTheme.isDark(),true);}catch(IllegalStateException e){statusLabel.setText(e.getMessage());}
        refreshTheme();
    }
    private void refreshTheme() {
        syncThemeColors();themeButton.setText(UiTheme.isDark()?"Chế độ sáng":"Chế độ tối");
        if(homePanel!=null)homePanel.refresh();
        if(syllabusPanel!=null)syllabusPanel.refreshTheme();if(lessonReviewPanel!=null)lessonReviewPanel.refreshTheme();if(examPanel!=null)examPanel.refreshTheme();
        renderWeeklyCalendar();updatePomodoroLabels();updateNavStyles();UiTheme.apply(getContentPane());revalidate();repaint();
    }
    private void stylePrimaryButton(JButton btn) {
        UiTheme.primary(btn);
        btn.setForeground(Color.WHITE);
        btn.setFont(FONT_HEADING);
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setOpaque(true);
        btn.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        btn.setBorder(BorderFactory.createEmptyBorder(10, 18, 10, 18));
    }

    private void styleSecondaryButton(JButton btn) {
        btn.setBackground(BG_SOFT);
        btn.setForeground(TEXT_PRIMARY);
        btn.setFont(FONT_BODY);
        btn.setFocusPainted(false);
        btn.setOpaque(true);
        btn.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        btn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER, 1, true),
                BorderFactory.createEmptyBorder(8, 14, 8, 14)));
    }

    private void styleAllButtons() {
        stylePrimaryButton(analyzeButton);
        stylePrimaryButton(pomodoroStartButton);
        styleSecondaryButton(pomodoroResetButton);
        styleSecondaryButton(imageButton);
        stylePrimaryButton(textbookButton);



    }

    private JPanel createMainContent() {
        JPanel content = new JPanel(new BorderLayout(14, 0));
        content.setOpaque(false);



        contentCards.setOpaque(false);
        homePanel=new HomePanel(DESKTOP_CHAT_ID,repository,this::openReview,this::showNavPanel,focusTime::summary);
        contentCards.add(homePanel,"home");
        contentCards.add(createInputPanel(), "input");
        contentCards.add(createScheduleCalendarPanel(), "schedule");
        syllabusPanel = new SyllabusPanel(DESKTOP_CHAT_ID, repository, geminiService, this::openTextbook, this::openReview, this::deleteTextbook);
        contentCards.add(syllabusPanel, "syllabus");
        contentCards.add(createQuizPanel(), "quiz");
        examPanel=new ExamPanel(DESKTOP_CHAT_ID,repository,geminiService);contentCards.add(examPanel,"exam");
        contentCards.add(createPomodoroPanel(), "pomodoro");
        content.add(contentCards, BorderLayout.CENTER);

        showNavPanel("home");
        return content;
    }

    private JPanel createSidebar() {
        JPanel sidebar=new JPanel();sidebar.setLayout(new javax.swing.BoxLayout(sidebar,javax.swing.BoxLayout.Y_AXIS));UiTheme.role(sidebar,"card","text");
        sidebar.setBorder(BorderFactory.createEmptyBorder(28,18,24,18));sidebar.setPreferredSize(new Dimension(228,0));
        JLabel brand=UiKit.label("Study Agent",23,true,"text");brand.setAlignmentX(LEFT_ALIGNMENT);sidebar.add(brand);
        JLabel tagline=UiKit.label("TRỢ LÝ HỌC TẬP",10,true,"muted");tagline.setAlignmentX(LEFT_ALIGNMENT);sidebar.add(javax.swing.Box.createVerticalStrut(6));sidebar.add(tagline);sidebar.add(javax.swing.Box.createVerticalStrut(43));
        String[][] entries={{"Tổng quan","home"},{"Lịch học","schedule"},{"Đề cương","syllabus"},{"Ôn tập","quiz"},{"Đề thi","exam"},{"Tập trung","pomodoro"},{"Nhập liệu","input"}};
        for(var entry:entries){sidebar.add(createNavButton(entry[0],entry[1]));sidebar.add(javax.swing.Box.createVerticalStrut(9));}
        sidebar.add(javax.swing.Box.createVerticalGlue());JLabel local=UiKit.label("Không gian cá nhân",12,true,"text");local.setAlignmentX(LEFT_ALIGNMENT);sidebar.add(local);
        sidebar.add(javax.swing.Box.createVerticalStrut(7));JLabel saved=UiKit.label("Dữ liệu lưu trên thiết bị",11,false,"muted");saved.setAlignmentX(LEFT_ALIGNMENT);saved.setMaximumSize(new Dimension(192,24));sidebar.add(saved);return sidebar;
    }
    private static final class NavigationIcon implements Icon {
        private final String key;

        private NavigationIcon(String key) {
            this.key = key;
        }

        @Override
        public int getIconWidth() {
            return 18;
        }

        @Override
        public int getIconHeight() {
            return 18;
        }

        @Override
        public void paintIcon(java.awt.Component component, Graphics graphics, int x, int y) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.translate(x, y);
            g.setColor(component.getForeground());
            g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            switch (key) {
                case "home" -> {
                    g.drawRoundRect(2,2,6,6,2,2);g.drawRoundRect(11,2,6,6,2,2);
                    g.drawRoundRect(2,11,6,6,2,2);g.drawRoundRect(11,11,6,6,2,2);
                }
                case "input" -> {
                    g.drawRoundRect(3, 2, 12, 14, 2, 2);
                    g.drawLine(6, 6, 12, 6);
                    g.drawLine(6, 9, 12, 9);
                    g.drawLine(6, 12, 10, 12);
                }
                case "schedule" -> {
                    g.drawRoundRect(2, 4, 14, 12, 2, 2);
                    g.drawLine(2, 8, 16, 8);
                    g.drawLine(6, 2, 6, 6);
                    g.drawLine(12, 2, 12, 6);
                    g.drawLine(6, 11, 6, 11);
                    g.drawLine(10, 11, 10, 11);
                    g.drawLine(13, 11, 13, 11);
                }
                case "syllabus" -> {
                    g.drawRoundRect(4, 2, 11, 14, 2, 2);
                    g.drawRoundRect(2, 4, 11, 12, 2, 2);
                    g.drawLine(5, 8, 10, 8);
                    g.drawLine(5, 11, 10, 11);
                }
                case "curriculum" -> {
                    g.drawLine(9, 4, 9, 16);
                    g.drawLine(9, 6, 4, 4);
                    g.drawLine(4, 4, 2, 5);
                    g.drawLine(2, 5, 2, 14);
                    g.drawLine(2, 14, 9, 16);
                    g.drawLine(9, 6, 14, 4);
                    g.drawLine(14, 4, 16, 5);
                    g.drawLine(16, 5, 16, 14);
                    g.drawLine(16, 14, 9, 16);
                    g.drawLine(4, 8, 7, 9);
                    g.drawLine(11, 9, 14, 8);
                }
                case "quiz", "exam" -> {
                    g.drawRoundRect(2, 2, 14, 14, 3, 3);
                    g.drawLine(5, 9, 8, 12);
                    g.drawLine(8, 12, 13, 6);
                }
                case "pomodoro" -> {
                    g.drawOval(2, 3, 14, 14);
                    g.drawLine(9, 6, 9, 10);
                    g.drawLine(9, 10, 12, 12);
                    g.drawLine(7, 1, 11, 1);
                    g.drawLine(9, 1, 9, 3);
                }
                default -> g.drawOval(3, 3, 12, 12);
            }
            g.dispose();
        }
    }

    private JButton createNavButton(String text, String key) {
        JButton btn = new JButton(text);
        btn.setFont(FONT_BODY);
        btn.setFocusPainted(false);
        btn.setHorizontalAlignment(javax.swing.SwingConstants.LEFT);
        btn.setIcon(new NavigationIcon(key));
        btn.setIconTextGap(12);
        btn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        btn.setPreferredSize(new Dimension(190, 44));
        btn.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        btn.setAlignmentX(LEFT_ALIGNMENT);
        btn.putClientProperty("navKey", key);
        btn.addActionListener(e -> showNavPanel(key));
        navButtons.add(btn);
        return btn;
    }

    private void showNavPanel(String key) {
        activeNavKey = key;
        if("home".equals(key)&&homePanel!=null)homePanel.refresh();
        breadcrumb.setText("Không gian cá nhân  /  "+navTitle(key));
        if ("syllabus".equals(key) && syllabusPanel != null) syllabusPanel.refresh();
        if ("quiz".equals(key) && lessonReviewPanel != null) lessonReviewPanel.refresh();
        if ("exam".equals(key) && examPanel != null) examPanel.refresh();
        java.awt.CardLayout cl = (java.awt.CardLayout) contentCards.getLayout();
        cl.show(contentCards, key);
        if ("schedule".equals(key)) {
            currentSessions = repository.getStudySessions(DESKTOP_CHAT_ID);
            renderWeeklyCalendar();
            syncBoundFieldsFromState();
        }
        updateNavStyles();for(java.awt.Component screen:contentCards.getComponents())if(screen.isVisible())UiTheme.apply(screen);
    }

    private static String navTitle(String key) {
        return switch(key){case "home"->"Tổng quan";case "schedule"->"Lịch học";case "syllabus"->"Đề cương";case "quiz"->"Ôn tập";case "exam"->"Đề thi";case "pomodoro"->"Tập trung";default->"Nhập liệu";};
    }
    private void updateNavStyles() {
        for(JButton btn:navButtons){boolean active=activeNavKey.equals(btn.getClientProperty("navKey"));UiTheme.role(btn,active?"tint":"card",active?"accent":"muted");btn.setFont(safeFont("Segoe UI",active?Font.BOLD:Font.PLAIN,14));}
    }
    private void openReview(String subject,int number) {
        showNavPanel("quiz");lessonReviewPanel.selectLesson(subject,number);
    }
    private JPanel createQuizPanel() {
        lessonReviewPanel=new LessonReviewPanel(DESKTOP_CHAT_ID,repository,geminiService,null);return lessonReviewPanel;
    }

    private JPanel createScheduleCalendarPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBackground(BG_CARD);
        panel.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));

        JPanel topStack = new JPanel(new BorderLayout(6, 6));
        topStack.setOpaque(false);
        googleCalendarPanel=new GoogleCalendarPanel(googleCalendar);googleCalendarPanel.setCompact(true);
        topStack.add(createBoundEditorBar(), BorderLayout.NORTH);

        JPanel navigation = new JPanel(new BorderLayout(16, 0));
        navigation.setOpaque(false);
        navigation.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        JButton previousWeekButton = new JButton("‹");
        JButton nextWeekButton = new JButton("›");
        for (JButton button : List.of(previousWeekButton, nextWeekButton)) {
            button.setFont(safeFont("Segoe UI", Font.BOLD, 18));
            button.putClientProperty("theme.compact",true);
            button.setFocusPainted(false);
            button.setBackground(BG_SOFT);
            button.setForeground(TEXT_PRIMARY);
            button.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(BORDER, 1, true),
                    BorderFactory.createEmptyBorder(4, 14, 4, 14)));
            button.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        }
        calendarRangeLabel.setFont(safeFont("Segoe UI", Font.BOLD, 16));
        calendarRangeLabel.setForeground(ACCENT);
        previousWeekButton.addActionListener(event -> {
            displayedWeekStart = displayedWeekStart.minusWeeks(1);
            renderWeeklyCalendar();
        });
        nextWeekButton.addActionListener(event -> {
            displayedWeekStart = displayedWeekStart.plusWeeks(1);
            renderWeeklyCalendar();
        });
        JPanel weekButtons=UiKit.transparent(new FlowLayout(FlowLayout.LEFT,8,0));weekButtons.add(previousWeekButton);
        weekButtons.add(UiKit.button("Hôm nay",false,()->{displayedWeekStart=LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));renderWeeklyCalendar();}));
        weekButtons.add(nextWeekButton);navigation.add(weekButtons,BorderLayout.WEST);
        navigation.add(calendarRangeLabel, BorderLayout.CENTER);

        studyStats.setFont(safeFont("Segoe UI",Font.BOLD,13));studyStatsPanel.setBorder(BorderFactory.createEmptyBorder(4,4,3,4));studyStatsPanel.add(studyStats);studyStatsPanel.add(studyCounts,BorderLayout.EAST);studyProgress.setPreferredSize(new Dimension(140,6));studyStatsPanel.add(studyProgress,BorderLayout.SOUTH);topStack.add(studyStatsPanel);
        topStack.add(navigation, BorderLayout.SOUTH);
        panel.add(topStack, BorderLayout.NORTH);

        scheduleTabs.setFont(FONT_HEADING);
        scheduleTabs.addTab("  Trên lớp  ", new JScrollPane(classCalendarDays));
        scheduleTabs.addTab("  Tự học  ", new JScrollPane(studyCalendarDays));
        scheduleTabs.setSelectedIndex(1);
        scheduleTabs.addChangeListener(e->{studyStatsPanel.setVisible(scheduleTabs.getSelectedIndex()==1);});
        panel.add(scheduleTabs, BorderLayout.CENTER);
        syncBoundFieldsFromState();
        renderWeeklyCalendar();
        return panel;
    }

    /** Thanh chỉnh biên kỳ ngay trên màn Lịch học. */
    private JPanel createBoundEditorBar() {
        JPanel bar=UiKit.transparent(new FlowLayout(FlowLayout.LEFT,7,0));
        for(JTextField field:List.of(boundFromField,boundToField)){
            field.setFont(FONT_BODY);field.setPreferredSize(new Dimension(124,34));
        }
        boundFromField.getAccessibleContext().setAccessibleName("Từ ngày");boundToField.getAccessibleContext().setAccessibleName("Đến ngày");
        applyBoundButton.setText("Áp dụng");regenerateBoundButton.setText("Tạo lại lịch");regenerateBoundButton.setToolTipText("Tạo lại lịch tự học cho khoảng ngày đã chọn");
        styleSecondaryButton(applyBoundButton);stylePrimaryButton(regenerateBoundButton);
        applyBoundButton.putClientProperty("theme.compact",true);regenerateBoundButton.putClientProperty("theme.compact",true);
        applyBoundButton.addActionListener(e->applyScheduleBound(false));regenerateBoundButton.addActionListener(e->applyScheduleBound(true));
        bar.add(UiKit.label("Từ",13,false,"muted"));bar.add(boundFromField);bar.add(UiKit.label("Đến",13,false,"muted"));bar.add(boundToField);bar.add(applyBoundButton);bar.add(regenerateBoundButton);bar.add(googleCalendarPanel);
        return bar;
    }
    private void syncBoundFieldsFromState() {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        LocalDate[] bounds = resolveScheduleBounds();
        if (bounds != null) {
            boundFromField.setText(bounds[0].format(fmt));
            boundToField.setText(bounds[1].format(fmt));
        } else if ((boundFromField.getText() == null || boundFromField.getText().isBlank())
                && (boundToField.getText() == null || boundToField.getText().isBlank())) {
            boundFromField.setText(LocalDate.now().format(fmt));
            boundToField.setText(LocalDate.now().plusWeeks(15).format(fmt));
        }
    }

    /**
     * Áp dụng biên kỳ từ ô trên màn Lịch.
     * @param regenerate true = sinh lại lịch học thêm theo biên mới
     */
    private void applyScheduleBound(boolean regenerate) {
        String fromText = boundFromField.getText() == null ? "" : boundFromField.getText().trim();
        String toText = boundToField.getText() == null ? "" : boundToField.getText().trim();
        LocalDate from = StudyOptimizer.parseDate(fromText);
        LocalDate to = StudyOptimizer.parseDate(toText);
        if (from == null || to == null) {
            JOptionPane.showMessageDialog(this,
                    "Ngày không hợp lệ. Dùng định dạng dd/MM/yyyy (ví dụ 01/09/2026).",
                    "Khoảng ngày không hợp lệ", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (to.isBefore(from)) {
            JOptionPane.showMessageDialog(this,
                    "Ngày kết thúc phải sau hoặc bằng ngày bắt đầu.",
                    "Khoảng ngày không hợp lệ", JOptionPane.WARNING_MESSAGE);
            return;
        }

        scheduleBoundStart = from;
        scheduleBoundEnd = to;
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        boundFromField.setText(from.format(fmt));
        boundToField.setText(to.format(fmt));
        displayedWeekStart = from.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

        // Cập nhật note TKB để lọc lịch trên lớp đúng kỳ
        if (!currentTimetableEntries.isEmpty()) {
            applyDateRangeToEntries(currentTimetableEntries, from, to);
            repository.saveTimetableEntries(DESKTOP_CHAT_ID, currentTimetableEntries);
            googleCalendar.requestSync();
        }

        if (regenerate) {
            if (currentTimetableEntries.isEmpty()) {
                JOptionPane.showMessageDialog(this,
                        "Chưa có thời khóa biểu. Hãy phân tích TKB ở mục Nhập liệu trước.",
                        "Chưa có dữ liệu", JOptionPane.INFORMATION_MESSAGE);
                renderWeeklyCalendar();
                return;
            }
            setBusy(true, String.format("Đang sinh lại lịch %s → %s...", from.format(fmt), to.format(fmt)));
            final LocalDate fFrom = from;
            final LocalDate fTo = to;
            new SwingWorker<List<StudySession>, Void>() {
                @Override
                protected List<StudySession> doInBackground() {
                    applyDateRangeToEntries(currentTimetableEntries, fFrom, fTo);
                    List<StudySession> sessions = optimizer.generateSemesterSchedule(
                            currentTimetableEntries, fFrom, fTo);
                    sessions = sessions.stream()
                            .filter(s -> s.getScheduledDate() != null && !s.getScheduledDate().isBlank())
                            .filter(s -> {
                                LocalDate d = LocalDate.parse(s.getScheduledDate());
                                return !d.isBefore(fFrom) && !d.isAfter(fTo);
                            })
                            .toList();
                    repository.saveTimetableEntries(DESKTOP_CHAT_ID, currentTimetableEntries);
                    repository.saveStudySessions(DESKTOP_CHAT_ID, sessions);
                    return sessions;
                }

                @Override
                protected void done() {
                    try {
                        List<StudySession> sessions = get();
                        currentSessions = new ArrayList<>(sessions);
                        Map<String, Integer> indexes = new java.util.HashMap<>();
                        for (StudySession session : currentSessions) {
                            String key = session.getSubjectName() == null ? ""
                                    : session.getSubjectName().toLowerCase(Locale.ROOT);
                            int index = indexes.getOrDefault(key, 0) + 1;
                            indexes.put(key, index);
                            session.setSessionIndex(index);
                        }
                        renderWeeklyCalendar();
                        googleCalendar.requestSync();
                        setBusy(false, String.format("Đã sinh lại %d phiên tự học trong kỳ %s → %s",
                                sessions.size(), fFrom.format(fmt), fTo.format(fmt)));
                        JOptionPane.showMessageDialog(StudyDesktopUI.this,
                                String.format("Đã áp dụng biên kỳ và sinh lại %d buổi học thêm.", sessions.size()),
                                "Thành công", JOptionPane.INFORMATION_MESSAGE);
                    } catch (Exception ex) {
                        setBusy(false, "Sinh lại lịch thất bại");
                        Throwable c = ex.getCause() == null ? ex : ex.getCause();
                        JOptionPane.showMessageDialog(StudyDesktopUI.this, c.getMessage(),
                                "Lỗi", JOptionPane.ERROR_MESSAGE);
                    }
                }
            }.execute();
        } else {
            renderWeeklyCalendar();
            statusLabel.setText(String.format("Đã áp dụng biên kỳ %s → %s (chỉ lọc hiển thị)",
                    from.format(fmt), to.format(fmt)));
            JOptionPane.showMessageDialog(this,
                    "Đã áp dụng biên kỳ.\n"
                            + "• Lịch trên lớp chỉ hiện trong khoảng này.\n"
                            + "• Bấm \"Sinh lại lịch theo biên kỳ\" nếu muốn tạo lại các buổi học thêm cho đúng khoảng mới.",
                    "Đã áp dụng biên kỳ", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void renderWeeklyCalendar() {
        LocalDate weekEnd=displayedWeekStart.plusDays(6);
        calendarRangeLabel.setText(displayedWeekStart.format(DateTimeFormatter.ofPattern("dd/MM"))+" – "+weekEnd.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
        LocalDate[] bounds=resolveScheduleBounds();LocalDate from=bounds==null?null:bounds[0],to=bounds==null?null:bounds[1];
        classCalendarDays.setWeek(displayedWeekStart,from,to,currentTimetableEntries,currentSessions);
        studyCalendarDays.setWeek(displayedWeekStart,from,to,currentTimetableEntries,currentSessions);
        var progress=com.studyagent.model.StudyProgress.summary(currentSessions,java.time.LocalDateTime.now(com.studyagent.model.StudyProgress.ZONE));
        studyStats.setText("Hoàn thành: "+progress.completedDue()+" / "+progress.due()+" ca đến hạn");
        studyStats.setToolTipText("Toàn bộ lịch tự học, tính từ giờ bắt đầu. Các ca học trước được tính vào tỷ lệ khi đến lịch.");
        studyCounts.setText("Quá hạn: "+progress.overdue()+"   ·   Chưa đến lịch: "+progress.upcoming()+"   ·   Học trước: "+progress.completedEarly());
        studyProgress.setMaximum((int)Math.max(1,progress.due()));studyProgress.setValue((int)progress.completedDue());
        if(googleCalendarPanel!=null)googleCalendarPanel.refresh();
        if(!activeStudyIds.isEmpty()&&currentSessions.stream().filter(s->activeStudyIds.contains(s.getId())).allMatch(StudySession::isCompleted)){activeStudyIds=List.of();if(lessonReviewPanel!=null)lessonReviewPanel.setStudySessionAction(null,null);}
        finishStudyButton.setEnabled(!activeStudyIds.isEmpty());
    }
    private JPanel createInputPanel() {
        DateTimeFormatter fmt=DateTimeFormatter.ofPattern("dd/MM/yyyy");
        if(boundFromField.getText().isBlank())boundFromField.setText(LocalDate.now().format(fmt));
        if(boundToField.getText().isBlank())boundToField.setText(LocalDate.now().plusWeeks(15).format(fmt));
        return new InputPanel(imageButton,textbookButton,analyzeButton,timetableInput,this::importTimetableFiles,this::importTextbookFile);
    }
    /**
     * Đọc biên kỳ từ màn Lịch học — bắt buộc khi phân tích TKB
     * để lịch chỉ sinh trong kỳ, không lặp vô hạn.
     */
    private LocalDate[] readScheduleDateRange() {
        String fromText = boundFromField.getText() == null ? "" : boundFromField.getText().trim();
        String toText = boundToField.getText() == null ? "" : boundToField.getText().trim();
        if (fromText.isBlank() || toText.isBlank()) {
            throw new IllegalArgumentException(
                    "Chưa đặt biên kỳ học.\n"
                            + "Vào mục Lịch học → điền Từ ngày / Đến ngày → Áp dụng biên kỳ,\n"
                            + "rồi quay lại Nhập liệu để phân tích TKB.");
        }
        LocalDate from = StudyOptimizer.parseDate(fromText);
        LocalDate to = StudyOptimizer.parseDate(toText);
        if (from == null || to == null) {
            throw new IllegalArgumentException("Khoảng ngày không hợp lệ. Dùng định dạng dd/MM/yyyy (ví dụ 01/09/2026).");
        }
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("Ngày kết thúc phải sau hoặc bằng ngày bắt đầu.");
        }
        return new LocalDate[]{from, to};
    }

    /**
     * Xác định biên kỳ học: biến đã lưu → ô biên kỳ trên Lịch → session đã có ngày → note TKB.
     * Trả null nếu không suy ra được (khi đó lịch trên lớp không hiện, tránh lặp vô hạn).
     */
    private LocalDate[] resolveScheduleBounds() {
        if (scheduleBoundStart != null && scheduleBoundEnd != null
                && !scheduleBoundEnd.isBefore(scheduleBoundStart)) {
            return new LocalDate[]{scheduleBoundStart, scheduleBoundEnd};
        }

        LocalDate fromUi = StudyOptimizer.parseDate(
                boundFromField.getText() == null ? "" : boundFromField.getText().trim());
        LocalDate toUi = StudyOptimizer.parseDate(
                boundToField.getText() == null ? "" : boundToField.getText().trim());
        if (fromUi != null && toUi != null && !toUi.isBefore(fromUi)) {
            scheduleBoundStart = fromUi;
            scheduleBoundEnd = toUi;
            return new LocalDate[]{fromUi, toUi};
        }

        LocalDate min = null;
        LocalDate max = null;
        for (StudySession s : currentSessions) {
            if (s.getScheduledDate() == null || s.getScheduledDate().isBlank()) continue;
            try {
                LocalDate d = LocalDate.parse(s.getScheduledDate());
                if (min == null || d.isBefore(min)) min = d;
                if (max == null || d.isAfter(max)) max = d;
            } catch (Exception ignored) {}
        }
        if (min != null && max != null) {
            scheduleBoundStart = min;
            scheduleBoundEnd = max;
            return new LocalDate[]{min, max};
        }

        // Suy từ note TKB (dạng dd/MM/yyyy đến dd/MM/yyyy)
        for (TimetableEntry entry : currentTimetableEntries) {
            String note = entry.getNote();
            if (note == null || note.isBlank()) continue;
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("(\\d{1,2}/\\d{1,2}/\\d{2,4})\\s*(?:đến|-)\\s*(\\d{1,2}/\\d{1,2}/\\d{2,4})",
                            java.util.regex.Pattern.CASE_INSENSITIVE)
                    .matcher(note);
            if (m.find()) {
                LocalDate a = StudyOptimizer.parseDate(m.group(1));
                LocalDate b = StudyOptimizer.parseDate(m.group(2));
                if (a != null && b != null && !b.isBefore(a)) {
                    if (min == null || a.isBefore(min)) min = a;
                    if (max == null || b.isAfter(max)) max = b;
                }
            }
        }
        if (min != null && max != null) {
            scheduleBoundStart = min;
            scheduleBoundEnd = max;
            return new LocalDate[]{min, max};
        }
        return null;
    }

    /** Gắn khoảng ngày kỳ học vào note mỗi môn nếu chưa có, để lọc lịch trên lớp đúng kỳ. */
    private void applyDateRangeToEntries(List<TimetableEntry> entries, LocalDate from, LocalDate to) {
        if (entries == null || from == null || to == null) return;
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        String rangeText = from.format(fmt) + " đến " + to.format(fmt);
        for (TimetableEntry entry : entries) {
            String note = entry.getNote();
            if (note != null && note.matches("(?s).*\\d{1,2}/\\d{1,2}/\\d{2,4}\\s*(?:đến|-)\\s*\\d{1,2}/\\d{1,2}/\\d{2,4}.*")) {
                continue; // đã có khoảng ngày riêng
            }
            if (note == null || note.isBlank()) {
                entry.setNote(rangeText);
            } else if (!note.contains(rangeText)) {
                entry.setNote(note + " | " + rangeText);
            }
        }
    }



    private JPanel createPomodoroPanel() {
        JPanel panel = new JPanel(new BorderLayout(16, 16));
        panel.setBackground(BG_CARD);
        panel.setBorder(BorderFactory.createEmptyBorder(36, 40, 36, 40));

        JLabel title = new JLabel("Tập trung học theo Pomodoro", JLabel.CENTER);
        title.setFont(safeFont("Segoe UI", Font.BOLD, 22));
        title.setForeground(TEXT_PRIMARY);
        panel.add(title, BorderLayout.NORTH);

        // Timer card
        JPanel timerCard = new JPanel(new GridLayout(3, 1, 6, 6));
        timerCard.setBackground(ACCENT_SOFT);
        timerCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(224, 231, 255), 1, true),
                BorderFactory.createEmptyBorder(28, 40, 28, 40)));
        pomodoroModeLabel.setHorizontalAlignment(JLabel.CENTER);
        pomodoroModeLabel.setFont(FONT_HEADING);
        pomodoroModeLabel.setForeground(ACCENT);
        pomodoroTimerLabel.setHorizontalAlignment(JLabel.CENTER);
        pomodoroTimerLabel.setFont(FONT_TIMER);
        pomodoroTimerLabel.setForeground(TEXT_PRIMARY);
        pomodoroSessionLabel.setHorizontalAlignment(JLabel.CENTER);
        pomodoroSessionLabel.setFont(FONT_BODY);
        pomodoroSessionLabel.setForeground(TEXT_SECONDARY);
        timerCard.add(pomodoroModeLabel);
        timerCard.add(pomodoroTimerLabel);
        timerCard.add(pomodoroSessionLabel);

        JPanel centerWrap = new JPanel(new FlowLayout(FlowLayout.CENTER));
        centerWrap.setOpaque(false);
        centerWrap.add(timerCard);
        panel.add(centerWrap, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 8));
        actions.setOpaque(false);
        pomodoroStartButton.addActionListener(event -> togglePomodoro());
        pomodoroResetButton.addActionListener(event -> resetPomodoro());
        actions.add(pomodoroStartButton);
        actions.add(pomodoroResetButton);
        styleSecondaryButton(finishStudyButton);finishStudyButton.setEnabled(false);finishStudyButton.addActionListener(e->completeActiveStudy(true));actions.add(finishStudyButton);
        panel.add(actions, BorderLayout.SOUTH);

        pomodoroTimer = new Timer(1000, event -> tickPomodoro());
        return panel;
    }

    private JPanel createStatusBar() {
        JPanel status = new JPanel(new BorderLayout());
        status.setOpaque(false);
        status.setBorder(BorderFactory.createEmptyBorder(4, 2, 0, 2));
        statusLabel.setFont(FONT_SMALL);
        statusLabel.setForeground(TEXT_SECONDARY);
        status.add(statusLabel, BorderLayout.WEST);
        return status;
    }

    private void wireActions() {
        analyzeButton.addActionListener(event -> analyzeText());
        imageButton.addActionListener(event -> chooseImages());
        textbookButton.addActionListener(event -> chooseTextbook());



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
        JFileChooser chooser=new JFileChooser();
        chooser.setDialogTitle("Chọn thời khóa biểu (có thể chọn nhiều file)");
        chooser.setMultiSelectionEnabled(true);chooser.setAcceptAllFileFilterUsed(false);
        chooser.setFileFilter(new FileNameExtensionFilter("PDF, Word hoặc ảnh (.pdf, .docx, .png, .jpg, .jpeg, .webp)","pdf","docx","png","jpg","jpeg","webp"));
        if(chooser.showOpenDialog(this)!=JFileChooser.APPROVE_OPTION)return;
        File[] files=chooser.getSelectedFiles();
        if(files==null||files.length==0){if(chooser.getSelectedFile()==null)return;files=new File[]{chooser.getSelectedFile()};}
        importTimetableFiles(files);
    }

    void importTimetableFiles(File[] files) {
        File[] selected=files.clone();
        runAnalysis(()->new com.studyagent.parser.TimetableFiles(geminiService).read(selected));
    }
    /**
     * Chuyển danh sách buổi học thành text:
     * mỗi ngày = 1 ý lớn, mỗi khung giờ = 1 gạch đầu dòng.
     *
     * Ví dụ:
     * Thứ 2:
     * - 07:00-08:50 Lập trình hướng đối tượng (405-A2)
     * - 09:00-11:50 Cơ sở dữ liệu (304-A2)
     */
    private static String formatEntriesAsTimetableText(List<TimetableEntry> entries) {
        if (entries == null || entries.isEmpty()) return "";

        // Gom theo thứ, giữ thứ tự xuất hiện
        Map<String, List<TimetableEntry>> byDay = new java.util.LinkedHashMap<>();
        for (TimetableEntry e : entries) {
            String day = e.getDayOfWeek() == null || e.getDayOfWeek().isBlank()
                    ? "?" : e.getDayOfWeek().trim();
            byDay.computeIfAbsent(day, k -> new ArrayList<>()).add(e);
        }

        // Sắp xếp buổi trong ngày theo giờ bắt đầu
        for (List<TimetableEntry> list : byDay.values()) {
            list.sort(java.util.Comparator.comparing(
                    e -> e.getStartTime() == null ? "99:99" : e.getStartTime()));
        }

        StringBuilder sb = new StringBuilder();
        boolean firstDay = true;
        for (Map.Entry<String, List<TimetableEntry>> dayBlock : byDay.entrySet()) {
            if (!firstDay) sb.append("\n");
            firstDay = false;
            sb.append(dayBlock.getKey()).append(":\n");
            for (TimetableEntry e : dayBlock.getValue()) {
                String start = e.getStartTime() == null ? "?" : e.getStartTime().trim();
                String end = e.getEndTime() == null ? "?" : e.getEndTime().trim();
                String subject = e.getSubjectName() == null || e.getSubjectName().isBlank()
                        ? "Môn học" : e.getSubjectName().trim();
                sb.append("- ").append(start).append("-").append(end).append(" ").append(subject);
                if (e.getRoom() != null && !e.getRoom().isBlank()
                        && !e.getRoom().equalsIgnoreCase("N/A")) {
                    sb.append(" (").append(e.getRoom().trim()).append(")");
                }
                if (e.getNote() != null && !e.getNote().isBlank()) {
                    sb.append(" — ").append(e.getNote().trim());
                }
                sb.append("\n");
            }
        }
        return sb.toString().trim();
    }

    private void chooseTextbook() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Chọn giáo trình hoặc đề cương môn học");
        chooser.setAcceptAllFileFilterUsed(false);
        chooser.setFileFilter(new FileNameExtensionFilter("Tài liệu PDF hoặc Word (.pdf, .docx)", "pdf", "docx"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File file = chooser.getSelectedFile();
        importTextbookFile(file);
    }

    private void importTextbookFile(File file) {
        String fileName = file.getName().toLowerCase(Locale.ROOT);
        if (!fileName.endsWith(".pdf") && !fileName.endsWith(".docx")) {
            JOptionPane.showMessageDialog(this, "Chỉ hỗ trợ file PDF (.pdf) hoặc Word (.docx).",
                    "Định dạng file không hỗ trợ", JOptionPane.ERROR_MESSAGE);
            return;
        }
        Set<String> names=new java.util.LinkedHashSet<>(repository.getDistinctSubjects(DESKTOP_CHAT_ID));
        for(var book:repository.getAllTextbooks(DESKTOP_CHAT_ID))names.add(book.getSubjectName());
        javax.swing.JComboBox<String> subjectPicker=new javax.swing.JComboBox<>(names.toArray(new String[0]));subjectPicker.setEditable(true);
        if(names.isEmpty())subjectPicker.setSelectedItem("");
        JPanel subjectForm=new JPanel(new BorderLayout(0,10));subjectForm.add(new JLabel("Chọn môn có sẵn hoặc nhập tên môn mới:"),BorderLayout.NORTH);subjectForm.add(subjectPicker);
        subjectForm.setPreferredSize(new Dimension(430,85));UiTheme.apply(subjectForm);
        if(JOptionPane.showConfirmDialog(this,subjectForm,"Môn học của tài liệu",JOptionPane.OK_CANCEL_OPTION,JOptionPane.PLAIN_MESSAGE)!=JOptionPane.OK_OPTION)return;
        final String subject=java.util.Objects.toString(subjectPicker.getEditor().getItem(),"").trim();
        if(subject.isBlank()){JOptionPane.showMessageDialog(this,"Hãy nhập tên môn học để lưu tài liệu.","Thiếu tên môn",JOptionPane.WARNING_MESSAGE);return;}

        setBusy(true, "Đang lưu giáo trình và tự động phân tích các chương/bài môn " + subject + "…");

        new SwingWorker<com.studyagent.ai.TextbookProcessor.Result, String>() {
            @Override
            protected com.studyagent.ai.TextbookProcessor.Result doInBackground() {
                final String textForStore;
                final String archivedPath;
                try {
                    textForStore = com.studyagent.parser.DocumentParser.extractTextWithPages(file);
                    if (textForStore.replaceAll("\\[Trang PDF \\d+\\]", "").isBlank())
                        throw new IllegalStateException("Không đọc được văn bản. PDF scan cần OCR trước khi phân tích.");
                    archivedPath = com.studyagent.repository.TextbookFiles.archive(file.toPath()).toString();
                } catch (java.io.IOException e) { throw new IllegalStateException("Không đọc hoặc lưu được file gốc: " + e.getMessage(), e); }
                if (!repository.saveTextbook(DESKTOP_CHAT_ID, subject, file.getName(), textForStore, archivedPath))
                    throw new IllegalStateException("Không lưu được giáo trình. File gốc không bị thay đổi; hãy kiểm tra quyền ghi và dung lượng ổ đĩa.");
                return new com.studyagent.ai.TextbookProcessor(repository, geminiService).process(DESKTOP_CHAT_ID, subject, message -> publish(message));
            }

            @Override
            protected void process(List<String> messages) {
                if (!messages.isEmpty()) statusLabel.setText(messages.get(messages.size()-1));
            }

            @Override
            protected void done() {
                try {
                    var result = get();
                    renderWeeklyCurriculum();
                    setBusy(false, result.summary());
                    showNavPanel("syllabus");syllabusPanel.selectSubject(subject);
                    if (!result.complete()) JOptionPane.showMessageDialog(StudyDesktopUI.this,
                            result.summary()+"\nCác mục đã xong được giữ lại. Chọn Phân tích giáo trình để tiếp tục.",
                            "Phân tích chưa hoàn tất",JOptionPane.WARNING_MESSAGE);                } catch (Exception e) {
                    setBusy(false, "Lỗi nạp giáo trình");
                    renderWeeklyCurriculum();
                    Throwable cause = e.getCause() == null ? e : e.getCause();
                    String msg = cause.getMessage() != null ? cause.getMessage() : cause.toString();
                    JOptionPane.showMessageDialog(StudyDesktopUI.this, msg, "Lỗi nạp giáo trình", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void openTextbook(String subject) {
        StudyRepository.TextbookItem item = repository.getTextbook(DESKTOP_CHAT_ID, subject);
        if (item == null) return;
        try {
            java.nio.file.Path path = item.getOriginalPath() == null ? null : java.nio.file.Path.of(item.getOriginalPath());
            if(path!=null&&!path.isAbsolute())path=com.studyagent.AppPaths.root().resolve(path).normalize();
            if(path!=null&&!Files.isRegularFile(path)&&path.getFileName()!=null){
                var relocated=com.studyagent.AppPaths.resolve("data","textbooks",path.getFileName().toString());
                if(Files.isRegularFile(relocated))path=relocated;
            }
            if (path == null || !Files.isRegularFile(path)) {
                JOptionPane.showMessageDialog(this, "Giáo trình cũ chỉ lưu văn bản, chưa lưu file gốc. Chọn lại file PDF/Word để liên kết. Muốn có số trang trong đề cương, hãy nạp lại PDF ở mục Nhập liệu.");
                JFileChooser chooser = new JFileChooser();
                chooser.setDialogTitle("Chọn lại file gốc: " + item.getFileName());
                chooser.setFileFilter(new FileNameExtensionFilter("PDF hoặc Word", "pdf", "docx"));
                if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
                path = com.studyagent.repository.TextbookFiles.archive(chooser.getSelectedFile().toPath());
                repository.linkTextbookFile(DESKTOP_CHAT_ID, subject, path.toString());
            }
            if (!java.awt.Desktop.isDesktopSupported() || !java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.OPEN))
                throw new IllegalStateException("Hệ thống không hỗ trợ mở file tự động. File đã lưu tại: " + path);
            java.awt.Desktop.getDesktop().open(path.toFile());
        } catch (Exception e) { JOptionPane.showMessageDialog(this, "Không mở được file giáo trình: " + e.getMessage(), "Mở file gốc", JOptionPane.WARNING_MESSAGE); }
    }

    private void deleteTextbook(String picked) {
        int confirmation = JOptionPane.showConfirmDialog(this,
                "Xóa giáo trình và đề cương môn " + picked + "?",
                "Xác nhận xóa", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirmation != JOptionPane.YES_OPTION) return;

        if (!repository.deleteTextbook(DESKTOP_CHAT_ID, picked)) {
            JOptionPane.showMessageDialog(this, "Không thể xóa giáo trình. Vui lòng thử lại.",
                    "Lỗi xóa giáo trình", JOptionPane.ERROR_MESSAGE);
            return;
        }
        renderWeeklyCurriculum();
        statusLabel.setText("Đã xóa giáo trình và đề cương môn " + picked);

    }

    private void startStudyEarly(StudySession session) {
        var plans=repository.prepareChapters(DESKTOP_CHAT_ID,session.getSubjectName());
        var chapter=plans.isEmpty()?null:chooseStudyChapter(session,plans);
        if(!plans.isEmpty()&&chapter==null)return;
        var group=com.studyagent.scheduler.ScheduleBlocks.mergeStudySessions(currentSessions).stream().filter(b->b.sessions().stream().anyMatch(s->java.util.Objects.equals(s.getId(),session.getId()))).findFirst();
        List<StudySession> members=group.map(com.studyagent.scheduler.ScheduleBlocks.StudyBlock::sessions).orElse(List.of(session));
        List<String> ids=members.stream().map(StudySession::getId).filter(java.util.Objects::nonNull).toList();
        String startedAt=java.time.Instant.now().toString();
        try{repository.markStudySessionsStarted(DESKTOP_CHAT_ID,ids,startedAt);}catch(RuntimeException e){JOptionPane.showMessageDialog(this,e.getMessage(),"Chưa vào học được",JOptionPane.WARNING_MESSAGE);return;}
        for(var member:members)if(!member.hasStarted())member.setStartedAt(startedAt);
        activeStudyIds=members.stream().allMatch(StudySession::isCompleted)?List.of():ids;
        finishStudyButton.setEnabled(!activeStudyIds.isEmpty());
        lessonReviewPanel.setStudySessionAction(activeStudyIds.isEmpty()?null:session.getSubjectName(),activeStudyIds.isEmpty()?null:()->completeActiveStudy(true));
        googleCalendar.requestSync();
        // Tính thời lượng của buổi học thêm (phút)
        int durationMinutes = 25; // mặc định
        try {
            int startMin = parseTimeToMinutes(session.getStartTime());
            int endMin = parseTimeToMinutes(session.getEndTime());
            if (endMin > startMin) {
                durationMinutes = Math.min(60,members.stream().mapToInt(s->Math.max(0,parseTimeToMinutes(s.getEndTime())-parseTimeToMinutes(s.getStartTime()))).sum());
            }
        } catch (Exception ignored) {}

        // Kích hoạt đồng hồ Pomodoro
        focusTime.advance(focusMode,remainingSeconds);focusTime.pause();
        remainingSeconds = durationMinutes * 60;
        focusMode = true;
        pomodoroSession = 1;
        pomodoroModeLabel.setText("ĐANG HỌC TRƯỚC: " + session.getSubjectName().toUpperCase());
        updatePomodoroLabels();

        // Chuyển sang panel Pomodoro
        showNavPanel("pomodoro");
        if (!pomodoroTimer.isRunning()) {
            pomodoroTimer.start();
            pomodoroStartButton.setText("Tạm dừng");
        }
        focusTime.start();
        statusLabel.setText("Đang vào học trước môn " + session.getSubjectName() + " (" + durationMinutes + " phút)");

        // Mở thông tin đề cương buổi học
        if(chapter!=null)openReview(session.getSubjectName(),chapter.getWeekNumber());
    }

    private void completeActiveStudy(boolean stopTimer){
        if(activeStudyIds.isEmpty())return;
        try{
            repository.completeStudySessions(DESKTOP_CHAT_ID,activeStudyIds);
            for(var session:currentSessions)if(activeStudyIds.contains(session.getId()))session.setCompleted(true);
            activeStudyIds=List.of();finishStudyButton.setEnabled(false);lessonReviewPanel.setStudySessionAction(null,null);
            if(stopTimer){focusTime.advance(focusMode,remainingSeconds);pomodoroTimer.stop();focusTime.pause();pomodoroStartButton.setText("Tiếp tục");}
            renderWeeklyCalendar();googleCalendar.requestSync();statusLabel.setText("Đã hoàn thành ca học · Đang cập nhật nhắc học trên Google Calendar");
        }catch(RuntimeException e){JOptionPane.showMessageDialog(this,e.getMessage(),"Chưa lưu được ca học",JOptionPane.WARNING_MESSAGE);}
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

    private com.studyagent.model.WeeklyCurriculum chooseStudyChapter(StudySession session,List<com.studyagent.model.WeeklyCurriculum> plans) {
        var picker = new javax.swing.JComboBox<com.studyagent.model.WeeklyCurriculum>(plans.toArray(new com.studyagent.model.WeeklyCurriculum[0]));
        picker.setRenderer(new javax.swing.DefaultListCellRenderer() {
            @Override public java.awt.Component getListCellRendererComponent(javax.swing.JList<?> list,Object value,int index,boolean selected,boolean focus) {
                var label=(JLabel)super.getListCellRendererComponent(list,value,index,selected,focus);
                if(value instanceof com.studyagent.model.WeeklyCurriculum plan)label.setText(plan.getChapterTitle());return label;
            }
        });
        if (JOptionPane.showConfirmDialog(this,picker,"Chọn chương muốn học — "+session.getSubjectName(),JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION)return null;
        return (com.studyagent.model.WeeklyCurriculum)picker.getSelectedItem();
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
        editPanel.add(new JLabel("Giờ bắt đầu (từ 20:00):"));
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
                    || parseTimeToMinutes(endTime) <= parseTimeToMinutes(startTime)
                    || parseTimeToMinutes(endTime)-parseTimeToMinutes(startTime)>60
                    || parseTimeToMinutes(startTime) < 20 * 60 || parseTimeToMinutes(endTime) > 22 * 60) {
                JOptionPane.showMessageDialog(this, "Hãy nhập giờ dạng HH:mm trong khoảng 20:00–22:00, giờ kết thúc sau giờ bắt đầu; mỗi môn tối đa 60 phút/ngày.",
                        "Lịch học không hợp lệ", JOptionPane.ERROR_MESSAGE);
                return;
            }
            String updatedDay = (String) dayCombo.getSelectedItem();
            String updatedDate=session.getScheduledDate();
            if (session.getScheduledDate() != null && !session.getScheduledDate().isBlank()) {
                LocalDate originalDate = LocalDate.parse(session.getScheduledDate());
                int selectedDayIndex = java.util.Arrays.asList(days).indexOf(updatedDay);
                updatedDate=originalDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).plusDays(selectedDayIndex).toString();
            }
            int used=0;
            for(var other:currentSessions)if(other!=session&&(session.getId()==null||!session.getId().equals(other.getId()))
                    &&other.getSubjectName().trim().equalsIgnoreCase(session.getSubjectName().trim())
                    &&(updatedDate!=null?updatedDate.equals(other.getScheduledDate()):StudyOptimizer.dayIndex(updatedDay)==StudyOptimizer.dayIndex(other.getDayOfWeek())))
                used+=Math.max(0,parseTimeToMinutes(other.getEndTime())-parseTimeToMinutes(other.getStartTime()));
            if(used+parseTimeToMinutes(endTime)-parseTimeToMinutes(startTime)>60){JOptionPane.showMessageDialog(this,"Môn này đã có "+used+" phút trong ngày đã chọn. Tổng thời gian mỗi môn không được vượt 60 phút/ngày.","Vượt thời gian học",JOptionPane.WARNING_MESSAGE);return;}
            session.setScheduledDate(updatedDate);
            session.setDayOfWeek(updatedDay);
            session.setStartTime(startTime);
            session.setEndTime(endTime);
            session.setTopicOverview(topicField.getText().trim());

            repository.updateStudySession(session);
            renderWeeklyCalendar();
            googleCalendar.requestSync();
            JOptionPane.showMessageDialog(this, "Đã cập nhật lịch học thêm thành công!", "Thành công", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void toggleSessionStatus(StudySession session) {
        boolean newStatus = !session.isCompleted();
        repository.toggleStudySessionCompleted(session.getId(), newStatus);
        session.setCompleted(newStatus);
        renderWeeklyCalendar();
        googleCalendar.requestSync();
    }

    private void runAnalysis(EntryLoader loader) {
        final LocalDate[] dateRange;
        try {
            dateRange = readScheduleDateRange();
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Khoảng ngày không hợp lệ", JOptionPane.WARNING_MESSAGE);
            return;
        }

        DateTimeFormatter statusFmt = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        setBusy(true, String.format("Đang sinh lịch từ %s đến %s (không lặp ngoài khoảng này)...",
                dateRange[0].format(statusFmt), dateRange[1].format(statusFmt)));
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
                // Gắn khoảng ngày vào TKB + chỉ sinh session trong khoảng đã chọn
                applyDateRangeToEntries(entries, dateRange[0], dateRange[1]);
                List<StudySession> sessions = optimizer.generateSemesterSchedule(
                        entries, dateRange[0], dateRange[1]);
                // Loại bỏ mọi session không có ngày hoặc ngoài khoảng
                sessions = sessions.stream()
                        .filter(s -> s.getScheduledDate() != null && !s.getScheduledDate().isBlank())
                        .filter(s -> {
                            LocalDate d = LocalDate.parse(s.getScheduledDate());
                            return !d.isBefore(dateRange[0]) && !d.isAfter(dateRange[1]);
                        })
                        .toList();
                repository.saveTimetableEntries(DESKTOP_CHAT_ID, entries);
                repository.saveStudySessions(DESKTOP_CHAT_ID, sessions);

                Set<String> subjects = new HashSet<>();
                for (TimetableEntry entry : entries) {
                    if (entry.getSubjectName() != null && !entry.getSubjectName().isBlank()) {
                        subjects.add(entry.getSubjectName().trim());
                    }
                }
                return new AnalysisResult(entries, sessions, subjects.size());
            }

            @Override
            protected void done() {
                try {
                    AnalysisResult result = get();
                    scheduleBoundStart = dateRange[0];
                    scheduleBoundEnd = dateRange[1];
                    displayedWeekStart = dateRange[0].with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                    render(result);
                    timetableInput.setText(formatEntriesAsTimetableText(result.entries));
                    timetableInput.setCaretPosition(0);
                    showNavPanel("schedule");
                    setBusy(false, String.format(
                            "Hoàn tất kỳ %s → %s: %d buổi trên lớp, %d ca tự học, %d môn.",
                            dateRange[0].format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                            dateRange[1].format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
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

        // Khôi phục biên kỳ học từ ngày đã gán cho các buổi (tránh hiện lịch vô hạn)
        LocalDate min = null;
        LocalDate max = null;
        for (StudySession s : sessions) {
            if (s.getScheduledDate() == null || s.getScheduledDate().isBlank()) continue;
            try {
                LocalDate d = LocalDate.parse(s.getScheduledDate());
                if (min == null || d.isBefore(min)) min = d;
                if (max == null || d.isAfter(max)) max = d;
            } catch (Exception ignored) {}
        }
        scheduleBoundStart = min;
        scheduleBoundEnd = max;
        if (min != null) {
            displayedWeekStart = (LocalDate.now().isBefore(min)?min:(max!=null&&LocalDate.now().isAfter(max)?max:LocalDate.now())).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy");
            boundFromField.setText(min.format(fmt));
            if (max != null) boundToField.setText(max.format(fmt));
        }
        if (!entries.isEmpty() || !sessions.isEmpty()) {
            render(new AnalysisResult(entries, sessions, 0));
            statusLabel.setText("Đã tải dữ liệu đã lưu");
        }
        renderWeeklyCurriculum();
    }

    private void renderWeeklyCurriculum() {
        if(homePanel!=null)homePanel.refresh();
        if(syllabusPanel!=null)syllabusPanel.refresh();
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

        if (syllabusPanel != null) syllabusPanel.refresh();
        googleCalendar.requestSync();
    }

    private void setBusy(boolean busy, String message) {
        analyzeButton.setEnabled(!busy);
        imageButton.setEnabled(!busy);
        textbookButton.setEnabled(!busy);
        statusLabel.setText(message);
    }

    private void togglePomodoro() {
        if (pomodoroTimer.isRunning()) {
            remainingSeconds-=focusTime.advance(focusMode,remainingSeconds);
            pomodoroTimer.stop();
            focusTime.pause();updatePomodoroLabels();
            pomodoroStartButton.setText("Tiếp tục");
            statusLabel.setText("Pomodoro đang tạm dừng");
        } else {
            focusTime.start();
            pomodoroTimer.start();
            pomodoroStartButton.setText("Tạm dừng");
            statusLabel.setText(focusMode ? "Đang tập trung học" : "Đang nghỉ");
        }
    }

    private void resetPomodoro() {
        focusTime.advance(focusMode,remainingSeconds);
        pomodoroTimer.stop();
        focusTime.pause();
        remainingSeconds = 25 * 60;
        pomodoroSession = 1;
        focusMode = true;
        pomodoroStartButton.setText("Bắt đầu");
        updatePomodoroLabels();
        statusLabel.setText("Pomodoro đã được đặt lại");
    }

    private void tickPomodoro() {
        remainingSeconds-=focusTime.advance(focusMode,remainingSeconds);
        if("home".equals(activeNavKey)&&homePanel!=null)homePanel.refreshFocusTime();
        if (remainingSeconds > 0) {
            updatePomodoroLabels();
            return;
        }
        focusTime.flush();focusTime.restartInterval();

        Toolkit.getDefaultToolkit().beep();
        if (focusMode) {
            completeActiveStudy(false);
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

    @Override public void dispose(){
        if(calendarRefreshTimer!=null)calendarRefreshTimer.stop();
        if(googleCalendarPanel!=null)googleCalendarPanel.close();
        if(googleCalendar!=null)googleCalendar.close();
        if(pomodoroTimer!=null)pomodoroTimer.stop();
        try{if(focusTime!=null){focusTime.advance(focusMode,remainingSeconds);focusTime.pause();}}
        finally{super.dispose();}
    }

    private void updatePomodoroLabels() {
        int minutes = remainingSeconds / 60;
        int seconds = remainingSeconds % 60;
        pomodoroTimerLabel.setText(String.format("%02d:%02d", minutes, seconds));
        pomodoroModeLabel.setText(focusMode ? "TẬP TRUNG" : "NGHỈ GIẢI LAO");
        pomodoroModeLabel.setForeground(focusMode ? ACCENT : TEAL);
        pomodoroTimerLabel.setForeground(focusMode ? TEXT_PRIMARY : TEAL);
        pomodoroSessionLabel.setText("Phiên " + pomodoroSession + " / 4");
    }

    private static JTextArea createOutputArea() {
        JTextArea area = new JTextArea();
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setFont(safeMonospaceFont(Font.PLAIN, 14));
        area.setForeground(TEXT_PRIMARY);
        area.setBackground(BG_SOFT);
        area.setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));
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

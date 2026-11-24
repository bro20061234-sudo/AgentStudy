package com.studyagent.ui;

import com.studyagent.calendar.GoogleCalendarCoordinator;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.net.URI;

/** Phone setup stays visible and actionable; credentials and account approval remain with the user. */
public final class GoogleCalendarPanel extends JPanel {
    private final GoogleCalendarCoordinator coordinator;
    private final JLabel status=UiKit.label(" ",12,false,"muted");
    private final JButton settingsButton=UiKit.button("Google Calendar…",false,this::showSettings);
    private boolean compact;
    private JDialog settings;private Timer polling;
    public GoogleCalendarPanel(GoogleCalendarCoordinator coordinator){
        super(new BorderLayout(12,0));this.coordinator=coordinator;setOpaque(false);setBorder(BorderFactory.createEmptyBorder(8,4,8,4));
        add(status);add(settingsButton,BorderLayout.EAST);refresh();
    }
    public void setCompact(boolean value){compact=value;status.setVisible(!value);setBorder(value?BorderFactory.createEmptyBorder():BorderFactory.createEmptyBorder(8,4,8,4));settingsButton.putClientProperty("theme.compact",value);refresh();}
    public void refresh(){status.setText(coordinator.status());status.setToolTipText(coordinator.status());UiTheme.role(status,null,coordinator.pending()?"warning":coordinator.connected()?"green":"muted");settingsButton.setToolTipText(coordinator.status());if(compact){settingsButton.setText(coordinator.pending()?"Google · Chờ đồng bộ":coordinator.connected()?"Google · Đã đồng bộ":"Google Calendar…");UiTheme.role(settingsButton,"card",coordinator.pending()?"warning":coordinator.connected()?"green":"text");}if(settings!=null)UiTheme.apply(settings.getContentPane());}
    private void showSettings(){
        if(settings!=null){settings.toFront();return;}
        settings=new JDialog(SwingUtilities.getWindowAncestor(this),"Nhắc lịch trên điện thoại · Google Calendar",Dialog.ModalityType.MODELESS);settings.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        JPanel content=new UiKit.Card(new BorderLayout(0,14));content.add(UiKit.label("Kết nối lịch học với điện thoại",23,true,"text"),BorderLayout.NORTH);
        var instructions=LessonHtml.pane();LessonHtml.show(instructions,"","## Thiết lập một lần\n1. Tạo dự án tại Google Cloud, bật Google Calendar API.\n2. Thiết lập Google Auth Platform: tên ứng dụng Study Agent, email hỗ trợ, tài khoản thử nghiệm là Gmail của bạn nếu dự án ở chế độ Testing.\n3. Tạo OAuth client loại Desktop app, tải file JSON.\n4. Bấm Chọn cấu hình OAuth bên dưới, rồi Kết nối Google và cấp quyền trên trình duyệt.\n5. Trên điện thoại, đăng nhập cùng Gmail trong Google Calendar, bật đồng bộ lịch Study Agent và quyền thông báo.\n## Cách nhắc\n- Mỗi buổi trên lớp và tự học: nhắc trước 10 phút.\n- Ca tự học chưa vào học: nhắc thêm sau 5 và 10 phút. Google sẽ có hai sự kiện nhắc nhỏ tương ứng.\n- Đã vào học: bỏ hai nhắc muộn. Đã hoàn thành: bỏ tất cả nhắc của ca đó.\n- Lịch đã đồng bộ tiếp tục nhắc khi máy tính tắt. Nếu học trước khi máy không có Internet, cần đồng bộ lại để Google ngừng nhắc.\n- Quyền kết nối chỉ dùng để tạo và quản lý lịch riêng của Study Agent.\n\n[Hướng dẫn Google OAuth](https://developers.google.com/workspace/calendar/quickstart/java) · [Bật thông báo trên điện thoại](https://support.google.com/calendar/answer/37242?hl=vi)");
        instructions.addHyperlinkListener(e->{if(e.getEventType()==javax.swing.event.HyperlinkEvent.EventType.ACTIVATED)browse(URI.create(e.getURL().toString()));});content.add(UiKit.scroll(instructions));
        JPanel footer=UiKit.transparent(new BorderLayout(0,10));JTextArea detail=QuestionPlayer.text(coordinator.status(),13,false,"muted");detail.setRows(3);footer.add(detail,BorderLayout.NORTH);
        JPanel actions=UiKit.transparent(new FlowLayout(FlowLayout.LEFT,8,0));
        JButton choose=UiKit.button("Chọn cấu hình OAuth (.json)",false,()->{
            JFileChooser picker=new JFileChooser();picker.setFileFilter(new FileNameExtensionFilter("Desktop OAuth JSON","json"));picker.setAcceptAllFileFilterUsed(false);
            if(picker.showOpenDialog(settings)==JFileChooser.APPROVE_OPTION)try{coordinator.importClient(picker.getSelectedFile().toPath());refresh();}catch(Exception e){error(e);}
        });
        JButton connect=UiKit.button("Kết nối Google",true,()->coordinator.connect(GoogleCalendarPanel::browse,e->SwingUtilities.invokeLater(()->{refresh();if(e!=null)error(e);})));
        JButton sync=UiKit.button("Đồng bộ ngay",false,coordinator::requestSync);actions.add(choose);actions.add(connect);actions.add(sync);footer.add(actions,BorderLayout.SOUTH);content.add(footer,BorderLayout.SOUTH);
        polling=new Timer(700,e->{detail.setText(coordinator.status());choose.setEnabled(!coordinator.busy());connect.setEnabled(coordinator.configured()&&!coordinator.busy());connect.setText(coordinator.connected()?"Đăng nhập lại":"Kết nối Google");sync.setEnabled(coordinator.connected()&&!coordinator.busy());refresh();});polling.start();
        settings.addWindowListener(new java.awt.event.WindowAdapter(){@Override public void windowClosed(java.awt.event.WindowEvent e){polling.stop();settings=null;}});
        connect.setEnabled(coordinator.configured()&&!coordinator.busy());sync.setEnabled(coordinator.connected());settings.setContentPane(content);UiTheme.apply(content);settings.setSize(800,760);settings.setLocationRelativeTo(this);settings.setVisible(true);
    }
    private static void browse(URI uri){try{if(!Desktop.isDesktopSupported()||!Desktop.getDesktop().isSupported(Desktop.Action.BROWSE))throw new IllegalStateException("Không mở được trình duyệt mặc định.");Desktop.getDesktop().browse(uri);}catch(Exception e){throw new IllegalStateException("Không mở được trang Google. Hãy kiểm tra trình duyệt mặc định.",e);}}
    private void error(Throwable e){JOptionPane.showMessageDialog(settings==null?this:settings,e.getMessage(),"Kết nối Google Calendar",JOptionPane.WARNING_MESSAGE);}
    public void close(){if(settings!=null)settings.dispose();}
}

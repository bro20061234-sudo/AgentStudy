package com.studyagent.ui.preview;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

/** Interactive visual proposal, with sample data only. Never opens the database or calls AI. */
public final class StudyUiPreview extends JPanel {
    private static final int W=1440,H=940;
    private static final String[] NAV={"Tổng quan","Lịch học","Giáo trình","Đề cương","Ôn tập","Pomodoro","Nhập liệu"};
    private final List<Hit> hits=new ArrayList<>();
    private boolean dark;
    private int page=0,chapter=0;
    private String toast="";
    private record Hit(Rectangle bounds,Runnable action){}
    private Color bg,card,soft,line,ink,muted,accent,tint,green;
    private Graphics2D g;

    public StudyUiPreview(){
        setPreferredSize(new Dimension(W,H));
        addMouseListener(new MouseAdapter(){@Override public void mouseClicked(MouseEvent event){Point p=point(event);for(int i=hits.size()-1;i>=0;i--)if(hits.get(i).bounds.contains(p)){hits.get(i).action.run();repaint();break;}}});
        addMouseMotionListener(new MouseMotionAdapter(){@Override public void mouseMoved(MouseEvent e){boolean clickable=hits.stream().anyMatch(h->h.bounds.contains(point(e)));setCursor(Cursor.getPredefinedCursor(clickable?Cursor.HAND_CURSOR:Cursor.DEFAULT_CURSOR));}});
    }
    private Point point(MouseEvent e){double scale=Math.min(getWidth()/(double)W,getHeight()/(double)H);return new Point((int)((e.getX()-(getWidth()-W*scale)/2)/scale),(int)((e.getY()-(getHeight()-H*scale)/2)/scale));}
    private void palette(){
        bg=c(dark?"10141F":"F5F6FA");card=c(dark?"191F2D":"FFFFFF");soft=c(dark?"222A3B":"F8F9FC");line=c(dark?"30394C":"E7EAF2");
        ink=c(dark?"EDF0F8":"20273B");muted=c(dark?"A2ADC4":"788299");accent=c(dark?"AC9AFF":"7560E8");tint=c(dark?"302A4B":"EFEBFF");green=c(dark?"6CD6B0":"279675");
    }
    @Override protected void paintComponent(Graphics graphics){
        super.paintComponent(graphics);palette();g=(Graphics2D)graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(bg);g.fillRect(0,0,getWidth(),getHeight());double scale=Math.min(getWidth()/(double)W,getHeight()/(double)H);g.translate((getWidth()-W*scale)/2,(getHeight()-H*scale)/2);g.scale(scale,scale);hits.clear();
        sidebar();header();
        switch(page){case 0->overview();case 1->calendar();case 2->library();case 3->syllabus();case 4->review();case 5->pomodoro();case 6->input();}
        text("BẢN XEM TRƯỚC  ·  Dữ liệu minh họa",262,916,11,muted,false);
        text("Đã lưu trên thiết bị",1230,916,11,muted,false);
        if(!toast.isEmpty()){box(480,849,670,42,12,ink);text(toast,500,876,13,bg,false);}
        g.dispose();
    }
    private void sidebar(){
        box(0,0,230,H,0,card);rule(230,0,230,H);
        box(24,29,38,38,12,c("7560E8"));icon(0,33,38,Color.WHITE);text("Study Agent",74,48,21,ink,true);text("TRỢ LÝ HỌC TẬP",75,65,9,muted,true);
        text("KHÔNG GIAN HỌC TẬP",26,123,10,muted,true);
        for(int i=0;i<NAV.length;i++){
            int y=145+i*53;final int index=i;
            if(page==i)box(14,y,202,43,12,tint);
            icon(i,29,y+12,page==i?accent:muted);text(NAV[i],63,y+27,14,page==i?accent:muted,page==i);
            hit(14,y,202,43,()->{page=index;toast="";});
        }
        box(19,702,192,100,14,soft);text("Mỗi ngày một chút",35,730,13,ink,true);
        text("Học theo nhịp của bạn.",35,752,11,muted,false);text("Tiến bộ theo cách của bạn.",35,771,11,muted,false);
        rule(20,831,210,831);box(26,853,33,33,16,tint);text("B",37,875,14,accent,true);text("Không gian cá nhân",71,866,12,ink,true);text("Lưu cục bộ trên máy",71,885,10,muted,false);
    }
    private void header(){
        text("Không gian cá nhân",264,43,12,muted,false);text("/",402,43,12,line,false);text(NAV[page],421,43,12,ink,true);
        pill("AI đã cấu hình",1087,23,134,29,dark?c("18392F"):c("E7F5EE"),green);
        box(1234,22,166,32,10,card);outline(1234,22,166,32,10,line);text(dark?"Chế độ sáng":"Chế độ tối",1274,43,12,ink,false);
        g.setColor(ink);if(dark){g.drawOval(1250,32,11,11);for(int a=0;a<8;a++){double angle=a*Math.PI/4;g.drawLine(1255+(int)(8*Math.cos(angle)),37+(int)(8*Math.sin(angle)),1255+(int)(10*Math.cos(angle)),37+(int)(10*Math.sin(angle)));}}
        else{g.fillOval(1249,30,14,14);g.setColor(card);g.fillOval(1254,27,12,13);}
        hit(1234,22,166,32,()->{dark=!dark;toast="";});rule(262,75,1400,75);
    }
    private void heading(String title,String sub){text(title,264,126,28,ink,true);text(sub,265,153,13,muted,false);}
    private void overview(){
        heading("Hôm nay, mình học gì?","Từng chương nhỏ, thêm một bước tiến. Đây là không gian học tập của bạn.");
        pill("THỨ SÁU, 30 THÁNG 10",1190,107,210,31,card,muted);
        box(264,179,1136,144,20,dark?c("292541"):c("ECE8FB"));pill("TIẾP TỤC ĐANG HỌC",287,197,173,23,dark?c("3C335E"):c("DFD7FA"),accent);
        text("Cơ sở dữ liệu",287,252,24,ink,true);text("Bài 6 · Mô hình dữ liệu quan hệ",288,279,14,muted,false);
        progress(287,298,400,5,.46,accent);text("6 / 14 bài đã học",707,303,11,muted,false);
        button("Tiếp tục học   →",1164,238,207,43,true,()->page=3);
        stat(264,346,"CA TỰ HỌC HÔM NAY","02","Chọn chương khi vào học",0);
        stat(650,346,"TIẾN ĐỘ TỔNG QUAN","9 / 22","chương, bài đã hoàn thành",3);
        stat(1036,346,"THỜI GIAN TẬP TRUNG","1h 25m","trong tuần này",5);
        text("Các môn của bạn",265,511,18,ink,true);link("Xem thư viện  →",1259,511,()->page=2);
        subject(264,535,"Cơ sở dữ liệu","14 bài",6,14,c("8A75E8"),"Bài 6 · Mô hình dữ liệu quan hệ");
        subject(650,535,"Hệ điều hành","4 chương",2,4,c("4FA79C"),"Chương 2 · Quản lý tiến trình");
        subject(1036,535,"Lịch sử Đảng","4 chương",1,4,c("D19962"),"Chương 1 · Giai đoạn 1930–1945");
        box(264,758,1136,113,16,card);text("Lịch tự học tiếp theo",286,788,15,ink,true);text("Hôm nay",286,814,12,muted,false);
        pill("19:30 – 20:30",460,784,136,33,soft,ink);text("Cơ sở dữ liệu",620,800,14,ink,true);text("Bạn tự chọn bài muốn học trong ca này",620,825,12,muted,false);button("Vào học   →",1214,788,160,40,false,()->page=3);
    }
    private void stat(int x,int y,String label,String value,String detail,int icon){box(x,y,364,123,16,card);text(label,x+22,y+28,10,muted,true);text(value,x+22,y+68,30,ink,true);text(detail,x+22,y+96,12,muted,false);box(x+303,y+22,38,38,12,soft);icon(icon,x+313,y+32,accent);}
    private void subject(int x,int y,String title,String count,int done,int total,Color color,String next){
        box(x,y,364,199,17,card);box(x+21,y+21,38,38,12,soft);icon(2,x+31,y+31,color);pill(count,x+251,y+26,91,26,soft,muted);
        text(title,x+21,y+87,18,ink,true);text(next,x+21,y+114,12,muted,false);progress(x+21,y+139,320,6,done/(double)total,color);
        text(done+" / "+total+" mục đã học",x+21,y+174,12,muted,false);link("Mở môn  →",x+251,y+174,()->page=3);
    }
    private void syllabus(){
        heading("Đề cương giáo trình","Nội dung theo đúng chương, bài trong tài liệu. Học theo thứ tự bạn chọn.");
        button("Cơ sở dữ liệu",264,178,268,39,false,()->notice("Bản mẫu: đang hiển thị môn Cơ sở dữ liệu"));g.setColor(muted);g.drawLine(506,195,511,200);g.drawLine(511,200,516,195);
        button("Mở file gốc",1027,178,145,39,false,()->notice("Bản xem trước không mở tài liệu thật"));button("Phân tích / tiếp tục",1184,178,216,39,true,()->notice("Khi áp dụng: tự soạn phần còn thiếu, giữ các mục đã xong"));
        box(264,238,290,634,17,card);text("NỘI DUNG MÔN HỌC",285,270,10,muted,true);text("14 bài · Đã phân tích xong",285,294,12,green,false);
        String[] titles={"Khái niệm chung về CSDL","Kiến trúc cơ sở dữ liệu","Các mô hình dữ liệu","Mô hình hóa dữ liệu","Mô hình dữ liệu quan hệ","Ngôn ngữ truy vấn","Phụ thuộc hàm"};
        for(int i=0;i<titles.length;i++){
            int y=314+i*67;final int n=i;if(i==chapter)box(275,y,268,59,10,tint);
            text(String.format("%02d",i+1),287,y+27,12,i==chapter?accent:muted,true);
            wrapped(titles[i],322,y+22,204,13,i==chapter?accent:ink,i==chapter,18);
            text(i<4?"Đã học":"Chưa học",322,y+46,10,muted,false);hit(275,y,268,59,()->chapter=n);
        }
        rule(285,807,532,807);text("Tiến độ học",285,832,11,muted,false);text("6 / 14 bài",470,832,11,ink,true);progress(285,849,246,5,.43,accent);
        box(573,238,827,634,17,card);pill("ĐỀ CƯƠNG ĐÃ LƯU",600,259,158,24,soft,green);text("Trang PDF 12–20",1218,276,11,muted,false);
        String title=chapter==0?"Khái niệm chung về cơ sở dữ liệu":titles[chapter];text("Bài "+(chapter+1)+" · "+title,600,322,23,ink,true);
        text("Một bài có thể học qua nhiều ca. Tiến độ được lưu khi bạn hoàn thành.",601,348,12,muted,false);rule(601,369,1370,369);
        section("01","Mục tiêu học tập",601,403);
        bullet("Phân biệt dữ liệu, cơ sở dữ liệu và hệ quản trị cơ sở dữ liệu.",620,436);
        bullet("Nhận biết các thành phần và vai trò của một hệ thống CSDL.",620,464);
        section("02","Kiến thức trọng tâm",601,514);
        text("Cơ sở dữ liệu là gì?",620,551,14,ink,true);
        wrapped("Một tập hợp dữ liệu có liên quan, được tổ chức để lưu trữ, tìm kiếm và sử dụng cho một mục đích cụ thể.",620,576,722,13,muted,false,23);
        box(619,623,749,83,12,soft);text("VÍ DỤ",637,647,10,accent,true);wrapped("CSDL sinh viên lưu mã sinh viên, họ tên, lớp và kết quả học tập. Các dữ liệu liên kết với nhau thay vì nằm rời rạc.",637,671,710,13,ink,false,21);
        section("03","Bài tập & tự kiểm tra",601,749);text("Hãy mô tả một CSDL bạn gặp trong cuộc sống hằng ngày.",620,782,13,muted,false);
        rule(600,806,1371,806);button("Đánh dấu đã học",600,821,187,34,false,()->notice("Bản mẫu: đã đánh dấu hoàn thành bài học"));button("Luyện tập bài này   →",1132,821,237,34,true,()->page=4);
    }
    private void section(String number,String title,int x,int y){box(x,y-19,27,26,7,tint);text(number,x+6,y-1,10,accent,true);text(title,x+40,y,16,ink,true);}
    private void bullet(String value,int x,int y){g.setColor(accent);g.fillOval(x,y-7,4,4);text(value,x+15,y,13,ink,false);}
    private void calendar(){
        heading("Dành thời gian cho việc học","Lịch giữ chỗ cho thời gian tự học. Đến giờ, bạn tự chọn chương muốn học.");
        button("‹",264,179,39,36,false,()->notice("Bản mẫu: tuần 26/10 – 01/11"));button("Hôm nay",313,179,99,36,false,()->{});button("›",422,179,39,36,false,()->{});
        text("26 tháng 10 – 01 tháng 11, 2026",482,203,17,ink,true);pill("Lịch tự học",1141,180,122,35,tint,accent);pill("Trên lớp",1274,180,126,35,card,muted);
        box(264,236,1136,636,17,card);String[] days={"THỨ HAI","THỨ BA","THỨ TƯ","THỨ NĂM","THỨ SÁU","THỨ BẢY","CHỦ NHẬT"};
        int left=334,col=150;
        for(int i=0;i<7;i++){int x=left+i*col;if(i==4)box(x,248,145,609,10,dark?c("211F33"):c("F8F6FF"));text(days[i],x+18,275,10,muted,true);text(""+(i<6?26+i:1),x+58,306,22,i==4?accent:ink,true);if(i>0)rule(x-4,330,x-4,854);}
        for(int i=0;i<6;i++){int y=365+i*86;text((18+i)+":00",280,y,11,muted,false);rule(330,y-5,1380,y-5);}
        event(337,489,142,82,"Cơ sở dữ liệu","19:30 – 20:30",accent);event(637,532,142,82,"Hệ điều hành","20:00 – 21:00",green);event(937,489,142,82,"Cơ sở dữ liệu","19:30 – 20:30",accent);event(1087,618,142,82,"Lịch sử Đảng","21:00 – 22:00",c("D19962"));
        text("Mẹo: bấm vào ca tự học để chọn bài và bắt đầu.",284,846,12,muted,false);
    }
    private void event(int x,int y,int w,int h,String title,String time,Color color){box(x,y,w,h,10,soft);g.setColor(color);g.fillRoundRect(x,y+10,3,h-20,3,3);text(time,x+11,y+22,11,color,true);wrapped(title,x+11,y+45,w-20,12,ink,true,18);hit(x,y,w,h,()->page=3);}
    private void library(){
        heading("Thư viện giáo trình","Tài liệu gốc, đề cương và nội dung học của bạn ở cùng một nơi.");button("+  Thêm giáo trình",1190,109,210,41,true,()->page=6);
        box(264,179,1136,78,16,card);icon(2,287,204,accent);text("Nạp một lần. Sẵn sàng cho cả môn học.",326,210,15,ink,true);text("Tự nhận diện chương/bài, soạn đề cương và lưu kết quả ngay trên máy.",326,234,12,muted,false);
        String[] names={"Cơ sở dữ liệu","Hệ điều hành","Lịch sử Đảng cộng sản Việt Nam"};String[] counts={"14 bài","4 chương","4 chương"};
        for(int i=0;i<3;i++){int y=285+i*172;box(264,y,1136,150,17,card);box(286,y+25,76,98,12,soft);icon(2,313,y+60,accent);text(names[i],385,y+40,19,ink,true);text("PDF  ·  "+counts[i]+"  ·  Đã lưu file gốc",385,y+68,12,muted,false);pill("Đề cương đã sẵn sàng",385,y+88,187,27,dark?c("18392F"):c("E7F5EE"),green);button("Mở file",1032,y+53,125,39,false,()->notice("Bản xem trước: mở PDF gốc bằng ứng dụng trên máy"));button("Xem đề cương",1171,y+53,200,39,true,()->page=3);}
    }
    private void review(){
        heading("Hiểu bài qua từng câu hỏi","Luyện tập theo đúng nội dung đã học. Câu trả lời được tự động lưu.");
        pill("Cơ sở dữ liệu  /  Bài 1",264,178,273,33,card,ink);pill("Bộ câu hỏi đã lưu",1215,179,185,30,tint,accent);
        box(264,232,821,640,18,card);text("CÂU 02 / 12",291,268,11,muted,true);progress(424,258,631,5,2/12.,accent);
        wrapped("Hệ quản trị cơ sở dữ liệu có vai trò nào sau đây?",293,324,752,24,ink,true,34);
        String[] options={"Quản lý, lưu trữ và truy xuất dữ liệu có tổ chức.","Chỉ dùng để soạn thảo văn bản và trình chiếu.","Thay thế toàn bộ phần cứng của máy tính.","Chỉ lưu dữ liệu dưới dạng hình ảnh."};
        for(int i=0;i<4;i++){int y=393+i*79;box(292,y,764,64,12,i==0?tint:soft);outline(292,y,764,64,12,i==0?accent:line);box(310,y+18,27,27,9,i==0?accent:card);text(""+(char)('A'+i),319,y+37,12,i==0?c("FFFFFF"):muted,true);text(options[i],352,y+38,14,ink,false);hit(292,y,764,64,()->notice("Bản mẫu: đã lưu lựa chọn"));}
        rule(292,742,1056,742);text("Đã tự lưu câu trả lời",292,789,12,green,false);button("Câu tiếp theo   →",823,762,233,43,true,()->notice("Bản mẫu: chuyển sang câu hỏi tiếp theo"));
        box(1105,232,295,333,17,card);text("Bài luyện của bạn",1127,267,17,ink,true);text("Tiến độ trả lời",1127,305,12,muted,false);text("2 / 12 câu",1274,305,12,ink,true);progress(1127,321,249,5,2/12.,accent);
        for(int i=0;i<12;i++){int x=1129+i%5*49,y=347+i/5*48;box(x,y,36,35,9,i<2?tint:soft);text(""+(i+1),x+12,y+23,12,i<2?accent:muted,i<2);}
        button("Nộp bài",1127,508,251,37,true,()->notice("Bản mẫu: chấm điểm và hiển thị lời giải"));
        box(1105,585,295,141,17,card);text("Học từ câu trả lời sai",1127,619,15,ink,true);wrapped("Sau khi nộp bài, xem giải thích hoặc luyện lại những câu bạn chưa nắm chắc.",1127,646,245,13,muted,false,23);
        link("Xem lịch sử làm bài  →",1127,775,()->notice("Bản mẫu: lịch sử điểm và các lần luyện tập"));
    }
    private void pomodoro(){
        heading("Một khoảng thời gian cho riêng việc học","Bắt đầu nhỏ. Tập trung vào một điều, rồi nghỉ một chút.");
        box(443,208,775,623,25,card);pill("TẬP TRUNG",725,244,195,32,tint,accent);
        g.setColor(line);g.setStroke(new BasicStroke(9));g.drawOval(687,321,275,275);g.setColor(accent);g.draw(new Arc2D.Double(687,321,275,275,90,275,Arc2D.OPEN));g.setStroke(new BasicStroke(1));
        text("25:00",715,481,68,ink,true);text("PHIÊN 1 / 4",779,518,12,muted,true);
        text("Cơ sở dữ liệu · Bài bạn đang học",660,649,16,ink,true);button("Bắt đầu tập trung",680,682,286,48,true,()->notice("Bản mẫu: bắt đầu bộ đếm 25 phút"));link("Điều chỉnh thời lượng",747,775,()->notice("Bản mẫu: tùy chỉnh thời gian học và nghỉ"));
    }
    private void input(){
        heading("Thêm vào không gian học tập","Bắt đầu bằng thời khóa biểu hoặc giáo trình của bạn.");
        box(264,196,552,391,19,card);box(842,196,558,391,19,card);
        icon(1,302,231,accent);text("Thời khóa biểu",302,297,23,ink,true);wrapped("Nhập văn bản hoặc chọn ảnh, PDF. Ứng dụng đọc lịch trên lớp và xếp ca tự học theo giờ trống.",302,338,470,14,muted,false,25);button("Chọn ảnh / PDF",302,484,223,44,true,()->notice("Bản mẫu: chọn file thời khóa biểu"));link("Nhập bằng văn bản",546,512,()->notice("Bản mẫu: nhập nội dung thời khóa biểu"));
        icon(2,881,231,green);text("Giáo trình môn học",881,297,23,ink,true);wrapped("Thêm PDF hoặc Word. Tự phân tích cấu trúc và soạn đề cương cho từng chương, bài.",881,338,462,14,muted,false,25);button("+  Thêm giáo trình",881,484,265,44,true,()->notice("Bản mẫu: chọn môn và file giáo trình"));
        box(264,613,1136,132,17,soft);text("Mọi thứ được kết nối",289,648,16,ink,true);text("Thời khóa biểu   →   Ca tự học   →   Chọn chương / bài   →   Ôn tập & theo dõi tiến độ",289,685,15,accent,true);text("Bạn chủ động chọn nội dung học. Lịch không ép một chương vào một ca cố định.",289,717,12,muted,false);
    }
    private void icon(int type,int x,int y,Color color){
        g.setColor(color);g.setStroke(new BasicStroke(1.6f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
        switch(type){
            case 0->{for(int i=0;i<4;i++)g.drawRoundRect(x+i%2*10,y+i/2*10,7,7,2,2);}
            case 1->{g.drawRoundRect(x,y+2,19,17,3,3);g.drawLine(x,y+7,x+19,y+7);g.drawLine(x+5,y,x+5,y+4);g.drawLine(x+14,y,x+14,y+4);}
            case 2->{g.drawRoundRect(x,y,18,20,3,3);g.drawLine(x+5,y,x+5,y+20);g.drawLine(x+9,y+6,x+14,y+6);g.drawLine(x+9,y+10,x+14,y+10);}
            case 3->{g.drawRoundRect(x+2,y,15,20,3,3);g.drawLine(x+6,y+6,x+13,y+6);g.drawLine(x+6,y+10,x+13,y+10);g.drawLine(x+6,y+14,x+11,y+14);}
            case 4->{g.drawRoundRect(x,y,19,19,4,4);g.drawLine(x+4,y+10,x+8,y+14);g.drawLine(x+8,y+14,x+15,y+6);}
            case 5->{g.drawOval(x,y+3,19,19);g.drawLine(x+6,y,x+13,y);g.drawLine(x+10,y+7,x+10,y+13);g.drawLine(x+10,y+13,x+14,y+13);}
            default->{g.drawRoundRect(x,y+6,20,14,3,3);g.drawLine(x+10,y+13,x+10,y);g.drawLine(x+5,y+5,x+10,y);g.drawLine(x+10,y,x+15,y+5);}
        }g.setStroke(new BasicStroke(1));
    }
    private void notice(String value){toast=value;new javax.swing.Timer(3500,e->{toast="";repaint();((javax.swing.Timer)e.getSource()).stop();}).start();}
    private void button(String title,int x,int y,int w,int h,boolean primary,Runnable action){box(x,y,w,h,10,primary?c(dark?"8C76EF":"7560E8"):card);if(!primary)outline(x,y,w,h,10,line);g.setFont(new Font("Segoe UI",Font.BOLD,13));int width=g.getFontMetrics().stringWidth(title);text(title,x+(w-width)/2,y+h/2+5,13,primary?Color.WHITE:ink,true);hit(x,y,w,h,action);}
    private void link(String value,int x,int y,Runnable action){text(value,x,y,12,accent,true);hit(x,y-18,Math.max(100,value.length()*8),25,action);}
    private void hit(int x,int y,int w,int h,Runnable action){hits.add(new Hit(new Rectangle(x,y,w,h),action));}
    private void pill(String value,int x,int y,int w,int h,Color fill,Color fg){box(x,y,w,h,9,fill);text(value,x+13,y+h/2+4,11,fg,true);}
    private void progress(int x,int y,int w,int h,double value,Color color){box(x,y,w,h,h,line);box(x,y,(int)(w*value),h,h,color);}
    private void box(int x,int y,int w,int h,int radius,Color color){g.setColor(color);g.fillRoundRect(x,y,w,h,radius*2,radius*2);}
    private void outline(int x,int y,int w,int h,int radius,Color color){g.setColor(color);g.drawRoundRect(x,y,w,h,radius*2,radius*2);}
    private void rule(int x,int y,int x2,int y2){g.setColor(line);g.drawLine(x,y,x2,y2);}
    private void text(String value,int x,int y,int size,Color color,boolean bold){g.setFont(new Font("Segoe UI",bold?Font.BOLD:Font.PLAIN,size));g.setColor(color);g.drawString(value,x,y);}
    private int wrapped(String value,int x,int y,int width,int size,Color color,boolean bold,int leading){g.setFont(new Font("Segoe UI",bold?Font.BOLD:Font.PLAIN,size));StringBuilder row=new StringBuilder();for(String word:value.split(" ")){String next=row.isEmpty()?word:row+" "+word;if(g.getFontMetrics().stringWidth(next)>width&&!row.isEmpty()){text(row.toString(),x,y,size,color,bold);y+=leading;row=new StringBuilder(word);}else row=new StringBuilder(next);}if(!row.isEmpty())text(row.toString(),x,y,size,color,bold);return y;}
    private static Color c(String hex){return new Color(Integer.parseInt(hex,16));}
    public static void main(String[] args)throws Exception{
        boolean render=args.length>0&&args[0].equals("--render");
        SwingUtilities.invokeAndWait(()->{try{
            if(render){Path directory=Path.of("artifacts","ui-preview");Files.createDirectories(directory);for(boolean dark:new boolean[]{false,true})for(int page=0;page<NAV.length;page++){
                StudyUiPreview preview=new StudyUiPreview();preview.dark=dark;preview.page=page;preview.setSize(W,H);BufferedImage image=new BufferedImage(W,H,BufferedImage.TYPE_INT_RGB);Graphics2D graphics=image.createGraphics();preview.paint(graphics);graphics.dispose();ImageIO.write(image,"png",directory.resolve((dark?"dark":"light")+"-"+page+".png").toFile());}
            }else{JFrame window=new JFrame("Study Agent · Xem trước giao diện");window.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);window.setContentPane(new StudyUiPreview());window.pack();window.setMinimumSize(new Dimension(1080,735));window.setLocationRelativeTo(null);window.setVisible(true);}
        }catch(Exception e){throw new RuntimeException(e);}});
    }
}

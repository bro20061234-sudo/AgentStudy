package com.studyagent.calendar;

import com.google.gson.*;
import com.studyagent.AppPaths;
import com.studyagent.model.StudyProgress;
import com.studyagent.repository.StudyRepository;
import java.io.IOException;
import java.net.URI;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Serial background sync retries pending local changes; no network runs until the real window opens. */
public final class GoogleCalendarCoordinator implements AutoCloseable {
    private final long chatId;private final StudyRepository repository;private final GoogleOAuth oauth;
    private final Path settingsFile=AppPaths.resolve("data","google","sync.json");
    private final ScheduledExecutorService worker=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"Google-Calendar-Sync");t.setDaemon(true);return t;});
    private final AtomicBoolean force=new AtomicBoolean(true),queued=new AtomicBoolean();
    private volatile boolean started,connecting,syncing,pending;
    private volatile String status="Chưa kết nối Google Calendar",calendarId="",lastHash="";
    public GoogleCalendarCoordinator(long chatId,StudyRepository repository){
        this.chatId=chatId;this.repository=repository;
        try{oauth=new GoogleOAuth(AppPaths.resolve("data","google"));}catch(IOException e){throw new IllegalStateException("Không đọc được cấu hình Google",e);}
        try{var settings=PrivateJson.read(settingsFile);calendarId=PrivateJson.text(settings,"calendar_id");lastHash=PrivateJson.text(settings,"last_hash");}catch(IOException ignored){}
        if(oauth.connected()){pending=true;status="Đã kết nối · Đang chờ kiểm tra đồng bộ";}
    }
    public void start(){if(started)return;started=true;worker.scheduleWithFixedDelay(this::sync,2,30,TimeUnit.SECONDS);}
    public boolean configured(){return oauth.configured();}
    public boolean connected(){return oauth.connected();}
    public boolean busy(){return connecting||syncing;}
    public boolean pending(){return pending;}
    public String status(){return status;}
    public void importClient(Path source)throws IOException{oauth.importClient(source);status="Đã chọn cấu hình OAuth · Bấm Kết nối Google";}
    public void connect(Consumer<URI> browser,Consumer<Throwable> finished){
        if(connecting||syncing)return;connecting=true;status="Đang chờ đăng nhập và cấp quyền trên trình duyệt…";
        worker.execute(()->{Throwable failure=null;try{oauth.authorize(browser);pending=true;force.set(true);sync();}catch(Throwable e){failure=e;status="Chưa kết nối: "+safe(e);}finally{connecting=false;finished.accept(failure);}});
    }
    public void requestSync(){
        pending=connected();force.set(true);
        if(!started||!connected())return;
        if(queued.compareAndSet(false,true))worker.schedule(()->{queued.set(false);sync();},300,TimeUnit.MILLISECONDS);
    }
    private void sync(){
        if(!oauth.connected())return;
        syncing=true;
        try{
            var planned=CalendarPlanner.plan(CalendarPlanner.load(repository,chatId),LocalDateTime.now(StudyProgress.ZONE));
            String hash=CalendarPlanner.hash(planned.stream().map(e->e.id()+":"+CalendarReconciler.fingerprint(e.body())).collect(java.util.stream.Collectors.joining("|")));
            if(!force.getAndSet(false)&&hash.equals(lastHash)){pending=false;return;}
            pending=true;status="Đang đồng bộ lịch và nhắc học với Google…";
            var gateway=new GoogleCalendarGateway(oauth);calendarId=gateway.ensureCalendar(calendarId);
            // Save the calendar ID before event writes, so a partial failure cannot create another calendar.
            saveSettings(lastHash);
            new CalendarReconciler().sync(gateway,calendarId,planned);
            saveSettings(hash);lastHash=hash;pending=force.get();
            status="Đã đồng bộ "+LocalTime.now(StudyProgress.ZONE).format(DateTimeFormatter.ofPattern("HH:mm"))+(pending?" · Còn thay đổi đang chờ":" · Điện thoại sẽ nhắc theo lịch đã lưu");
        }catch(Exception e){pending=true;force.set(true);status="Chưa đồng bộ: "+safe(e)+" · Sẽ tự thử lại khi có kết nối.";}
        finally{syncing=false;}
    }
    private void saveSettings(String hash)throws IOException{JsonObject settings=new JsonObject();settings.addProperty("calendar_id",calendarId);settings.addProperty("last_hash",hash);PrivateJson.write(settingsFile,settings);}
    private static String safe(Throwable e){String value=e.getMessage();return value==null?"Lỗi kết nối Google":value;}
    @Override public void close(){worker.shutdown();try{if(!worker.awaitTermination(2,TimeUnit.SECONDS))worker.shutdownNow();}catch(InterruptedException e){worker.shutdownNow();Thread.currentThread().interrupt();}}
}

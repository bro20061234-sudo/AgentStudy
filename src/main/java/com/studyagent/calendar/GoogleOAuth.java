package com.studyagent.calendar;

import com.google.gson.*;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.time.*;
import java.util.*;
import java.util.function.Consumer;

/** Installed-app Authorization Code + PKCE, using a loopback listener and the system browser. */
public final class GoogleOAuth {
    public static final String SCOPE="https://www.googleapis.com/auth/calendar.app.created";
    private static final URI TOKEN=URI.create("https://oauth2.googleapis.com/token");
    private final Path directory;
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(12)).followRedirects(HttpClient.Redirect.NEVER).build();
    private JsonObject token;
    @FunctionalInterface interface TokenExchange {JsonObject exchange(Map<String,String> values)throws Exception;}
    private final TokenExchange testExchange;
    public GoogleOAuth(Path directory)throws IOException{this(directory,null);}
    GoogleOAuth(Path directory,TokenExchange exchange)throws IOException{this.directory=directory;this.testExchange=exchange;try{token=PrivateJson.read(directory.resolve("token.json"));}catch(IOException e){token=new JsonObject();}}
    public boolean configured(){return Files.isRegularFile(directory.resolve("client.json"));}
    public synchronized boolean connected(){return !PrivateJson.text(token,"refresh_token").isBlank();}
    public void importClient(Path source)throws IOException{
        JsonObject json=PrivateJson.read(source);validateClient(json);
        JsonObject old;try{old=configured()?PrivateJson.read(directory.resolve("client.json")):new JsonObject();}catch(IOException e){old=new JsonObject();}
        if(old.has("installed")&&!PrivateJson.text(old.getAsJsonObject("installed"),"client_id").equals(PrivateJson.text(json.getAsJsonObject("installed"),"client_id")))throw new IOException("Đang có cấu hình OAuth khác. Hãy dùng đúng dự án đã kết nối hoặc đổi tên thư mục data/google để cấu hình một kết nối mới.");
        PrivateJson.write(directory.resolve("client.json"),json);
    }
    private JsonObject client()throws IOException{JsonObject json=PrivateJson.read(directory.resolve("client.json"));validateClient(json);return json.getAsJsonObject("installed");}
    private static void validateClient(JsonObject json)throws IOException{
        if(!json.has("installed")||!json.get("installed").isJsonObject()||!PrivateJson.text(json.getAsJsonObject("installed"),"client_id").endsWith(".apps.googleusercontent.com"))throw new IOException("Chọn file OAuth JSON loại Desktop app, tải từ Google Cloud. API key Gemini không dùng để kết nối lịch.");
    }
    public void authorize(Consumer<URI> browser)throws Exception{
        JsonObject client=client();String verifier=random(),state=random(),challenge=Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));
        try(ServerSocket server=new ServerSocket()){
            server.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"),0));server.setSoTimeout(1000);
            String redirect="http://127.0.0.1:"+server.getLocalPort()+"/oauth2/callback";
            Map<String,String> query=new LinkedHashMap<>();query.put("client_id",PrivateJson.text(client,"client_id"));query.put("redirect_uri",redirect);query.put("response_type","code");query.put("scope",SCOPE);query.put("access_type","offline");query.put("prompt","consent select_account");query.put("state",state);query.put("code_challenge",challenge);query.put("code_challenge_method","S256");
            browser.accept(URI.create("https://accounts.google.com/o/oauth2/v2/auth?"+form(query)));
            long deadline=System.nanoTime()+Duration.ofMinutes(3).toNanos();String code=null;
            while(System.nanoTime()<deadline&&!Thread.currentThread().isInterrupted()){
                try(Socket socket=server.accept()){
                    socket.setSoTimeout(3000);var reader=new BufferedReader(new InputStreamReader(socket.getInputStream(),StandardCharsets.US_ASCII));String line=reader.readLine();
                    if(line==null||line.length()>16384){reply(socket,400,"Yêu cầu không hợp lệ.");continue;}
                    String[] parts=line.split(" ");if(parts.length<2||!parts[0].equals("GET")){reply(socket,400,"Yêu cầu không hợp lệ.");continue;}
                    URI target=URI.create(parts[1]);if(!"/oauth2/callback".equals(target.getPath())){reply(socket,404,"Không tìm thấy.");continue;}
                    Map<String,String> values=parseQuery(target.getRawQuery());
                    if(!MessageDigest.isEqual(state.getBytes(StandardCharsets.UTF_8),values.getOrDefault("state","").getBytes(StandardCharsets.UTF_8))){reply(socket,400,"Phiên kết nối không hợp lệ.");continue;}
                    if(values.containsKey("error")){reply(socket,200,"Bạn đã hủy cấp quyền. Quay lại Study Agent để thử lại.");throw new IOException("Bạn chưa cấp quyền Google Calendar. Có thể kết nối lại khi sẵn sàng.");}
                    code=values.get("code");if(code==null||code.isBlank()){reply(socket,400,"Thiếu mã kết nối.");continue;}
                    reply(socket,200,"Đã nhận xác nhận từ Google. Bạn có thể đóng trang này và quay lại Study Agent.");break;
                }catch(SocketTimeoutException ignored){}
            }
            if(code==null)throw new IOException("Hết thời gian chờ đăng nhập Google. Bấm Kết nối để thử lại.");
            Map<String,String> data=clientFields(client);data.put("grant_type","authorization_code");data.put("code",code);data.put("redirect_uri",redirect);data.put("code_verifier",verifier);
            JsonObject result=exchange(data);if(PrivateJson.text(result,"refresh_token").isBlank())throw new IOException("Google chưa cấp quyền duy trì đồng bộ. Hãy đăng nhập lại và chấp nhận quyền tạo lịch Study Agent.");
            synchronized(this){store(result);}
        }
    }
    public synchronized String accessToken()throws Exception{
        long expiry=token.has("expires_at")?token.get("expires_at").getAsLong():0;
        if(expiry>System.currentTimeMillis()+60000&&!PrivateJson.text(token,"access_token").isBlank())return PrivateJson.text(token,"access_token");
        String refresh=PrivateJson.text(token,"refresh_token");if(refresh.isBlank())throw new IOException("Chưa kết nối Google Calendar.");
        Map<String,String> data=clientFields(client());data.put("grant_type","refresh_token");data.put("refresh_token",refresh);
        JsonObject result=exchange(data);result.addProperty("refresh_token",refresh);store(result);return PrivateJson.text(token,"access_token");
    }
    public synchronized void invalidateAccess(){token.addProperty("expires_at",0);}
    private void store(JsonObject result)throws IOException{long seconds=result.has("expires_in")?result.get("expires_in").getAsLong():3600;result.addProperty("expires_at",System.currentTimeMillis()+seconds*1000);PrivateJson.write(directory.resolve("token.json"),result);token=result;}
    private JsonObject exchange(Map<String,String> data)throws Exception{
        if(testExchange!=null)return testExchange.exchange(Map.copyOf(data));
        var request=HttpRequest.newBuilder(TOKEN).timeout(Duration.ofSeconds(20)).header("Content-Type","application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(form(data))).build();
        var response=http.send(request,HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if(response.statusCode()!=200)throw new IOException("Không lấy được quyền Google (HTTP "+response.statusCode()+"). Hãy kiểm tra cấu hình Desktop OAuth, tài khoản thử nghiệm và đăng nhập lại.");
        JsonObject json=JsonParser.parseString(response.body()).getAsJsonObject();if(PrivateJson.text(json,"access_token").isBlank())throw new IOException("Google chưa trả quyền truy cập hợp lệ.");return json;
    }
    private static Map<String,String> clientFields(JsonObject client){Map<String,String> data=new LinkedHashMap<>();data.put("client_id",PrivateJson.text(client,"client_id"));String secret=PrivateJson.text(client,"client_secret");if(!secret.isBlank())data.put("client_secret",secret);return data;}
    static String form(Map<String,String> values){return values.entrySet().stream().map(e->URLEncoder.encode(e.getKey(),StandardCharsets.UTF_8)+"="+URLEncoder.encode(e.getValue(),StandardCharsets.UTF_8)).collect(java.util.stream.Collectors.joining("&"));}
    private static String random(){byte[] bytes=new byte[32];new SecureRandom().nextBytes(bytes);return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);}
    static Map<String,String> parseQuery(String query){Map<String,String> result=new HashMap<>();if(query!=null)for(String pair:query.split("&")){String[] p=pair.split("=",2);result.put(URLDecoder.decode(p[0],StandardCharsets.UTF_8),p.length==2?URLDecoder.decode(p[1],StandardCharsets.UTF_8):"");}return result;}
    private static void reply(Socket socket,int status,String message)throws IOException{byte[] body=("<!doctype html><meta charset='utf-8'><title>Study Agent</title><p>"+message+"</p>").getBytes(StandardCharsets.UTF_8);var out=socket.getOutputStream();out.write(("HTTP/1.1 "+status+" OK\r\nContent-Type: text/html; charset=utf-8\r\nCache-Control: no-store\r\nConnection: close\r\nContent-Length: "+body.length+"\r\n\r\n").getBytes(StandardCharsets.US_ASCII));out.write(body);out.flush();}
}

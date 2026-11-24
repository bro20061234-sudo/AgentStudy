package com.studyagent.calendar;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.net.*;
import java.net.http.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

class GoogleOAuthTest {
    @TempDir Path temp;
    Path client()throws Exception{Path file=temp.resolve("client-input.json");Files.writeString(file,"{\"installed\":{\"client_id\":\"test.apps.googleusercontent.com\",\"client_secret\":\"test-only\",\"auth_uri\":\"https://untrusted.invalid/\"}}");return file;}
    @Test void browserFlowUsesPkceRejectsWrongStateAndPersistsOfflineTokenWithoutRealGoogleRequests()throws Exception{
        AtomicReference<Map<String,String>> authorization=new AtomicReference<>();AtomicInteger exchanges=new AtomicInteger();
        var oauth=new GoogleOAuth(temp.resolve("google"),data->{
            exchanges.incrementAndGet();assertEquals("test-code",data.get("code"));assertEquals("authorization_code",data.get("grant_type"));
            String challenge=Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(data.get("code_verifier").getBytes(StandardCharsets.US_ASCII)));assertEquals(authorization.get().get("code_challenge"),challenge);
            JsonObject token=new JsonObject();token.addProperty("access_token","fake-access");token.addProperty("refresh_token","fake-refresh");token.addProperty("expires_in",3600);return token;
        });oauth.importClient(client());AtomicReference<CompletableFuture<Void>> browser=new AtomicReference<>();
        oauth.authorize(uri->{
            assertEquals("accounts.google.com",uri.getHost());var query=GoogleOAuth.parseQuery(uri.getRawQuery());authorization.set(query);assertEquals(GoogleOAuth.SCOPE,query.get("scope"));assertEquals("S256",query.get("code_challenge_method"));assertTrue(query.get("redirect_uri").startsWith("http://127.0.0.1:"));
            browser.set(CompletableFuture.runAsync(()->{try{
                var http=HttpClient.newHttpClient();String redirect=query.get("redirect_uri");
                var wrong=http.send(HttpRequest.newBuilder(URI.create(redirect+"?code=bad&state=wrong")).GET().build(),HttpResponse.BodyHandlers.ofString());assertEquals(400,wrong.statusCode());
                var good=http.send(HttpRequest.newBuilder(URI.create(redirect+"?code=test-code&state="+query.get("state"))).GET().build(),HttpResponse.BodyHandlers.ofString());assertEquals(200,good.statusCode());
            }catch(Exception e){throw new RuntimeException(e);}}));
        });browser.get().get(5,TimeUnit.SECONDS);assertEquals(1,exchanges.get());assertTrue(oauth.connected());assertEquals("fake-access",oauth.accessToken());
        assertTrue(new GoogleOAuth(temp.resolve("google")).connected());
    }
    @Test void cancellingConsentDoesNotStoreAToken()throws Exception{
        var oauth=new GoogleOAuth(temp.resolve("cancel"),data->{throw new AssertionError("No token exchange after denied consent");});oauth.importClient(client());AtomicReference<CompletableFuture<Void>> browser=new AtomicReference<>();
        assertThrows(java.io.IOException.class,()->oauth.authorize(uri->{var query=GoogleOAuth.parseQuery(uri.getRawQuery());browser.set(CompletableFuture.runAsync(()->{try{HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(query.get("redirect_uri")+"?error=access_denied&state="+query.get("state"))).GET().build(),HttpResponse.BodyHandlers.ofString());}catch(Exception e){throw new RuntimeException(e);}}));}));
        browser.get().get(5,TimeUnit.SECONDS);assertFalse(oauth.connected());assertFalse(Files.exists(temp.resolve("cancel/token.json")));
    }
    @Test void webClientOrApiKeyFileIsRejectedBeforeWritingSettings()throws Exception{
        Path file=temp.resolve("web.json");Files.writeString(file,"{\"web\":{\"client_id\":\"test.apps.googleusercontent.com\"}}");var oauth=new GoogleOAuth(temp.resolve("bad"));
        assertThrows(java.io.IOException.class,()->oauth.importClient(file));assertFalse(oauth.configured());assertFalse(oauth.connected());
    }
}

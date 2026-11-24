package com.studyagent.calendar;

import com.google.gson.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.util.*;

/** Atomic local credentials storage, readable only by the current OS user where supported. */
final class PrivateJson {
    private PrivateJson(){}
    static JsonObject read(Path file)throws IOException{if(!Files.isRegularFile(file))return new JsonObject();try{return JsonParser.parseString(Files.readString(file,StandardCharsets.UTF_8)).getAsJsonObject();}catch(RuntimeException e){throw new IOException("Tệp cấu hình Google không hợp lệ. Hãy chọn lại hoặc kết nối lại.",e);}}
    static void write(Path file,JsonObject json)throws IOException{
        Files.createDirectories(file.getParent());Path temporary=Files.createTempFile(file.getParent(),"google-",".tmp");
        try{
            var acl=Files.getFileAttributeView(temporary,AclFileAttributeView.class);
            if(acl!=null){var owner=Files.getOwner(temporary);acl.setAcl(List.of(AclEntry.newBuilder().setType(AclEntryType.ALLOW).setPrincipal(owner).setPermissions(EnumSet.allOf(AclEntryPermission.class)).build()));}
            else if(Files.getFileAttributeView(temporary,PosixFileAttributeView.class)!=null)Files.setPosixFilePermissions(temporary,PosixFilePermissions.fromString("rw-------"));
            Files.writeString(temporary,json.toString(),StandardCharsets.UTF_8);
            try{Files.move(temporary,file,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException e){Files.move(temporary,file,StandardCopyOption.REPLACE_EXISTING);}
        }finally{Files.deleteIfExists(temporary);}
    }
    static String text(JsonObject object,String key){return object.has(key)&&!object.get(key).isJsonNull()?object.get(key).getAsString():"";}
}

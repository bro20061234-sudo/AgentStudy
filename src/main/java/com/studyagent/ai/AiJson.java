package com.studyagent.ai;

import com.google.gson.*;

/** Repairs only invalid string escapes from generated math/SQL; never invents missing objects. */
public final class AiJson {
    private AiJson() {}
    public static JsonArray array(String raw) {
        try{return JsonParser.parseString(raw).getAsJsonArray();}
        catch(JsonParseException e){return JsonParser.parseString(escapeStrings(raw)).getAsJsonArray();}
    }
    private static String escapeStrings(String raw){
        StringBuilder out=new StringBuilder();boolean string=false;
        for(int i=0;i<raw.length();i++){
            char c=raw.charAt(i);
            if(c=='"'){string=!string;out.append(c);continue;}
            if(string&&c=='\\'&&i+1<raw.length()){
                char next=raw.charAt(++i);
                if("\"\\/bfnrt".indexOf(next)>=0){out.append('\\').append(next);continue;}
                if(next=='u'&&i+4<raw.length()&&raw.substring(i+1,i+5).matches("[0-9a-fA-F]{4}")){
                    out.append("\\u").append(raw,i+1,i+5);i+=4;continue;
                }
                out.append("\\\\").append(next);continue;
            }
            if(string&&c<32){out.append(String.format("\\u%04x",(int)c));continue;}
            out.append(c);
        }
        return out.toString();
    }
}

package ru.astra.assistant;
import java.util.Locale;
import java.util.regex.Pattern;
final class VoiceGate {
    private static final Pattern WAKE=Pattern.compile("^(?:привет )?астра(?: |$)");
    private static final Pattern ALERT=Pattern.compile("^(?:внимание )?(?:камера|камеры|камер нет|муляж|опасный поворот|пешеходный переход|контроль скорости|ограничение скорости|дорожные работы|через [0-9]+ метров)(?: |$)");
    static String normalize(String s){
        return s==null?"":s.toLowerCase(Locale.ROOT).replace('ё','е').replaceAll("[^\\p{L}\\p{N}+() -]"," ").replaceAll("\\s+"," ").trim();
    }
    static boolean isRoadAlert(String s){return ALERT.matcher(normalize(s)).find();}
    static String extract(String raw, boolean awaiting){
        String s=normalize(raw);
        if(s.isEmpty()||isRoadAlert(s))return null;
        java.util.regex.Matcher m=WAKE.matcher(s);
        if(m.find()){
            String cmd=s.substring(m.end()).trim();
            return isRoadAlert(cmd)?null:cmd;
        }
        return awaiting?s:null;
    }
}

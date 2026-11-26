package ru.astra.assistant;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.widget.EditText;
import android.widget.LinearLayout;
import org.json.*;
import java.net.URL;
import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

final class AstraBrain {
    interface Reply { void accept(String text); }
    private static final ExecutorService worker = Executors.newSingleThreadExecutor();
    private static final Handler main = new Handler(Looper.getMainLooper());
    private static SharedPreferences prefs(Context c) { return c.getSharedPreferences("astra_private", Context.MODE_PRIVATE); }

    static void settings(Activity a) {
        SharedPreferences p=prefs(a);
        LinearLayout root=new LinearLayout(a); root.setOrientation(LinearLayout.VERTICAL);
        EditText host=new EditText(a); host.setHint("HTTPS-адрес Ollama в Tailscale"); host.setSingleLine(true); host.setText(p.getString("host",""));
        EditText model=new EditText(a); model.setHint("Имя установленной модели Ollama"); model.setSingleLine(true); model.setText(p.getString("model",""));
        root.addView(host); root.addView(model);
        new AlertDialog.Builder(a).setTitle("Локальный ИИ").setMessage("При сохранении неизвестные голосовые команды отправляются на указанный сервер. Оставьте адрес пустым для отключения. Память хранится только на телефоне.")
          .setView(root).setNegativeButton("Отмена",null).setPositiveButton("Сохранить",(d,w)->{
            String h=host.getText().toString().trim(), m=model.getText().toString().trim();
            try {
                if(!h.isEmpty()){URL u=new URL(h); if(!"https".equals(u.getProtocol())||u.getHost().isEmpty()||u.getUserInfo()!=null||u.getQuery()!=null||u.getRef()!=null||!(u.getPath().isEmpty()||"/".equals(u.getPath()))||m.isEmpty())throw new IllegalArgumentException();}
                p.edit().putString("host",h).putString("model",m).apply();
            }catch(Exception e){new AlertDialog.Builder(a).setMessage("Нужен HTTPS-адрес без пути и имя модели. Настройки не изменены.").setPositiveButton("OK",null).show();}
          }).show();
    }

    static void reply(Context c,String raw,Reply cb) {
        String s=VoiceGate.normalize(raw);
        SharedPreferences p=prefs(c);
        if(s.startsWith("запомни ")){
            String old=p.getString("notes","");
            String note=raw.trim().replaceFirst("(?iu)^запомни\\s+","");
            if(note.length()>500||old.length()+note.length()>8000){cb.accept("Память заполнена или запись слишком длинная");return;}
            p.edit().putString("notes",old.isEmpty()?note:old+"\n"+note).apply();
            cb.accept("Запомнила");return;
        }
        if(s.equals("что ты запомнила")||s.equals("что ты помнишь")){
            String n=p.getString("notes","");cb.accept(n.isEmpty()?"Пока ничего не запомнила":n);return;
        }
        if(s.equals("очисти память")){cb.accept("Для удаления всех заметок скажите подтверждаю очистку памяти");return;}
        if(s.equals("подтверждаю очистку памяти")){p.edit().remove("notes").apply();cb.accept("Локальные заметки удалены");return;}
        String host=p.getString("host",""), model=p.getString("model","");
        if(host.isEmpty()||model.isEmpty()){cb.accept("Локальный ИИ не настроен. Команда не поддерживается");return;}
        if(raw.length()>2000){cb.accept("Слишком длинная команда");return;}
        worker.execute(()->{
            String answer;
            HttpsURLConnection con=null;
            try {
                con=(HttpsURLConnection)new URL(host.replaceAll("/+$","")+"/api/chat").openConnection();
                con.setInstanceFollowRedirects(false);con.setConnectTimeout(5000);con.setReadTimeout(30000);
                con.setRequestMethod("POST");con.setDoOutput(true);con.setRequestProperty("Content-Type","application/json");
                JSONObject req=new JSONObject().put("model",model).put("stream",false)
                  .put("options",new JSONObject().put("num_predict",300))
                  .put("messages",new JSONArray()
                    .put(new JSONObject().put("role","system").put("content","Ты голосовой помощник Астра. Отвечай кратко по-русски. Не утверждай, что управляешь телефоном или выполнила действие."))
                    .put(new JSONObject().put("role","user").put("content",raw)));
                byte[] bytes=req.toString().getBytes(StandardCharsets.UTF_8);
                try(OutputStream out=con.getOutputStream()){out.write(bytes);}
                if(con.getResponseCode()!=200)throw new IOException("HTTP");
                try(InputStream in=con.getInputStream();ByteArrayOutputStream buf=new ByteArrayOutputStream()){
                    byte[] b=new byte[4096];int n;
                    while((n=in.read(b))!=-1){if(buf.size()+n>262144)throw new IOException("size");buf.write(b,0,n);}
                    answer=new JSONObject(new String(buf.toByteArray(),StandardCharsets.UTF_8)).getJSONObject("message").getString("content").trim();
                }
                if(answer.isEmpty())answer="Сервер вернул пустой ответ";
                if(answer.length()>2000)answer=answer.substring(0,2000);
            }catch(Exception e){answer="Не удалось получить ответ локального ИИ. Проверь подключение к серверу и модель.";}
            finally{if(con!=null)con.disconnect();}
            String result=answer;main.post(()->cb.accept(result));
        });
    }
}

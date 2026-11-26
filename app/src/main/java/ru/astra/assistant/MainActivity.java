package ru.astra.assistant;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.speech.*;
import android.speech.tts.TextToSpeech;
import android.view.KeyEvent;
import android.widget.*;
import java.util.*;

public class MainActivity extends Activity {
    private TextView status;
    private EditText input;
    private SpeechRecognizer speech;
    private TextToSpeech tts;
    private boolean ttsReady;
    private PhoneCommands phone;
    private boolean listening;
    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        phone=new PhoneCommands(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(1); root.setPadding(32,64,32,32);
        status = new TextView(this); status.setTextSize(22); status.setText("Астра • режим разработки");
        root.addView(status);
        input = new EditText(this); input.setHint("Введите команду"); root.addView(input);
        button(root,"Настройки локального ИИ",v -> AstraBrain.settings(this));
        button(root,"Разрешения звонков и контактов",v -> requestPermissions(new String[]{Manifest.permission.READ_CONTACTS,Manifest.permission.CALL_PHONE},5));
        button(root,"Слушать",v -> {stopService(new Intent(this,VoiceService.class));listen();});
        button(root,"Включить фоновую Астру",v -> startBackgroundVoice());
        button(root,"Выключить фоновую Астру",v -> {stopService(new Intent(this,VoiceService.class));status.setText("Фоновый режим выключен");});
        button(root,"Выполнить",v -> command(input.getText().toString()));
        button(root,"Навигатор",v -> launch("ru.yandex.yandexnavi"));
        button(root,"Яндекс Музыка",v -> launch("ru.yandex.music"));
        button(root,"Остановить",v -> { if(speech!=null) speech.cancel(); listening=false; if(tts!=null)tts.stop(); phone.cancel(); stopService(new Intent(this,VoiceService.class)); status.setText("Остановлено"); });
        setContentView(root);
        tts = new TextToSpeech(this, s -> { if(s==TextToSpeech.SUCCESS && tts!=null) ttsReady=tts.setLanguage(new Locale("ru","RU"))>=0; });
        if(SpeechRecognizer.isRecognitionAvailable(this)) {
            speech=SpeechRecognizer.createSpeechRecognizer(this);
            speech.setRecognitionListener(new RecognitionListener(){
                public void onReadyForSpeech(Bundle p){status.setText("Слушаю");}
                public void onBeginningOfSpeech(){}
                public void onRmsChanged(float f){}
                public void onBufferReceived(byte[] b){}
                public void onEndOfSpeech(){}
                public void onError(int e){listening=false;status.setText("Речь не распознана. Код "+e);}
                public void onResults(Bundle b){listening=false; ArrayList<String> r=b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION); if(r!=null&&!r.isEmpty()&&!VoiceGate.isRoadAlert(r.get(0)))command(r.get(0));}
                public void onPartialResults(Bundle b){}
                public void onEvent(int e,Bundle b){}
            });
        }
    }
    private void startBackgroundVoice(){
        if(android.os.Build.VERSION.SDK_INT<31 || !SpeechRecognizer.isOnDeviceRecognitionAvailable(this)){
            say("Локальное распознавание недоступно");return;
        }
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},3);return;
        }
        if(android.os.Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},4);return;
        }
        if(speech!=null)speech.cancel();listening=false;if(tts!=null)tts.stop();
        try{startForegroundService(new Intent(this,VoiceService.class));status.setText("Фоновый режим запрошен");}
        catch(RuntimeException e){say("Не удалось запустить фоновый режим");}
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){
        super.onRequestPermissionsResult(request,permissions,results);
        if((request==3||request==4)&&results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED)startBackgroundVoice();
    }
    private void button(LinearLayout root,String title,android.view.View.OnClickListener l){Button b=new Button(this);b.setText(title);b.setOnClickListener(l);root.addView(b);}
    private void say(String s){status.setText(s);if(ttsReady&&tts!=null)tts.speak(s,TextToSpeech.QUEUE_FLUSH,null,UUID.randomUUID().toString());}
    private void listen(){
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},1);return;}
        if(speech==null){say("Распознавание речи недоступно");return;}
        if(listening)return; if(tts!=null)tts.stop();
        Intent i=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE,"ru-RU");
        i.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE,true);
        listening=true; speech.startListening(i);
    }
    private void launch(String pkg){Intent i=getPackageManager().getLaunchIntentForPackage(pkg);if(i!=null){startActivity(i);}else{say("Приложение не установлено");}}
    private void media(int code){AudioManager a=(AudioManager)getSystemService(AUDIO_SERVICE);a.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,code));a.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,code));}
    private void command(String raw){
        String s=raw.toLowerCase(new Locale("ru")).trim().replaceFirst("^(привет[, ]+)?астра[, ]*","");
        String phoneReply=phone.handle(s);
        if(phoneReply!=null){say(phoneReply);return;}
        if(s.startsWith("маршрут до ")||s.startsWith("поехали в ")){String q=s.substring(s.startsWith("маршрут до ")?11:10);Intent i=new Intent(Intent.ACTION_VIEW,Uri.parse("yandexnavi://build_route_on_map?text="+Uri.encode(q)));i.setPackage("ru.yandex.yandexnavi");try{startActivity(i);}catch(Exception e){launch("ru.yandex.yandexnavi");}return;}
        if(s.contains("навигатор")){launch("ru.yandex.yandexnavi");return;}
        if(s.contains("следующий трек")){media(KeyEvent.KEYCODE_MEDIA_NEXT);return;}
        if(s.contains("предыдущий трек")){media(KeyEvent.KEYCODE_MEDIA_PREVIOUS);return;}
        if(s.contains("пауза")){media(KeyEvent.KEYCODE_MEDIA_PAUSE);return;}
        if(s.equals("продолжить музыку")){media(KeyEvent.KEYCODE_MEDIA_PLAY);return;}
        if(s.contains("музык")){launch("ru.yandex.music");return;}
        AstraBrain.reply(this,s,answer->{if(!isFinishing()&&!isDestroyed())say(answer);});
    }
    @Override protected void onDestroy(){if(speech!=null)speech.destroy();if(tts!=null)tts.shutdown();super.onDestroy();}
}

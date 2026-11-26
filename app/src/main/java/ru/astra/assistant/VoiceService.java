package ru.astra.assistant;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.net.*;
import android.os.*;
import android.speech.*;
import android.speech.tts.*;
import android.view.KeyEvent;
import java.util.*;

public class VoiceService extends Service implements RecognitionListener {
    private static final String CHANNEL="astra_voice", STOP="ru.astra.assistant.STOP";
    private final Handler handler=new Handler(Looper.getMainLooper());
    private SpeechRecognizer recognizer;
    private TextToSpeech tts;
    private boolean ready,active,speaking;
    private long commandUntil;
    private int failures;
    private final Runnable listenTask=()->listen();
    @Override public IBinder onBind(Intent i){return null;}
    @Override public void onCreate(){
        super.onCreate();
        getSystemService(NotificationManager.class).createNotificationChannel(new NotificationChannel(CHANNEL,"Голосовой режим Астры",NotificationManager.IMPORTANCE_LOW));
        tts=new TextToSpeech(this,status->{
            if(status==TextToSpeech.SUCCESS && tts!=null){
                ready=tts.setLanguage(new Locale("ru","RU"))>=0;
                tts.setOnUtteranceProgressListener(new UtteranceProgressListener(){
                    public void onStart(String id){}
                    public void onDone(String id){handler.post(()->finishSpeaking());}
                    public void onError(String id){handler.post(()->finishSpeaking());}
                });
            }
        });
    }
    private Notification notification(){
        int flags=PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE;
        PendingIntent open=PendingIntent.getActivity(this,1,new Intent(this,MainActivity.class),flags);
        PendingIntent stop=PendingIntent.getService(this,2,new Intent(this,VoiceService.class).setAction(STOP),flags);
        return new Notification.Builder(this,CHANNEL).setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Астра • голосовой режим").setContentText("Ожидание фразы «Астра»").setOngoing(true)
            .setContentIntent(open).addAction(new Notification.Action.Builder(android.R.drawable.ic_menu_close_clear_cancel,"Остановить",stop).build()).build();
    }
    @Override public int onStartCommand(Intent i,int flags,int startId){
        if(i!=null&&STOP.equals(i.getAction())){stopSelf();return START_NOT_STICKY;}
        if(active)return START_NOT_STICKY;
        startForeground(41,notification());
        if(Build.VERSION.SDK_INT<31||checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED
                ||!SpeechRecognizer.isOnDeviceRecognitionAvailable(this)){stopSelf();return START_NOT_STICKY;}
        try{
            recognizer=SpeechRecognizer.createOnDeviceSpeechRecognizer(this);
            recognizer.setRecognitionListener(this);active=true;schedule(300);
        }catch(RuntimeException e){stopSelf();}
        return START_NOT_STICKY;
    }
    private void schedule(long delay){handler.removeCallbacks(listenTask);if(active&&!speaking)handler.postDelayed(listenTask,delay);}
    private void listen(){
        if(!active||speaking||recognizer==null)return;
        Intent i=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE,"ru-RU");
        i.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE,true);
        try{recognizer.startListening(i);}catch(RuntimeException e){retry();}
    }
    private void retry(){if(++failures>=8){stopSelf();return;}schedule(Math.min(10000,700L*failures));}
    public void onReadyForSpeech(Bundle b){}
    public void onBeginningOfSpeech(){}
    public void onRmsChanged(float f){}
    public void onBufferReceived(byte[] b){}
    public void onEndOfSpeech(){}
    public void onPartialResults(Bundle b){}
    public void onEvent(int t,Bundle b){}
    public void onError(int code){
        if(!active||speaking)return;
        if(code==SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS||code==SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED||code==SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE){stopSelf();return;}
        if(code==SpeechRecognizer.ERROR_NO_MATCH||code==SpeechRecognizer.ERROR_SPEECH_TIMEOUT){failures=0;schedule(500);}
        else retry();
    }
    public void onResults(Bundle b){
        if(!active||speaking)return;
        failures=0;
        ArrayList<String> words=b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        String cmd=words==null||words.isEmpty()?null:VoiceGate.extract(words.get(0),SystemClock.elapsedRealtime()<commandUntil);
        if(cmd==null){schedule(400);return;}
        if(cmd.isEmpty()){commandUntil=SystemClock.elapsedRealtime()+10000;say("Слушаю");return;}
        commandUntil=0;execute(cmd);
    }
    private void execute(String s){
        switch(s){
            case "следующий трек": media(KeyEvent.KEYCODE_MEDIA_NEXT);break;
            case "предыдущий трек": media(KeyEvent.KEYCODE_MEDIA_PREVIOUS);break;
            case "пауза": case "пауза музыки": media(KeyEvent.KEYCODE_MEDIA_PAUSE);break;
            case "продолжить музыку": media(KeyEvent.KEYCODE_MEDIA_PLAY);break;
            case "проверь vpn":case "проверь впн":case "проверь подключение vpn":case "проверь подключение впн":
                ConnectivityManager cm=getSystemService(ConnectivityManager.class);
                boolean vpn=false;
                for(Network n:cm.getAllNetworks()){
                    NetworkCapabilities nc=cm.getNetworkCapabilities(n);
                    if(nc!=null&&nc.hasTransport(NetworkCapabilities.TRANSPORT_VPN)){vpn=true;break;}
                }
                say(vpn?"VPN активен. Тип VPN не определен":"VPN не обнаружен");return;
            case "отмена":schedule(300);return;
            case "останови астру":stopSelf();return;
            default:say("Эта команда пока недоступна в фоновом режиме");return;
        }
        schedule(400);
    }
    private void media(int code){
        AudioManager a=getSystemService(AudioManager.class);
        a.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,code));
        a.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,code));
    }
    private void say(String s){
        if(!ready||tts==null){schedule(400);return;}
        speaking=true;
        if(tts.speak(s,TextToSpeech.QUEUE_FLUSH,null,UUID.randomUUID().toString())==TextToSpeech.ERROR)finishSpeaking();
    }
    private void finishSpeaking(){if(!active)return;speaking=false;schedule(450);}
    @Override public void onDestroy(){
        active=false;handler.removeCallbacksAndMessages(null);
        if(recognizer!=null){recognizer.cancel();recognizer.destroy();}
        if(tts!=null){tts.stop();tts.shutdown();}
        stopForeground(STOP_FOREGROUND_REMOVE);super.onDestroy();
    }
}

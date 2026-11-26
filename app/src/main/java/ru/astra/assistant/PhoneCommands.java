package ru.astra.assistant;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.ContactsContract;
import android.telecom.TelecomManager;
import java.util.LinkedHashMap;

final class PhoneCommands {
    private final Context context;
    private String pending;
    private long expires;
    private String lastNumber;
    PhoneCommands(Context c){context=c;}
    boolean awaiting(){return pending!=null&&SystemClock.elapsedRealtime()<expires;}
    void cancel(){pending=null;expires=0;}
    String handle(String raw){
        String s=VoiceGate.normalize(raw);
        if(s.equals("отмена")){cancel();return "Отменено";}
        if(s.equals("подтверждаю звонок")||s.equals("подтверждаю")){
            if(!awaiting()){cancel();return "Нет звонка для подтверждения";}
            String number=pending;cancel();
            if(context.checkSelfPermission(Manifest.permission.CALL_PHONE)!=PackageManager.PERMISSION_GRANTED)
                return "На стоянке разрешите Астре телефонные звонки";
            try {
                TelecomManager telecom=context.getSystemService(TelecomManager.class);
                if(telecom==null)return "Телефонная служба недоступна";
                telecom.placeCall(Uri.fromParts("tel",number,null),new Bundle());
                lastNumber=number;
                return "Запрос на звонок отправлен";
            }catch(RuntimeException e){return "Не удалось начать звонок";}
        }
        cancel();
        if(s.equals("прими звонок")||s.equals("ответь на звонок")||s.equals("ответить на звонок"))
            return controlCall(true);
        if(s.equals("заверши звонок")||s.equals("заверши разговор")||s.equals("положи трубку"))
            return controlCall(false);
        if(s.equals("повтори звонок")||s.equals("набери последний номер")){
            return lastNumber==null?"В этой сессии нет предыдущего номера":prepare("последний номер Астры",lastNumber);
        }
        if(s.startsWith("позвони ")){
            String name=s.substring(8).trim();
            if(name.matches("[+0-9 ()-]{3,25}"))return prepare(name,name);
            if(context.checkSelfPermission(Manifest.permission.READ_CONTACTS)!=PackageManager.PERMISSION_GRANTED)
                return "На стоянке разрешите Астре доступ к контактам";
            LinkedHashMap<String,String> matches=new LinkedHashMap<>(),exact=new LinkedHashMap<>();
            try(Cursor c=context.getContentResolver().query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                new String[]{ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,ContactsContract.CommonDataKinds.Phone.NUMBER},null,null,null)){
                if(c!=null)while(c.moveToNext()){
                    String label=c.getString(0),number=c.getString(1);
                    if(label==null||number==null)continue;
                    String normalized=VoiceGate.normalize(label), key=number.replaceAll("[^+0-9]","");
                    if(!key.matches("[+]?[0-9]{3,20}"))continue;
                    if(normalized.equals(name))exact.put(key,label);
                    if(normalized.contains(name))matches.put(key,label);
                }
            }catch(RuntimeException e){return "Не удалось прочитать контакты";}
            if(!exact.isEmpty())matches=exact;
            if(matches.isEmpty())return "Контакт не найден";
            if(matches.size()>1)return "Найдено несколько номеров. Назовите полное имя или номер";
            java.util.Map.Entry<String,String> m=matches.entrySet().iterator().next();
            return prepare(m.getValue(),m.getKey());
        }
        AudioManager a=context.getSystemService(AudioManager.class);
        if(s.equals("громче")||s.equals("тише")||s.equals("увеличь громкость")||s.equals("уменьши громкость")){
            if(a==null)return "Управление звуком недоступно";
            try{
                a.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                    s.equals("громче")||s.equals("увеличь громкость")?AudioManager.ADJUST_RAISE:AudioManager.ADJUST_LOWER,0);
                return "Громкость музыки изменена";
            }catch(RuntimeException e){return "Не удалось изменить громкость";}
        }
        if(s.equals("проверь блютуз")||s.equals("проверь bluetooth")||s.equals("проверь звук автомобиля")){
            if(a==null)return "Аудиослужба недоступна";
            try{
                for(AudioDeviceInfo d:a.getDevices(AudioManager.GET_DEVICES_OUTPUTS)){
                    int type=d.getType();
                    if(type==AudioDeviceInfo.TYPE_BLUETOOTH_A2DP||type==AudioDeviceInfo.TYPE_BLUETOOTH_SCO
                        ||(android.os.Build.VERSION.SDK_INT>=31&&(type==AudioDeviceInfo.TYPE_BLE_HEADSET||type==AudioDeviceInfo.TYPE_BLE_SPEAKER)))
                        return "Обнаружен доступный аудиовыход Bluetooth. Активный маршрут звука не проверен";
                }
                return "Аудиовыход Bluetooth не обнаружен";
            }catch(RuntimeException e){return "Не удалось проверить аудиовыходы";}
        }
        return null;
    }
    @SuppressWarnings("deprecation")
    private String controlCall(boolean answer){
        if(context.checkSelfPermission(Manifest.permission.ANSWER_PHONE_CALLS)!=PackageManager.PERMISSION_GRANTED
            ||context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE)!=PackageManager.PERMISSION_GRANTED)
            return "На стоянке разрешите Астре управление звонками и состояние телефона";
        TelecomManager telecom=context.getSystemService(TelecomManager.class);
        if(telecom==null)return "Телефонная служба недоступна";
        try{
            if(answer){
                android.telephony.TelephonyManager telephony=context.getSystemService(android.telephony.TelephonyManager.class);
                if(telephony==null||telephony.getCallState()!=android.telephony.TelephonyManager.CALL_STATE_RINGING)return "Входящий звонок не обнаружен";
                telecom.acceptRingingCall(android.telecom.VideoProfile.STATE_AUDIO_ONLY);
                return "Запрос на приём звонка отправлен";
            }
            if(android.os.Build.VERSION.SDK_INT<28)return "Завершение звонков требует Android 9 или новее";
            if(!telecom.isInCall())return "Активных звонков нет";
            return telecom.endCall()?"Звонок завершён":"Не удалось завершить звонок";
        }catch(RuntimeException e){return "Android не разрешил управление звонком";}
    }
    private String prepare(String name,String raw){
        String n=raw.replaceAll("[^+0-9]","");
        if(!n.matches("[+]?[0-9]{3,20}"))return "Некорректный номер";
        pending=n;expires=SystemClock.elapsedRealtime()+20000;
        return "Позвонить: "+name+"? Скажите подтверждаю звонок или отмена";
    }
}

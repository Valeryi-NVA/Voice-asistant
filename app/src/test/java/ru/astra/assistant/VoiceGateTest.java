package ru.astra.assistant;
import org.junit.Test;
import static org.junit.Assert.*;
public class VoiceGateTest {
    @Test public void alerts(){
        for(String s:new String[]{"Камера на пятьдесят, 500 метров","Камер нет","Опасный поворот, 500 метров","Пешеходный переход 150 метров","Муляж, 300 метров"})
            assertNull(VoiceGate.extract(s,true));
    }
    @Test public void wake(){
        assertNull(VoiceGate.extract("Следующий трек",false));
        assertEquals("следующий трек",VoiceGate.extract("Астра, следующий трек",false));
        assertEquals("",VoiceGate.extract("Привет, Астра!",false));
        assertEquals("пауза",VoiceGate.extract("Пауза",true));
        assertNull(VoiceGate.extract("Астранавт",false));
        assertNull(VoiceGate.extract("Астра, камера 500 метров",false));
    }
}

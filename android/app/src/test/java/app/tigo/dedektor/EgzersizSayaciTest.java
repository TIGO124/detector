package app.tigo.dedektor;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class EgzersizSayaciTest {

    private static void tekrarla(EgzersizSayaci s, String durum, int kare) {
        for (int i = 0; i < kare; i++) s.guncelle(durum, 0.9f);
    }

    @Test
    public void squatSayar() {
        EgzersizSayaci s = new EgzersizSayaci();
        tekrarla(s, "Ayakta", 6);
        for (int i = 0; i < 3; i++) {
            tekrarla(s, "Squat", 6);
            tekrarla(s, "Ayakta", 6);
        }
        assertEquals(3, s.sayi());
    }

    @Test
    public void tekKarelikHataSaymaz() {
        EgzersizSayaci s = new EgzersizSayaci();
        tekrarla(s, "Ayakta", 6);
        s.guncelle("Squat", 0.9f);
        tekrarla(s, "Ayakta", 6);
        assertEquals(0, s.sayi());
    }

    @Test
    public void dusukOlasilikSayilmaz() {
        EgzersizSayaci s = new EgzersizSayaci();
        tekrarla(s, "Ayakta", 6);
        for (int i = 0; i < 6; i++) s.guncelle("Squat", 0.4f);
        tekrarla(s, "Ayakta", 6);
        assertEquals(0, s.sayi());
    }

    @Test
    public void sinavVeTurDegisimi() {
        EgzersizSayaci s = new EgzersizSayaci();
        s.sec(EgzersizSayaci.Tur.SINAV);
        tekrarla(s, "Şınav yukarı", 6);
        tekrarla(s, "Şınav aşağı", 6);
        tekrarla(s, "Şınav yukarı", 6);
        assertEquals(1, s.sayi());
        // Squat duruşları şınav saymaz; tür değişince sayaç sıfırlanır.
        tekrarla(s, "Squat", 6);
        tekrarla(s, "Ayakta", 6);
        assertEquals(1, s.sayi());
        s.sec(EgzersizSayaci.Tur.ZIPLAMA);
        assertEquals(0, s.sayi());
    }
}

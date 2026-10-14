package app.tigo.dedektor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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

    @Test
    public void plankSaniyeSayar() {
        EgzersizSayaci s = new EgzersizSayaci();
        s.sec(EgzersizSayaci.Tur.PLANK);
        long t = 0;
        int artis = 0;
        // 3 kare çoğunluk oluşana kadar sayılmaz; sonra 100 ms'lik kareler.
        for (int i = 0; i < 43; i++, t += 100) if (s.guncelle("Şınav yukarı", 0.9f, t)) artis++;
        assertEquals(4, s.sayi());
        assertEquals(4, artis);
        assertTrue(s.tutuyor());
    }

    @Test
    public void plankBirakilincaDurur() {
        EgzersizSayaci s = new EgzersizSayaci();
        s.sec(EgzersizSayaci.Tur.PLANK);
        long t = 0;
        for (int i = 0; i < 25; i++, t += 100) s.guncelle("Şınav aşağı", 0.9f, t);
        int once = s.sayi();
        for (int i = 0; i < 40; i++, t += 100) s.guncelle("Ayakta", 0.9f, t);
        assertFalse(s.tutuyor());
        // Çoğunluk değişene kadar birkaç kare daha sayılabilir; ayakta geçen 4 saniye sayılmaz.
        assertTrue(s.sayi() <= once + 1);
    }

    @Test
    public void plankUzunBoslukEklenmez() {
        EgzersizSayaci s = new EgzersizSayaci();
        s.sec(EgzersizSayaci.Tur.PLANK);
        for (int i = 0; i < 5; i++) s.guncelle("Şınav yukarı", 0.9f, i * 100);
        // Kişi bir süre kadraj dışı (kare yok), sonra geri döndü: aradaki 30 saniye sayılmaz.
        s.guncelle("Şınav yukarı", 0.9f, 30_000);
        assertTrue(s.sayi() <= 1);
    }

    @Test
    public void vucutKaybolunca() {
        EgzersizSayaci s = new EgzersizSayaci();
        s.sec(EgzersizSayaci.Tur.PLANK);
        for (int i = 0; i < 20; i++) s.guncelle("Şınav yukarı", 0.9f, i * 100);
        for (int i = 20; i < 60; i++) s.guncelle(null, 0f, i * 100);
        assertFalse(s.tutuyor());
        assertEquals(null, s.durum());
        assertTrue(s.sayi() <= 2);
    }

    @Test
    public void hedefBirKezTamamlanir() {
        EgzersizSayaci s = new EgzersizSayaci();
        s.hedef(2);
        tekrarla(s, "Ayakta", 6);
        tekrarla(s, "Squat", 6);
        tekrarla(s, "Ayakta", 6);
        assertFalse(s.hedefeUlasti());
        tekrarla(s, "Squat", 6);
        tekrarla(s, "Ayakta", 6);
        assertTrue(s.hedefeUlasti());
        assertFalse(s.hedefeUlasti());
        // Sıfırlanınca hedef yeniden kutlanabilir.
        s.sifirla();
        assertFalse(s.hedefeUlasti());
    }
}

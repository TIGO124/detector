package app.tigo.dedektor;

import android.content.SharedPreferences;
import java.util.Calendar;
import java.util.HashSet;
import java.util.Set;

/**
 * Egzersiz geçmişi: her gün her tür için yapılan tekrar (plankta saniye). Gün, yerel saate göre
 * 1970'ten beri geçen gün sayısıdır; anahtar "gün_TÜR". 60 günden eski kayıtlar silinir.
 * Depo arayüzü sayesinde testte bellekte, uygulamada SharedPreferences'ta tutulur.
 */
final class EgzersizGecmisi {

    /** Anahtar-değer deposu. */
    interface Depo {
        int oku(String anahtar);

        void yaz(String anahtar, int deger);

        Set<String> anahtarlar();

        void sil(String anahtar);
    }

    static final int GUN_SAYISI = 7;
    private static final int SAKLAMA_GUNU = 60;

    private final Depo depo;

    EgzersizGecmisi(Depo depo) {
        this.depo = depo;
    }

    /** SharedPreferences ile kalıcı geçmiş. */
    static EgzersizGecmisi tercihlerle(SharedPreferences t) {
        return new EgzersizGecmisi(new Depo() {
            @Override
            public int oku(String anahtar) {
                return t.getInt(anahtar, 0);
            }

            @Override
            public void yaz(String anahtar, int deger) {
                t.edit().putInt(anahtar, deger).apply();
            }

            @Override
            public Set<String> anahtarlar() {
                return new HashSet<>(t.getAll().keySet());
            }

            @Override
            public void sil(String anahtar) {
                t.edit().remove(anahtar).apply();
            }
        });
    }

    /** Bugünün gün numarası (yerel saat). */
    static long bugun() {
        Calendar c = Calendar.getInstance();
        long yerel = c.getTimeInMillis() + c.get(Calendar.ZONE_OFFSET) + c.get(Calendar.DST_OFFSET);
        return Math.floorDiv(yerel, 86_400_000L);
    }

    private static String anahtar(long gun, EgzersizSayaci.Tur tur) {
        return gun + "_" + tur.name();
    }

    synchronized void ekle(long gun, EgzersizSayaci.Tur tur, int adet) {
        if (adet <= 0) return;
        String a = anahtar(gun, tur);
        depo.yaz(a, depo.oku(a) + adet);
    }

    synchronized int gunluk(long gun, EgzersizSayaci.Tur tur) {
        return depo.oku(anahtar(gun, tur));
    }

    /** Son 7 gün (en eskisi başta, bugün sonda). */
    synchronized int[] haftalik(long bugun, EgzersizSayaci.Tur tur) {
        int[] d = new int[GUN_SAYISI];
        for (int i = 0; i < GUN_SAYISI; i++) d[i] = depo.oku(anahtar(bugun - (GUN_SAYISI - 1) + i, tur));
        return d;
    }

    synchronized int haftalikToplam(long bugun, EgzersizSayaci.Tur tur) {
        int t = 0;
        for (int d : haftalik(bugun, tur)) t += d;
        return t;
    }

    /** Saklama süresini geçmiş ya da bozuk anahtarları siler. */
    synchronized void temizle(long bugun) {
        for (String a : depo.anahtarlar()) {
            int i = a.indexOf('_');
            long gun;
            try {
                gun = i > 0 ? Long.parseLong(a.substring(0, i)) : -1;
            } catch (NumberFormatException e) {
                gun = -1;
            }
            if (gun < 0 || bugun - gun > SAKLAMA_GUNU) depo.sil(a);
        }
    }
}

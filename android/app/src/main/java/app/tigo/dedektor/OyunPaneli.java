package app.tigo.dedektor;

import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.media.ToneGenerator;
import android.os.SystemClock;
import android.util.TypedValue;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Oyun modu: taş-kağıt-makas, balon patlatma, parmak hesabı ve havada piyano. Oyun seçimi mod
 * panelinde, skor ve "Başla" sonuç kartında; balonlar ve piyano tuşları çizim katmanında.
 * Parmak uçları ekran oranına çevrilir (aynalı görüntüde de ekranda göründüğü yerde).
 */
final class OyunPaneli implements CizimView.Katman {

    private static final String[] ADLAR = {"Taş-Kağıt-Makas", "Balon", "Hesap", "Piyano"};
    private static final int[] SIMGELER = {R.drawable.ic_el, R.drawable.ic_balon, R.drawable.ic_hesap, R.drawable.ic_nota};

    private final AppCompatActivity etkinlik;
    private final Ayarlar ayarlar;
    private final SonucKarti kart;
    private final CizimView cizim;
    private final Geribildirim geribildirim;
    private final Kutlama kutlama;
    /** Oyun değişince (ipucu ve kart yeniden kurulsun). */
    private final Runnable oyunDegisti;
    private final SharedPreferences rekorlar;
    private final OyunAkisi tkm;
    private final BalonOyunu balon = new BalonOyunu(new Random());
    private final HesapOyunu hesap = new HesapOyunu(new Random());
    private final PiyanoOyunu piyano = new PiyanoOyunu();
    private final NotaCalar notalar = new NotaCalar();
    private final Paint boya = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cizgi = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint yazi = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF kutu = new RectF();
    private final float[] nokta = new float[2];
    private final float dp;
    /** Son el karesi ve işaret parmağı uçları (ekran oranında). */
    private volatile List<ElTakibi.ElDurumu> eller = Collections.emptyList();
    private volatile List<float[]> parmaklar = Collections.emptyList();
    private volatile boolean acik;
    private int sonBalonPuani = -1;
    private long sonHesapSuresi = -1;

    OyunPaneli(AppCompatActivity etkinlik, Ayarlar ayarlar, SonucKarti kart, CizimView cizim, Geribildirim geribildirim,
               Kutlama kutlama, Runnable oyunDegisti) {
        this.etkinlik = etkinlik;
        this.ayarlar = ayarlar;
        this.kart = kart;
        this.cizim = cizim;
        this.geribildirim = geribildirim;
        this.kutlama = kutlama;
        this.oyunDegisti = oyunDegisti;
        rekorlar = etkinlik.getSharedPreferences("oyun", AppCompatActivity.MODE_PRIVATE);
        tkm = new OyunAkisi(etkinlik.findViewById(R.id.oyun_yazisi), this::oyuncuSecimi);
        tkm.dinle(this::guncelle);
        dp = etkinlik.getResources().getDisplayMetrics().density;
        cizgi.setStyle(Paint.Style.STROKE);
        cizgi.setStrokeWidth(1.5f * dp);
        yazi.setTypeface(Cip.font(etkinlik, R.font.manrope_cok_kalin));
        yazi.setTextAlign(Paint.Align.CENTER);
        yazi.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 14, etkinlik.getResources().getDisplayMetrics()));
    }

    /** Mod panelinde oyun hapları (simgeli); seçili olan görünür yere kaydırılır. */
    void secimler(LinearLayout modPaneli) {
        Ayarlar.Oyun[] tum = Ayarlar.Oyun.values();
        TextView secili = null;
        for (int i = 0; i < tum.length; i++) {
            Ayarlar.Oyun o = tum[i];
            TextView c = Cip.olustur(etkinlik, ADLAR[i], SIMGELER[i], ayarlar.oyun == o, ayarlar.renk, v -> {
                if (ayarlar.oyun == o) return;
                durdur();
                ayarlar.oyun = o;
                ayarlar.kaydet();
                oyunDegisti.run();
            });
            if (ayarlar.oyun == o) secili = c;
            modPaneli.addView(c);
        }
        TextView hedef = secili;
        if (hedef != null && modPaneli.getParent() instanceof HorizontalScrollView) {
            HorizontalScrollView k = (HorizontalScrollView) modPaneli.getParent();
            k.post(() -> k.smoothScrollTo(Math.max(0, hedef.getLeft() - (k.getWidth() - hedef.getWidth()) / 2), 0));
        }
    }

    /** Oyun modundaysa kartı ve çizim katmanını kurar; değilse oyunları durdurur. */
    void goster(boolean oyunModu) {
        acik = oyunModu;
        if (!oyunModu) {
            durdur();
            cizim.katmanCikar(this);
            return;
        }
        kart.temizle();
        switch (ayarlar.oyun) {
            case TKM:
                kart.islemler.addView(Cip.tonlu(etkinlik, "Oyna", R.drawable.ic_oyna, ayarlar.renk, v -> tkm.oyna()));
                break;
            case BALON:
                kart.islemler.addView(Cip.tonlu(etkinlik, "Başla", R.drawable.ic_oyna, ayarlar.renk, v -> {
                    balon.basla(SystemClock.uptimeMillis());
                    sonBalonPuani = -1;
                    cizim.invalidate();
                    guncelle();
                }));
                break;
            case HESAP:
                kart.islemler.addView(Cip.tonlu(etkinlik, "Başla", R.drawable.ic_oyna, ayarlar.renk, v -> {
                    hesap.basla(SystemClock.uptimeMillis());
                    sonHesapSuresi = -1;
                    guncelle();
                }));
                break;
            default:
                break;
        }
        cizim.katmanEkle(this);
        guncelle();
    }

    String ipucu() {
        switch (ayarlar.oyun) {
            case BALON: return "Balonları işaret parmağınla patlat";
            case HESAP: return "Cevap kadar parmak göster, 1 saniye tut";
            case PIYANO: return "İşaret parmağını üstteki tuşlara indir";
            default: return "Oyna'ya bas, \"Göster!\" deyince elini göster";
        }
    }

    /** El modelinin sonucu (MediaPipe iş parçacığı): parmak uçları, hesap ve piyano. */
    void el(List<ElTakibi.ElDurumu> yeni) {
        eller = yeni;
        if (!acik) return;
        List<float[]> uclar = new ArrayList<>(yeni.size());
        int toplam = 0;
        for (ElTakibi.ElDurumu el : yeni) {
            NormalizedLandmark uc = el.noktalar.get(ElAnalizi.ISARET_UCU);
            float[] p = new float[2];
            cizim.ekranOrani(uc.x(), uc.y(), p);
            uclar.add(p);
            toplam += el.parmak;
        }
        parmaklar = uclar;
        long simdi = SystemClock.uptimeMillis();
        Ayarlar.Oyun oyun = ayarlar.oyun;
        if (oyun == Ayarlar.Oyun.PIYANO) {
            for (int n : piyano.guncelle(uclar)) {
                notalar.cal(n);
                geribildirim.titret(15);
            }
        } else if (oyun == Ayarlar.Oyun.HESAP) {
            int s = hesap.guncelle(toplam, !yeni.isEmpty(), simdi);
            if (s != HesapOyunu.YOK) etkinlik.runOnUiThread(() -> hesapSonucu(s, simdi));
        }
    }

    private void hesapSonucu(int s, long zaman) {
        geribildirim.titret(40);
        if (s == HesapOyunu.DOGRU) {
            geribildirim.ses(ToneGenerator.TONE_PROP_ACK, 150);
            geribildirim.soyle("Doğru");
            guncelle();
            kart.zipla();
            return;
        }
        long sure = hesap.sureMs(zaman);
        sonHesapSuresi = sure;
        long rekor = rekorlar.getLong("hesapRekor", 0);
        boolean yeni = rekor == 0 || sure < rekor;
        if (yeni) rekorlar.edit().putLong("hesapRekor", sure).apply();
        kutlama.baslat((yeni ? "Yeni rekor!\n" : "Bitti!\n") + saniye(sure), yeni);
        geribildirim.soyle(yeni ? "Yeni rekor" : "Bitti");
        guncelle();
    }

    /** Taş-kağıt-makas için: görünen ellerden ilk geçerli seçim. */
    private TasKagitMakas.Secim oyuncuSecimi() {
        for (ElTakibi.ElDurumu el : eller) {
            TasKagitMakas.Secim secim = TasKagitMakas.hareketten(el.hareketKodu, el.parmak);
            if (secim != null) return secim;
        }
        return null;
    }

    /** Kartın değerleri (arayüz iş parçacığı; saniyede birkaç kez). */
    void guncelle() {
        if (!acik) return;
        long simdi = SystemClock.uptimeMillis();
        switch (ayarlar.oyun) {
            case TKM:
                kart.yaz("TAŞ · KAĞIT · MAKAS", tkm.oyuncuPuani() + " : " + tkm.telefonPuani(), null, 34);
                kart.altYazi("Sen : Telefon");
                break;
            case BALON:
                if (balon.oynaniyor()) {
                    kart.yaz("BALON · " + (balon.kalanMs(simdi) + 999) / 1000 + " SN", String.valueOf(balon.puan()), null, 34);
                } else {
                    kart.yaz("BALON PATLAT", sonBalonPuani < 0 ? null : sonBalonPuani + " balon", "Başla'ya bas", 30);
                }
                int rekor = rekorlar.getInt("balonRekor", 0);
                kart.altYazi(rekor > 0 ? "En iyi: " + rekor + " balon" : "30 saniyede kaç balon?");
                break;
            case HESAP:
                if (hesap.oynaniyor()) {
                    kart.yaz("SORU " + hesap.soruNo() + " / " + HesapOyunu.SORU_SAYISI, hesap.soru() + " = ?", null, 32);
                    int g = hesap.gosterilen();
                    kart.altYazi(g < 0 ? "Elini göster" : "Gösterdiğin: " + g + (hesap.ilerleme(simdi) > 0 ? "  ·  tut…" : ""));
                } else {
                    kart.yaz("PARMAK HESABI", sonHesapSuresi < 0 ? null : saniye(sonHesapSuresi), "Başla'ya bas", 30);
                    long r = rekorlar.getLong("hesapRekor", 0);
                    kart.altYazi(r > 0 ? "En iyi: " + saniye(r) : HesapOyunu.SORU_SAYISI + " soru, iki elle 10'a kadar");
                }
                break;
            case PIYANO:
                int n = piyano.sonNota();
                kart.yaz("HAVADA PİYANO", n < 0 ? null : PiyanoOyunu.NOTALAR[n], "Tuşa parmağını indir", 32);
                kart.altYazi(null);
                break;
            default:
                break;
        }
    }

    private static String saniye(long ms) {
        return String.format(Locale.forLanguageTag("tr-TR"), "%.1f sn", ms / 1000f);
    }

    // ---------- Çizim (arayüz iş parçacığı) ----------

    @Override
    public void ciz(Canvas tuval, CizimView v) {
        if (!acik) return;
        if (ayarlar.oyun == Ayarlar.Oyun.BALON) balonlariCiz(tuval, v);
        else if (ayarlar.oyun == Ayarlar.Oyun.PIYANO) tuslariCiz(tuval, v);
    }

    private void balonlariCiz(Canvas tuval, CizimView v) {
        if (!balon.oynaniyor()) return;
        long simdi = SystemClock.uptimeMillis();
        int patlayan = balon.adim(simdi, parmaklar, v.oran());
        if (patlayan > 0) {
            geribildirim.ses(ToneGenerator.TONE_PROP_BEEP, 60);
            geribildirim.titret(25);
            v.post(() -> {
                guncelle();
                kart.zipla();
            });
        } else if (patlayan < 0) {
            v.post(this::balonBitti);
            return;
        }
        float w = v.getWidth();
        float h = v.getHeight();
        float r = BalonOyunu.YARICAP * w;
        for (BalonOyunu.Balon b : balon.balonlar()) {
            float x = b.x * w;
            float y = b.y * h;
            if (b.patladi >= 0) {
                float t = Math.min(1f, (simdi - b.patladi) / (float) BalonOyunu.PATLAMA_MS);
                cizgi.setColor(Cip.saydam(b.renk, Math.round(255 * (1 - t))));
                cizgi.setStrokeWidth(3 * dp);
                tuval.drawCircle(x, y, r * (1 + t), cizgi);
                continue;
            }
            cizgi.setColor(0xB3FFFFFF);
            cizgi.setStrokeWidth(1.5f * dp);
            tuval.drawLine(x, y + 1.15f * r, x + 0.15f * r, y + 2.3f * r, cizgi);
            boya.setColor(b.renk);
            kutu.set(x - r, y - 1.15f * r, x + r, y + 1.15f * r);
            tuval.drawOval(kutu, boya);
            tuval.drawCircle(x, y + 1.15f * r, 0.12f * r, boya);
            boya.setColor(0x66FFFFFF);
            kutu.set(x - 0.55f * r, y - 0.8f * r, x - 0.15f * r, y - 0.25f * r);
            tuval.drawOval(kutu, boya);
        }
        v.postInvalidateOnAnimation();
    }

    private void balonBitti() {
        int puan = balon.puan();
        sonBalonPuani = puan;
        int rekor = rekorlar.getInt("balonRekor", 0);
        boolean yeni = puan > rekor;
        if (yeni) rekorlar.edit().putInt("balonRekor", puan).apply();
        kutlama.baslat((yeni ? "Yeni rekor!\n" : "Süre doldu!\n") + puan + " balon", yeni);
        geribildirim.soyle(puan + " balon");
        geribildirim.titret(200);
        guncelle();
    }

    private void tuslariCiz(Canvas tuval, CizimView v) {
        float w = v.getWidth();
        float h = v.getHeight();
        float bosluk = 4 * dp;
        float tus = (w - bosluk * (PiyanoOyunu.TUS + 1)) / PiyanoOyunu.TUS;
        float ust = PiyanoOyunu.UST * h;
        float alt = PiyanoOyunu.ALT * h;
        Paint.FontMetrics m = yazi.getFontMetrics();
        for (int i = 0; i < PiyanoOyunu.TUS; i++) {
            float sol = bosluk + i * (tus + bosluk);
            kutu.set(sol, ust, sol + tus, alt);
            boolean basili = piyano.basili(i);
            boya.setColor(basili ? Cip.saydam(ayarlar.renk, 0xCC) : 0x40FFFFFF);
            tuval.drawRoundRect(kutu, 10 * dp, 10 * dp, boya);
            cizgi.setColor(basili ? ayarlar.renk : 0x99FFFFFF);
            cizgi.setStrokeWidth(1.5f * dp);
            tuval.drawRoundRect(kutu, 10 * dp, 10 * dp, cizgi);
            yazi.setColor(basili ? Cip.VURGU_USTU : 0xFFFFFFFF);
            tuval.drawText(PiyanoOyunu.NOTALAR[i], kutu.centerX(), alt - 12 * dp - m.descent, yazi);
        }
        // Parmak ucunun yeri: tuşa girdiğini görmek kolaylaşır.
        boya.setColor(0xE6FFFFFF);
        for (float[] p : parmaklar) tuval.drawCircle(p[0] * w, p[1] * h, 7 * dp, boya);
    }

    /** Süren oyunları durdurur (mod ya da oyun değişti). */
    void durdur() {
        tkm.durdur();
        balon.durdur();
        hesap.durdur();
        piyano.sifirla();
    }

    void kapat() {
        durdur();
        notalar.kapat();
    }
}

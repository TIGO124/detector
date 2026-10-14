package app.tigo.dedektor;

import com.google.mediapipe.tasks.components.containers.Connection;
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker;
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker;
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Çizilecek nokta bağlantıları: [başlangıç, bitiş, başlangıç, bitiş, ...] dizileri. */
final class Baglantilar {

    static final int[] AG = diziyeCevir(FaceLandmarker.FACE_LANDMARKS_TESSELATION);
    static final int[] YUZ_HATLARI = diziyeCevir(
        FaceLandmarker.FACE_LANDMARKS_FACE_OVAL,
        FaceLandmarker.FACE_LANDMARKS_LIPS,
        FaceLandmarker.FACE_LANDMARKS_LEFT_EYE,
        FaceLandmarker.FACE_LANDMARKS_RIGHT_EYE,
        FaceLandmarker.FACE_LANDMARKS_LEFT_EYE_BROW,
        FaceLandmarker.FACE_LANDMARKS_RIGHT_EYE_BROW,
        FaceLandmarker.FACE_LANDMARKS_LEFT_IRIS,
        FaceLandmarker.FACE_LANDMARKS_RIGHT_IRIS
    );
    static final int[] EL = diziyeCevir(HandLandmarker.HAND_CONNECTIONS);
    /** Vücut iskeleti; yüz noktaları (0–10) yüz ağıyla çakışmasın diye atlanır. */
    static final int[] VUCUT = vucutBaglantilari();

    private Baglantilar() {}

    @SafeVarargs
    private static int[] diziyeCevir(Collection<Connection>... kumeler) {
        List<Connection> hepsi = new ArrayList<>();
        for (Collection<Connection> k : kumeler) hepsi.addAll(k);
        int[] dizi = new int[hepsi.size() * 2];
        for (int i = 0; i < hepsi.size(); i++) {
            dizi[2 * i] = hepsi.get(i).start();
            dizi[2 * i + 1] = hepsi.get(i).end();
        }
        return dizi;
    }

    private static int[] vucutBaglantilari() {
        List<Connection> secili = new ArrayList<>();
        for (Connection b : PoseLandmarker.POSE_LANDMARKS) if (b.start() > 10 && b.end() > 10) secili.add(b);
        return diziyeCevir(secili);
    }
}

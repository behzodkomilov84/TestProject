package behzoddev.testproject.service;

import behzoddev.testproject.dao.UserActivityTotalRepository;
import behzoddev.testproject.dao.UserCourseActivityTotalRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// "/users" sahifasidagi "Oxirgi tashrif vaqti" katakchasi bosilganda —
// "Saytda jami necha soat" + kurslar kesimidagi vaqt (foydalanuvchi
// so'rovi, 2026-09-10: "Oxirgi tashrif vaqti ustuniga jami necha soat
// saytdan foydalandi? Kurslar kesimida qancha soatlari kursga aloqador
// sahifalarda ketyapti?").
//
// ISHLASH PRINSIPI: haqiqiy "sessiya davomiyligi" kuzatuvi (heartbeat/
// websocket) yo'q — shuning uchun ketma-ket HTTP so'rovlar orasidagi
// vaqt oralig'i "faol vaqt" sifatida hisoblanadi, FAQAT agar oraliq
// ACTIVE_GAP_THRESHOLD_SECONDS'dan kichik bo'lsa (aks holda foydalanuvchi
// "ketgan", oraliq hisoblanmaydi — masalan sahifani ochiq qoldirib
// uxlab qolgan bo'lsa). Bu OnlineUserTracker (faqat "hozir onlaynmi")
// bilan bir xil cheklovga ega — taxminiy (heuristik) ko'rsatkich,
// aniq stopwatch emas.
//
// Xotirada to'planadi (pendingSeconds), DB'ga esa FLUSH_INTERVAL_MS'da
// bir marta (batafsil izoh: flush()) yoziladi — har so'rovda DB yozish
// keraksiz yuk bo'lardi (OnlineUserTracker#touch bilan bir xil sabab).
@Slf4j
@Component
@RequiredArgsConstructor
public class UserActivityTracker {

    private static final long ACTIVE_GAP_THRESHOLD_SECONDS = 300; // 5 daqiqa

    private final UserActivityTotalRepository userActivityTotalRepository;
    private final UserCourseActivityTotalRepository userCourseActivityTotalRepository;

    // "/courses/5", "/courses/5/sections/12", "/api/courses/5/chapters" —
    // barchasi kursId=5 sifatida tan olinadi. "/courses/trash" yoki
    // "/courses/subscriptions" kabi RAQAMSIZ yo'llar mos KELMAYDI (\\d+
    // sababli) — bular kursga oid sahifalar emas.
    private static final Pattern COURSE_PATH_PATTERN =
            Pattern.compile("^/(?:api/)?courses/(\\d+)(?:/.*)?$");

    private final Map<Long, Instant> lastRequestTimeByUserId = new ConcurrentHashMap<>();
    // HAQIQIY TOPILGAN BUG (foydalanuvchi so'rovi, 2026-09-10: "дарсга
    // кириб шу саҳифада 3-4 дақиқа турдим. Лекин счётчик вақтни
    // ҳисобламабди" — umumiy vaqt hisoblangan, lekin kurs kesimida 0
    // qolgan): "gapSeconds" — foydalanuvchi OLDINGI so'ralgan sahifada
    // (shu URI) qancha vaqt o'tirgani, hozirgi (yangi) so'rov sahifasida
    // EMAS. Shu sabab kurs ID ham OLDINGI URI'dan olinishi kerak — avval
    // hozirgi (yangi) so'rov URI'sidan olinardi, ya'ni "dars sahifasida
    // 4 daqiqa o'tirib, keyin /users'ga o'tish" holatida o'sha 4 daqiqa
    // /users kursga oid bo'lmagani uchun umuman hech qaysi kursga
    // yozilmay, faqat umumiy vaqtga qo'shilib qolardi.
    private final Map<Long, String> lastRequestUriByUserId = new ConcurrentHashMap<>();
    private final Map<Long, AtomicLong> pendingTotalSeconds = new ConcurrentHashMap<>();
    private final Map<CourseKey, AtomicLong> pendingCourseSeconds = new ConcurrentHashMap<>();

    public void track(Long userId, String requestUri) {
        if (userId == null) return;

        Instant now = Instant.now();
        Instant lastTime = lastRequestTimeByUserId.put(userId, now);
        String lastUri = lastRequestUriByUserId.put(userId, requestUri);
        if (lastTime == null) return;

        long gapSeconds = Duration.between(lastTime, now).getSeconds();
        if (gapSeconds <= 0 || gapSeconds > ACTIVE_GAP_THRESHOLD_SECONDS) return;

        pendingTotalSeconds.computeIfAbsent(userId, k -> new AtomicLong()).addAndGet(gapSeconds);

        // "lastUri" — foydalanuvchi shu ORALIQda (gapSeconds) turgan
        // sahifa, "requestUri" (hozirgi, yangi so'rov) EMAS.
        Long courseId = extractCourseId(lastUri);
        if (courseId != null) {
            pendingCourseSeconds.computeIfAbsent(new CourseKey(userId, courseId), k -> new AtomicLong())
                    .addAndGet(gapSeconds);
        }
    }

    private Long extractCourseId(String uri) {
        if (uri == null) return null;
        Matcher m = COURSE_PATH_PATTERN.matcher(uri);
        if (!m.matches()) return null;
        try {
            return Long.parseLong(m.group(1));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // Har daqiqada xotiradagi to'plangan qiymatlarni DB'ga yozadi va
    // xotirani tozalaydi ("drain" — getAndSet(0), boshqa thread bir
    // vaqtda qo'shayotgan bo'lsa ham yo'qotilmaydi).
    @Scheduled(fixedRate = 60_000)
    @Transactional
    public void flush() {
        pendingTotalSeconds.forEach((userId, counter) -> {
            long delta = counter.getAndSet(0);
            if (delta > 0) {
                userActivityTotalRepository.addSeconds(userId, delta);
            }
        });

        pendingCourseSeconds.forEach((key, counter) -> {
            long delta = counter.getAndSet(0);
            if (delta > 0) {
                userCourseActivityTotalRepository.addSeconds(key.userId(), key.courseId(), delta);
            }
        });
    }

    private record CourseKey(Long userId, Long courseId) {
        private CourseKey {
            Objects.requireNonNull(userId);
            Objects.requireNonNull(courseId);
        }
    }
}

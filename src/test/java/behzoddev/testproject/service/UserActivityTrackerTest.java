package behzoddev.testproject.service;

import behzoddev.testproject.dao.UserActivityTotalRepository;
import behzoddev.testproject.dao.UserCourseActivityTotalRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * "/users" sahifasidagi "Saytda jami necha soat" / kurslar kesimidagi
 * vaqt hisobi — xotirada to'planadi (pendingSeconds), DB'ga esa
 * flush() orqali. Asosiy e'tibor: threshold'dan katta bo'shliqlar
 * hisoblanmasligi, kurs yo'lini to'g'ri aniqlash, flush() xotirani
 * to'g'ri "drain" qilishi.
 */
@ExtendWith(MockitoExtension.class)
class UserActivityTrackerTest {

    @Mock
    private UserActivityTotalRepository userActivityTotalRepository;
    @Mock
    private UserCourseActivityTotalRepository userCourseActivityTotalRepository;

    @InjectMocks
    private UserActivityTracker tracker;

    // reflection orqali xotiradagi map'larga to'g'ridan-to'g'ri kirish —
    // real vaqtda so'rovlar orasida kutishning o'rniga.
    @SuppressWarnings("unchecked")
    private Map<Long, Instant> lastRequestTimeMap() throws Exception {
        Field f = UserActivityTracker.class.getDeclaredField("lastRequestTimeByUserId");
        f.setAccessible(true);
        return (Map<Long, Instant>) f.get(tracker);
    }

    @SuppressWarnings("unchecked")
    private Map<Long, AtomicLong> pendingTotalMap() throws Exception {
        Field f = UserActivityTracker.class.getDeclaredField("pendingTotalSeconds");
        f.setAccessible(true);
        return (Map<Long, AtomicLong>) f.get(tracker);
    }

    // ===== track =====

    @Test
    void track_firstRequestForUser_recordsTimeButAccumulatesNothing() throws Exception {
        tracker.track(1L, "/index");

        assertThat(lastRequestTimeMap()).containsKey(1L);
        assertThat(pendingTotalMap()).doesNotContainKey(1L);
    }

    @Test
    void track_nullUserId_doesNotThrow() {
        tracker.track(null, "/index");
        // exception bo'lmasa yetarli
    }

    @Test
    void track_secondRequestWithinThreshold_accumulatesGap() throws Exception {
        tracker.track(1L, "/index");
        lastRequestTimeMap().put(1L, Instant.now().minusSeconds(30));

        tracker.track(1L, "/index");

        assertThat(pendingTotalMap().get(1L).get()).isGreaterThanOrEqualTo(30);
    }

    @Test
    void track_gapBeyondThreshold_doesNotAccumulate() throws Exception {
        tracker.track(1L, "/index");
        // 5 daqiqalik threshold'dan OSHIB ketgan bo'shliq (masalan foydalanuvchi
        // sahifani ochiq qoldirib ketgan) — hisoblanmasligi kerak.
        lastRequestTimeMap().put(1L, Instant.now().minusSeconds(600));

        tracker.track(1L, "/index");

        assertThat(pendingTotalMap()).doesNotContainKey(1L);
    }

    @Test
    void track_courseDetailPath_accumulatesCourseSeconds() throws Exception {
        tracker.track(1L, "/courses/5");
        lastRequestTimeMap().put(1L, Instant.now().minusSeconds(20));

        tracker.track(1L, "/courses/5/sections/12");

        Field f = UserActivityTracker.class.getDeclaredField("pendingCourseSeconds");
        f.setAccessible(true);
        Map<?, AtomicLong> courseMap = (Map<?, AtomicLong>) f.get(tracker);
        assertThat(courseMap).hasSize(1);
        assertThat(courseMap.values().iterator().next().get()).isGreaterThanOrEqualTo(20);
    }

    // HAQIQIY TOPILGAN BUG (foydalanuvchi so'rovi, 2026-09-10: "дарсга
    // кириб шу саҳифада 3-4 дақиқа турдим. Лекин счётчик вақтни
    // ҳисобламабди" — umumiy vaqt hisoblangan, lekin kurs kesimida 0
    // qolgan) — gapSeconds foydalanuvchi OLDINGI (kurs) sahifasida
    // turgan vaqti, shuning uchun keyingi so'rov KURSGA OID BO'LMASA
    // ham (masalan darsni o'qib, keyin /users'ga o'tsa), o'sha vaqt
    // baribir OLDINGI (kurs) sahifasiga yozilishi kerak.
    @Test
    void track_courseThenNonCoursePage_creditsGapToThePreviousCoursePage() throws Exception {
        tracker.track(1L, "/courses/6/sections/100");
        // Foydalanuvchi shu darsda 4 daqiqa "o'qib" turdi.
        lastRequestTimeMap().put(1L, Instant.now().minusSeconds(240));

        // Keyin KURSGA ALOQASI YO'Q sahifaga o'tdi.
        tracker.track(1L, "/users");

        Field f = UserActivityTracker.class.getDeclaredField("pendingCourseSeconds");
        f.setAccessible(true);
        Map<?, AtomicLong> courseMap = (Map<?, AtomicLong>) f.get(tracker);
        assertThat(courseMap).hasSize(1);
        assertThat(courseMap.values().iterator().next().get()).isGreaterThanOrEqualTo(240);
    }

    @Test
    void track_apiCoursePath_alsoRecognizedAsCourseActivity() throws Exception {
        tracker.track(1L, "/api/courses/7/chapters");
        lastRequestTimeMap().put(1L, Instant.now().minusSeconds(15));

        tracker.track(1L, "/api/courses/7");

        Field f = UserActivityTracker.class.getDeclaredField("pendingCourseSeconds");
        f.setAccessible(true);
        Map<?, AtomicLong> courseMap = (Map<?, AtomicLong>) f.get(tracker);
        assertThat(courseMap).hasSize(1);
    }

    @Test
    void track_nonNumericCoursePath_notTreatedAsCourseActivity() throws Exception {
        // "/courses/subscriptions" va "/courses/trash" — bular RAQAMSIZ,
        // haqiqiy kursga oid sahifa emas.
        tracker.track(1L, "/courses/subscriptions");
        lastRequestTimeMap().put(1L, Instant.now().minusSeconds(10));

        tracker.track(1L, "/courses/subscriptions");

        Field f = UserActivityTracker.class.getDeclaredField("pendingCourseSeconds");
        f.setAccessible(true);
        Map<?, AtomicLong> courseMap = (Map<?, AtomicLong>) f.get(tracker);
        assertThat(courseMap).isEmpty();
        // Lekin umumiy saytdagi vaqtga baribir qo'shiladi.
        assertThat(pendingTotalMap().get(1L).get()).isGreaterThanOrEqualTo(10);
    }

    @Test
    void track_unrelatedPath_notTreatedAsCourseActivity() throws Exception {
        tracker.track(1L, "/profile");
        lastRequestTimeMap().put(1L, Instant.now().minusSeconds(10));

        tracker.track(1L, "/profile");

        Field f = UserActivityTracker.class.getDeclaredField("pendingCourseSeconds");
        f.setAccessible(true);
        Map<?, AtomicLong> courseMap = (Map<?, AtomicLong>) f.get(tracker);
        assertThat(courseMap).isEmpty();
    }

    // ===== flush =====

    @Test
    void flush_pendingSeconds_persistsAndResetsToZero() throws Exception {
        pendingTotalMap().put(1L, new AtomicLong(90));

        tracker.flush();

        verify(userActivityTotalRepository).addSeconds(eq(1L), eq(90L));
        assertThat(pendingTotalMap().get(1L).get()).isZero();
    }

    @Test
    void flush_noPendingSeconds_doesNotCallRepository() {
        tracker.flush();

        verify(userActivityTotalRepository, never()).addSeconds(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void flush_pendingCourseSeconds_persistsAndResets() throws Exception {
        tracker.track(1L, "/courses/5");
        lastRequestTimeMap().put(1L, Instant.now().minusSeconds(45));
        tracker.track(1L, "/courses/5/sections/1");

        tracker.flush();

        verify(userCourseActivityTotalRepository).addSeconds(eq(1L), eq(5L), org.mockito.ArgumentMatchers.longThat(v -> v >= 45));
    }
}

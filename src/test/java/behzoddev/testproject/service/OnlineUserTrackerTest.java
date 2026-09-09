package behzoddev.testproject.service;

import behzoddev.testproject.dao.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * "/users" sahifasidagi "🟢 Onlayn" belgisi — xotiradagi (DB'siz)
 * kuzatuvchi, faqat "Oxirgi tashrif vaqti" uchun (throttled) DB'ga ham
 * yozadi. Asosiy e'tibor: threshold vaqtidan keyin "oflayn"ga qaytish,
 * bir nechta foydalanuvchini mustaqil kuzatish va DB yozish tezligini
 * cheklash (PERSIST_THROTTLE_SECONDS).
 */
@ExtendWith(MockitoExtension.class)
class OnlineUserTrackerTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private OnlineUserTracker tracker;

    @Test
    void isOnline_neverTouched_returnsFalse() {
        assertThat(tracker.isOnline(1L)).isFalse();
    }

    @Test
    void isOnline_justTouched_returnsTrue() {
        tracker.touch(1L);

        assertThat(tracker.isOnline(1L)).isTrue();
    }

    @Test
    void touch_nullUserId_doesNotThrow() {
        tracker.touch(null);

        assertThat(tracker.onlineUserIds()).isEmpty();
        verify(userRepository, never()).updateLastSeenAt(any(), any());
    }

    @Test
    void onlineUserIds_multipleTouched_returnsAllOfThem() {
        tracker.touch(1L);
        tracker.touch(2L);
        tracker.touch(3L);

        assertThat(tracker.onlineUserIds()).containsExactlyInAnyOrder(1L, 2L, 3L);
    }

    @Test
    void isOnline_touchedLongAgo_returnsFalse() throws Exception {
        tracker.touch(1L);

        // Threshold (180s) dan oshib ketgan "eski" vaqt belgisini
        // reflection orqali to'g'ridan-to'g'ri xaritaga yozamiz — real
        // vaqtda 3 daqiqa kutishning o'rniga.
        java.lang.reflect.Field field = OnlineUserTracker.class.getDeclaredField("lastSeenByUserId");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        var map = (java.util.Map<Long, java.time.Instant>) field.get(tracker);
        map.put(1L, java.time.Instant.now().minusSeconds(300));

        assertThat(tracker.isOnline(1L)).isFalse();
        assertThat(tracker.onlineUserIds()).doesNotContain(1L);
    }

    // ===== "Oxirgi tashrif vaqti" — throttled DB persist =====

    @Test
    void touch_firstTimeForUser_persistsLastSeenToDb() {
        tracker.touch(5L);

        verify(userRepository).updateLastSeenAt(eq(5L), any());
    }

    @Test
    void touch_calledTwiceInQuickSuccession_persistsOnlyOnce() {
        tracker.touch(5L);
        tracker.touch(5L);
        tracker.touch(5L);

        // Throttle (60s) ichida qayta-qayta chaqirilsa ham, DB'ga FAQAT
        // birinchi safar yoziladi — aks holda faol foydalanuvchi uchun
        // har so'rovda keraksiz UPDATE ketardi.
        verify(userRepository, org.mockito.Mockito.times(1)).updateLastSeenAt(eq(5L), any());
    }

    @Test
    void touch_throttleWindowPassed_persistsAgain() throws Exception {
        tracker.touch(5L);

        // Throttle oynasi (60s) o'tib ketgan holatni reflection orqali
        // simulyatsiya qilamiz — real vaqtda kutishning o'rniga.
        java.lang.reflect.Field field = OnlineUserTracker.class.getDeclaredField("lastPersistedByUserId");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        var map = (java.util.Map<Long, java.time.Instant>) field.get(tracker);
        map.put(5L, java.time.Instant.now().minusSeconds(120));

        tracker.touch(5L);

        verify(userRepository, org.mockito.Mockito.times(2)).updateLastSeenAt(eq(5L), any());
    }
}

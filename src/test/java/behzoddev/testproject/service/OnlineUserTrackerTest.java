package behzoddev.testproject.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * "/users" sahifasidagi "🟢 Onlayn" belgisi — xotiradagi (DB'siz)
 * kuzatuvchi. Asosiy e'tibor: threshold vaqtidan keyin "oflayn"ga
 * qaytish va bir nechta foydalanuvchini mustaqil kuzatish.
 */
class OnlineUserTrackerTest {

    @Test
    void isOnline_neverTouched_returnsFalse() {
        OnlineUserTracker tracker = new OnlineUserTracker();

        assertThat(tracker.isOnline(1L)).isFalse();
    }

    @Test
    void isOnline_justTouched_returnsTrue() {
        OnlineUserTracker tracker = new OnlineUserTracker();

        tracker.touch(1L);

        assertThat(tracker.isOnline(1L)).isTrue();
    }

    @Test
    void touch_nullUserId_doesNotThrow() {
        OnlineUserTracker tracker = new OnlineUserTracker();

        tracker.touch(null);

        assertThat(tracker.onlineUserIds()).isEmpty();
    }

    @Test
    void onlineUserIds_multipleTouched_returnsAllOfThem() {
        OnlineUserTracker tracker = new OnlineUserTracker();

        tracker.touch(1L);
        tracker.touch(2L);
        tracker.touch(3L);

        assertThat(tracker.onlineUserIds()).containsExactlyInAnyOrder(1L, 2L, 3L);
    }

    @Test
    void isOnline_touchedLongAgo_returnsFalse() throws Exception {
        OnlineUserTracker tracker = new OnlineUserTracker();
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
}

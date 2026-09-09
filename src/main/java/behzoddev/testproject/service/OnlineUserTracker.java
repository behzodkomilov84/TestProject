package behzoddev.testproject.service;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

// "/users" sahifasidagi "🟢 Onlayn" belgisi (foydalanuvchi so'rovi,
// 2026-09-09: "Hozir online turibdimi, shuni ham telegramga o'xshagan
// belgi bilan ko'rsat"). Foydalanuvchi "onlayn" holati FAQAT XOTIRADA
// (DB'siz) kuzatiladi — har bir autentifikatsiya qilingan HTTP so'rovda
// (OnlineUserTrackingInterceptor) shu yerga vaqt belgisi yoziladi.
// Qasddan DB'ga yozilmaydi: bu faqat "hozir shu daqiqada faolmi" degan
// taxminiy (real-time) ko'rsatkich, tarixiy ma'lumot emas — server
// qayta ishga tushsa holat tozalanadi, bu qabul qilinadi (aks holda har
// so'rovda DB yozish keraksiz yuk bo'lardi).
@Component
public class OnlineUserTracker {

    // Necha soniya harakatsizlikdan keyin "oflayn" deb hisoblanadi.
    // Heartbeat/websocket yo'q — shunchaki oxirgi HTTP so'rov vaqtiga
    // qaraladi, shuning uchun sahifani ochiq qoldirib hech narsa
    // bosmagan foydalanuvchi bu muddat ichida hali ham "onlayn"
    // hisoblanadi.
    private static final long ONLINE_THRESHOLD_SECONDS = 180;

    private final Map<Long, Instant> lastSeenByUserId = new ConcurrentHashMap<>();

    public void touch(Long userId) {
        if (userId != null) {
            lastSeenByUserId.put(userId, Instant.now());
        }
    }

    public boolean isOnline(Long userId) {
        Instant lastSeen = lastSeenByUserId.get(userId);
        return lastSeen != null && lastSeen.isAfter(cutoff());
    }

    public Set<Long> onlineUserIds() {
        Instant cutoff = cutoff();
        return lastSeenByUserId.entrySet().stream()
                .filter(e -> e.getValue().isAfter(cutoff))
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    private Instant cutoff() {
        return Instant.now().minusSeconds(ONLINE_THRESHOLD_SECONDS);
    }
}

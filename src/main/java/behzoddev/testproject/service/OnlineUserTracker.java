package behzoddev.testproject.service;

import behzoddev.testproject.dao.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

// "/users" sahifasidagi "🟢 Onlayn" belgisi (foydalanuvchi so'rovi,
// 2026-09-09: "Hozir online turibdimi, shuni ham telegramga o'xshagan
// belgi bilan ko'rsat"). "Hozir onlaynmi" holati FAQAT XOTIRADA (DB'siz)
// kuzatiladi — har bir autentifikatsiya qilingan HTTP so'rovda
// (OnlineUserTrackingInterceptor) yoki bot harakatida (TelegramBot,
// TelegramUserService) shu yerga vaqt belgisi yoziladi. Server qayta
// ishga tushsa bu holat tozalanadi — bu qabul qilinadi, chunki bu faqat
// "hozir shu daqiqada faolmi" degan taxminiy ko'rsatkich.
//
// "Oxirgi tashrif vaqti" ustuni esa (foydalanuvchi so'rovi, 2026-09-09)
// TARIXIY fakt bo'lgani uchun — User.lastSeenAt orqali DB'da ham
// saqlanadi, lekin HAR so'rovda emas (bu keraksiz DB yukini oshirardi) —
// bir foydalanuvchi uchun ko'pi bilan PERSIST_THROTTLE_SECONDS'da bir
// marta yoziladi.
@Component
@RequiredArgsConstructor
public class OnlineUserTracker {

    // Necha soniya harakatsizlikdan keyin "oflayn" deb hisoblanadi.
    // Heartbeat/websocket yo'q — shunchaki oxirgi so'rov vaqtiga
    // qaraladi, shuning uchun sahifani ochiq qoldirib hech narsa
    // bosmagan foydalanuvchi bu muddat ichida hali ham "onlayn"
    // hisoblanadi.
    private static final long ONLINE_THRESHOLD_SECONDS = 180;

    // DB'ga "oxirgi tashrif" yozish tezligini cheklash.
    private static final long PERSIST_THROTTLE_SECONDS = 60;

    private final UserRepository userRepository;

    private final Map<Long, Instant> lastSeenByUserId = new ConcurrentHashMap<>();
    private final Map<Long, Instant> lastPersistedByUserId = new ConcurrentHashMap<>();

    public void touch(Long userId) {
        if (userId == null) return;

        Instant now = Instant.now();
        lastSeenByUserId.put(userId, now);

        Instant lastPersisted = lastPersistedByUserId.get(userId);
        if (lastPersisted == null || lastPersisted.isBefore(now.minusSeconds(PERSIST_THROTTLE_SECONDS))) {
            lastPersistedByUserId.put(userId, now);
            userRepository.updateLastSeenAt(userId, LocalDateTime.ofInstant(now, ZoneId.systemDefault()));
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

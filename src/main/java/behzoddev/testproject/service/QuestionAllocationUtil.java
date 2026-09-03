package behzoddev.testproject.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// "Suv quyish" (water-filling) taqsimlash algoritmi — bir nechta ID
// (masalan mavzu) orasida savollar/imkoniyat soniga QARAB EMAS, balki
// TENG ulush bilan umumiy sonni taqsimlaydi (masalan 3 ta mavzu, 30 ta
// savol kerak bo'lsa — har biriga 10 tadan). Bironta ID'da imkoniyat
// yetmasa, o'sha o'zining bor sonida "to'lib" qoladi, yetmagan qism
// qolgan (hali joyi bor) ID'larga yana TENG bo'lib qayta taqsimlanadi —
// bu jarayon barcha ID'lar to'lib qolguncha yoki talab qondirilguncha
// davom etadi. Agar JAMI imkoniyat ham talabdan kam bo'lsa — aniq xato
// bilan to'xtaydi (o'zi tasodifiy kamaytirib qo'ymaydi).
//
// ExamVariantService ("🎲 Variantlar yaratish" — bir nechta O'QUVCHI
// uchun) va TeacherService ("Savollar to'plami"da avtomatik tanlash —
// BITTA to'plam uchun) ikkalasi ham shundan foydalanadi, bir xil
// mantiq ikki joyda takrorlanmasin deb.
final class QuestionAllocationUtil {

    private QuestionAllocationUtil() {
    }

    static Map<Long, Integer> allocateEqually(List<Long> ids, Map<Long, Integer> capacityById, int totalWanted) {
        Map<Long, Integer> allocated = new LinkedHashMap<>();
        for (Long id : ids) {
            allocated.put(id, 0);
        }

        Set<Long> active = new LinkedHashSet<>();
        for (Long id : ids) {
            if (capacityById.getOrDefault(id, 0) > 0) {
                active.add(id);
            }
        }

        int remaining = totalWanted;
        while (remaining > 0 && !active.isEmpty()) {
            int share = remaining / active.size();

            if (share == 0) {
                int given = 0;
                for (Long id : active) {
                    if (given >= remaining) break;
                    allocated.merge(id, 1, Integer::sum);
                    given++;
                }
                remaining -= given;
                break;
            }

            for (Long id : new ArrayList<>(active)) {
                int room = capacityById.getOrDefault(id, 0) - allocated.get(id);
                int give = Math.min(share, room);
                allocated.merge(id, give, Integer::sum);
                remaining -= give;
                if (allocated.get(id) >= capacityById.getOrDefault(id, 0)) {
                    active.remove(id);
                }
            }
        }

        if (remaining > 0) {
            int totalCapacity = capacityById.values().stream().mapToInt(Integer::intValue).sum();
            throw new IllegalArgumentException("❌ Yetarli savol yo'q: shu doirada jami " + totalCapacity +
                    " ta faol savol bor, lekin " + totalWanted + " ta so'ralgan.");
        }

        return allocated;
    }
}

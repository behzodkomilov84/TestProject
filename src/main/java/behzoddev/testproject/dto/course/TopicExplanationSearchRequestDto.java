package behzoddev.testproject.dto.course;

import java.util.List;

// "Kurs ichidan mavzu yoritmasi bo'yicha qidiruv" so'rovi — POST body sifatida
// (TopicExplanationSearchController). Avval GET + "topicIds" query parametrlari
// ro'yxati sifatida yuborilardi, lekin katta kurslarda (masalan 897 ta bog'langan
// mavzu) URL 12000+ belgigacha o'sib, server/browser URL uzunligi chegarasidan
// oshib "Failed to fetch" bilan yiqilardi (foydalanuvchi xabari, 2026-09-20:
// "qidirishda xatolik" — Epidemiologiya va immunoprofilaktika kursida).
public record TopicExplanationSearchRequestDto(
        List<Long> topicIds,
        String q
) {
}

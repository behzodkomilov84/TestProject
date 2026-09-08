package behzoddev.testproject.dto.science;

import jakarta.validation.constraints.NotBlank;

public record ScienceIdAndNameDto(
        Long id,
        @NotBlank(message = "❌Science.name bo'sh bo'lishi mumkin emas.") String name,
        // Shu fanda (UI'da "Bo'lim") nechta Bo'lim (TopicSection, UI'da
        // "Mavzu") borligi — science.html'da har bir qatorda ko'rsatish
        // uchun (masalan "(3 ta mavzu)"). Ixtiyoriy — boshqa (bot,
        // courseDetail.js) ishlatuvchilar buni e'tiborsiz qoldiradi.
        long sectionCount,
        // Qaysi Yo'nalishga tegishli — science.js shu bo'yicha guruhlaydi
        // (courses.js#renderFieldBox bilan bir xil andoza). Ixtiyoriy —
        // boshqa ishlatuvchilar buni e'tiborsiz qoldiradi.
        Long fieldId,
        String fieldName,
        // ADMIN cheklovi FRONTEND'da ham ko'rinishi uchun — bu fanni
        // (va uning Bo'lim/Mavzu/Savollarini) joriy foydalanuvchi
        // boshqara oladimi (ScienceService.canManageScience bilan bir
        // xil, backend'dagi HAQIQIY tekshiruv natijasi). Haqiqiy
        // topilgan bug (2026-09-08): backend allaqachon bloklagan bo'lsa
        // ham, frontend ✏️🗑️⬆⬇⌨️ tugmalarini HAR DOIM ko'rsatib
        // turardi — bosganda kutilmagan "⛔" xatosi chiqardi. Ixtiyoriy —
        // boshqa (bot, courseDetail.js) ishlatuvchilar buni e'tiborsiz
        // qoldiradi, shu sabab orqaga moslik konstruktorlarida "true"
        // (cheklovsiz) standart qiymat sifatida beriladi.
        boolean canManage) {

    // Orqaga moslik — ko'p joyda (bot, testlar) hali 2 argumentli
    // konstruktor ishlatiladi, qolganlari ular uchun ahamiyatsiz (0/null).
    public ScienceIdAndNameDto(Long id, String name) {
        this(id, name, 0, null, null, true);
    }

    // Orqaga moslik — sectionCount kerak, lekin fieldId/fieldName kerak
    // bo'lmagan eski chaqiruv joylari uchun.
    public ScienceIdAndNameDto(Long id, String name, long sectionCount) {
        this(id, name, sectionCount, null, null, true);
    }

    // ScienceRepository'dagi JPQL "select new ...(...)" konstruktor
    // ifodalari aynan shu 5 argumentli shaklni chaqiradi (canManage
    // JPQL'da emas, SERVICE qatlamida — ScienceService orqali —
    // hisoblab, alohida qo'shiladi, chunki u joriy foydalanuvchiga
    // bog'liq va query natijasi cache'lanmaydi).
    public ScienceIdAndNameDto(Long id, String name, long sectionCount, Long fieldId, String fieldName) {
        this(id, name, sectionCount, fieldId, fieldName, true);
    }
}

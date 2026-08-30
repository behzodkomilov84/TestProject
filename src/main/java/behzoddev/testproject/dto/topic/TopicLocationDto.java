package behzoddev.testproject.dto.topic;

// Mavzu qaysi Fan (Science) va Bo'limga (TopicSection) tegishli ekani —
// test-form.js'dagi "⬅ Orqaga" tugmasi shu orqali TEST BOSHQARUVI'dagi
// tegishli sahifaga (/topics?scienceId=&sectionId=) qaytadi. sectionId —
// ixtiyoriy, mavzu hali bo'limga biriktirilmagan bo'lsa NULL.
public record TopicLocationDto(Long scienceId, Long sectionId) {
}

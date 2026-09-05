package behzoddev.testproject.dto.question;

// "Bo'lim ichida qidiruv" — science.html'dagi 🔍 tugmasi ochadigan modal
// uchun (QuestionService.searchQuestionsByScience). Butun Fan bo'yicha
// (barcha Mavzu -> Dars -> Savol ierarxiyasi kesib o'tib) savol matnidan
// qidiradi — QuestionScienceTrashDto bilan bir xil g'oya (natija qaysi
// Dars/Bo'limga tegishli ekanini ham ko'rsatadi, foydalanuvchi bosib shu
// Darsga o'tishi uchun).
public record QuestionScienceSearchDto(
        Long id,
        String questionText,
        Long topicId,
        String topicName,
        // Ixtiyoriy — mavzu (Dars) biror Bo'limga (TopicSection/"Mavzu"
        // guruhi) biriktirilgan bo'lsa uning id'si va nomi (masalan
        // "I. UMUMIY KIMYO"). NULL — dars bo'limsiz. sectionId natijaga
        // bosilganda topics.html'ga TO'G'RI Mavzu filtri bilan o'tish
        // uchun kerak (topic.js#goToTopicInManagement bilan bir xil g'oya
        // — aks holda fokus DOM'da yo'q qatorga tushib qolishi mumkin).
        Long sectionId,
        String sectionName) {
}

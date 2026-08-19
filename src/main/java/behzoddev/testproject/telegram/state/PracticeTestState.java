package behzoddev.testproject.telegram.state;

import java.util.List;

// Botda "🎯 Mustaqil test" oqimi davomida (savoldan savolga o'tishda)
// saqlanadigan holat — TelegramSession.tempData'da JSON sifatida.
// TestSessionService.startTest() savollarni faqat bir marta, tasodifiy
// tartibda qaytaradi (hech qayerda saqlanmaydi) — shuning uchun bot
// o'zi shu ro'yxatni (va foydalanuvchi tanlagan javoblarni) saqlab
// borishi kerak, oxirida hammasini birdaniga finishTest()ga yuborish uchun.
public record PracticeTestState(
        Long testSessionId,
        long startedAtEpochMilli,
        int currentIndex,
        List<QuestionSnapshot> questions,
        List<AnswerPick> answers,
        // Exam/Hard rejimlarida — saytdagi vaqt chegarasi bilan bir xil
        // (Practice rejimida null — vaqt chegarasi yo'q).
        Long deadlineEpochMilli
) {
    public record QuestionSnapshot(Long id, String questionText, String imageUrl, List<AnswerSnapshot> answers) {}

    public record AnswerSnapshot(Long id, String answerText, String imageUrl) {}

    public record AnswerPick(Long questionId, Long answerId) {}
}

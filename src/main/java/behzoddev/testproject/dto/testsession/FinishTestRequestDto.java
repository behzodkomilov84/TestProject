package behzoddev.testproject.dto.testsession;

import java.util.List;

public record FinishTestRequestDto(
        Long testSessionId,
        Long startedAt,
        Long finishedAt,
        List<AnswerResultDto> answers,
        // Testda AJRATILGAN savollar soni (javob berilganlar emas) — vaqt
        // tugab, ba'zi savollarga ulgurmagan bo'lsa ham, natija (X/Y)
        // haqiqiy savollar soniga nisbatan hisoblanishi uchun. Masalan
        // 2 savoldan 1 tasiga ulgurgan bo'lsa — "1/2", "1/1" emas.
        Integer totalQuestions
) {}


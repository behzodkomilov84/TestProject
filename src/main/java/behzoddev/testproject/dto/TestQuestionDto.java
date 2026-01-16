package behzoddev.testproject.dto;

import java.util.List;

public record TestQuestionDto(Long id, String questionText, List<AnswerIdAndTextDto> answers) {

    /*//Рандомизация ответов (ВАЖНО)
    public static TestQuestionDto from(Question q) {
        List<AnswerIdAndTextDto> answers = q.getAnswers().stream()
                .map(a -> new AnswerIdAndTextDto(a.getId(), a.getAnswerText()))
                .collect(Collectors.toList());

        Collections.shuffle(answers); // 🔥 ПЕРЕМЕШИВАЕМ ОТВЕТЫ

        return new TestQuestionDto(q.getId(), q.getQuestionText(), answers);
    }
*/
}

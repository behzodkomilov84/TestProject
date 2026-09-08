package behzoddev.testproject.service;

import behzoddev.testproject.dao.AnswerRepository;
import behzoddev.testproject.dto.ModalAnswerCommentSaveDto;
import behzoddev.testproject.dto.ModalCommentSaveDto;
import behzoddev.testproject.entity.User;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AnswerService {

    private final AnswerRepository answerRepository;
    private final QuestionService questionService;

    // Lombok @RequiredArgsConstructor emas — @Lazy'ni parametrga qo'yish
    // uchun konstruktor qo'lda yozildi. Sabab: QuestionService -> Validation
    // -> AnswerService -> QuestionService aylanma bog'lanish (circular
    // dependency) hosil qilardi; Lazy proxy shu tsiklni uzadi.
    public AnswerService(AnswerRepository answerRepository,
                          @Lazy QuestionService questionService) {
        this.answerRepository = answerRepository;
        this.questionService = questionService;
    }

    public boolean isUnique(List<String> answersList) {
        Set<String> uniqueAnswers =
                answersList.stream()
                        .map(answer -> answer.trim().toLowerCase())
                        .collect(Collectors.toSet());

        return uniqueAnswers.size() == answersList.size();
    }

    @Transactional
    public void updateCommentOfTrueAnswer(ModalCommentSaveDto payload, User currentUser) {
        Long questionId = (Long) payload.questionId();
        // ADMIN cheklovi — izoh (commentary) ham savolning bir qismi,
        // shu sabab savol qaysi Fan-Mavzuga tegishli ekaniga qarab
        // tekshiriladi (foydalanuvchi so'rovi, 2026-09-08).
        questionService.checkCanManageQuestionById(questionId, currentUser);

        ModalAnswerCommentSaveDto answer = (ModalAnswerCommentSaveDto) payload.trueAnswer();

        if (!answerRepository.isCorrect(questionId, answer.id())){
            throw new IllegalArgumentException("Noto'g'ri javob uchun yuborilgan izoh saqlanmaydi.");
        }

        int updated = answerRepository.updateCommentOfTrueAnswer(answer.id(), answer.commentary());

        if (updated == 0){
            throw new IllegalStateException("Izoh yangilanmadi (0 rows updated)");
        }
    }
}

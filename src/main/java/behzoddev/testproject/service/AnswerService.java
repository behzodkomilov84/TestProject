package behzoddev.testproject.service;

import behzoddev.testproject.dao.AnswerRepository;
import behzoddev.testproject.dto.ModalAnswerCommentSaveDto;
import behzoddev.testproject.dto.ModalCommentSaveDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AnswerService {

    private final AnswerRepository answerRepository;

    public boolean isUnique(List<String> answersList) {
        Set<String> uniqueAnswers =
                answersList.stream()
                        .map(answer -> answer.trim().toLowerCase())
                        .collect(Collectors.toSet());

        return uniqueAnswers.size() == answersList.size();
    }

    @Transactional
    public void updateCommentOfTrueAnswer(ModalCommentSaveDto payload) {
        Long questionId = (Long) payload.questionId();

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

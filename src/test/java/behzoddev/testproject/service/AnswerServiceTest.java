package behzoddev.testproject.service;

import behzoddev.testproject.dao.AnswerRepository;
import behzoddev.testproject.dto.ModalAnswerCommentSaveDto;
import behzoddev.testproject.dto.ModalCommentSaveDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnswerServiceTest {

    @Mock
    private AnswerRepository answerRepository;

    @InjectMocks
    private AnswerService answerService;

    // ===== isUnique =====

    @Test
    void isUnique_allDistinctIgnoringCaseAndWhitespace_returnsFalseForDuplicates() {
        assertThat(answerService.isUnique(List.of(" A ", "a", "B"))).isFalse();
    }

    @Test
    void isUnique_allDistinct_returnsTrue() {
        assertThat(answerService.isUnique(List.of("A", "B", "C"))).isTrue();
    }

    // ===== updateCommentOfTrueAnswer =====

    @Test
    void updateCommentOfTrueAnswer_correctAnswer_updatesComment() {
        ModalCommentSaveDto payload = new ModalCommentSaveDto(1L, new ModalAnswerCommentSaveDto(10L, "yangi izoh", true));
        when(answerRepository.isCorrect(1L, 10L)).thenReturn(true);
        when(answerRepository.updateCommentOfTrueAnswer(10L, "yangi izoh")).thenReturn(1);

        answerService.updateCommentOfTrueAnswer(payload);
        // exception yo'q -> muvaffaqiyatli
    }

    @Test
    void updateCommentOfTrueAnswer_wrongAnswer_throws() {
        ModalCommentSaveDto payload = new ModalCommentSaveDto(1L, new ModalAnswerCommentSaveDto(10L, "izoh", false));
        when(answerRepository.isCorrect(1L, 10L)).thenReturn(false);

        assertThatThrownBy(() -> answerService.updateCommentOfTrueAnswer(payload))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Noto'g'ri javob");
    }

    @Test
    void updateCommentOfTrueAnswer_zeroRowsUpdated_throwsIllegalState() {
        ModalCommentSaveDto payload = new ModalCommentSaveDto(1L, new ModalAnswerCommentSaveDto(10L, "izoh", true));
        when(answerRepository.isCorrect(1L, 10L)).thenReturn(true);
        when(answerRepository.updateCommentOfTrueAnswer(10L, "izoh")).thenReturn(0);

        assertThatThrownBy(() -> answerService.updateCommentOfTrueAnswer(payload))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("0 rows updated");
    }
}

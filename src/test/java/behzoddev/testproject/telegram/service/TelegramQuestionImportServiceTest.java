package behzoddev.testproject.telegram.service;

import behzoddev.testproject.dto.excel.ImportResultDto;
import behzoddev.testproject.dto.science.ScienceIdAndNameDto;
import behzoddev.testproject.dto.topic.TopicWithQuestionCountDto;
import behzoddev.testproject.service.ExcelService;
import behzoddev.testproject.service.ScienceService;
import behzoddev.testproject.service.TopicService;
import behzoddev.testproject.telegram.state.BotState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Botda "🗂 Savollar boshqaruvi" — fan/mavzu tanlab, .xlsx faylni to'g'ridan-
 * to'g'ri botga yuborib import qilish (ExcelService orqali, saytdagi bilan
 * bir xil validatsiya).
 */
@ExtendWith(MockitoExtension.class)
class TelegramQuestionImportServiceTest {

    private static final Long CHAT_ID = 999L;

    @Mock
    private ScienceService scienceService;
    @Mock
    private TopicService topicService;
    @Mock
    private ExcelService excelService;
    @Mock
    private TelegramSessionService sessionService;

    @InjectMocks
    private TelegramQuestionImportService importService;

    @Test
    void startFlow_noSciences_saysEmpty() {
        when(scienceService.getSciences()).thenReturn(List.of());

        SendMessage msg = importService.startFlow(CHAT_ID);

        assertThat(msg.getText()).contains("fanlar mavjud emas");
    }

    @Test
    void startFlow_listsSciences() {
        when(scienceService.getSciences()).thenReturn(List.of(new ScienceIdAndNameDto(1L, "Matematika")));

        SendMessage msg = importService.startFlow(CHAT_ID);

        assertThat(msg.getReplyMarkup()).isNotNull();
    }

    @Test
    void selectScience_noTopics_promptsToCreateOnSite() {
        when(topicService.getTopicsWithQuestionCount(1L)).thenReturn(List.of());

        SendMessage msg = importService.selectScience(CHAT_ID, 1L);

        assertThat(msg.getText()).contains("mavzu yo'q");
    }

    @Test
    void selectTopic_setsAwaitingFileStateAndStoresTopicId() {
        SendMessage msg = importService.selectTopic(CHAT_ID, 42L);

        verify(sessionService).putTempData(CHAT_ID, "tg_importTopicId", "42");
        verify(sessionService).setState(CHAT_ID, BotState.AWAITING_EXCEL_FILE);
        assertThat(msg.getText()).contains(".xlsx");
    }

    @Test
    void importFile_noTopicSelected_showsWarning() {
        when(sessionService.getTempData(CHAT_ID)).thenReturn(Map.of());

        SendMessage msg = importService.importFile(CHAT_ID, "content".getBytes(), "questions.xlsx");

        assertThat(msg.getText()).contains("Mavzu tanlanmagan");
        verifyNoInteractions(excelService);
    }

    @Test
    void importFile_success_reportsImportedCountAndClearsSession() {
        when(sessionService.getTempData(CHAT_ID)).thenReturn(Map.of("tg_importTopicId", "42"));
        when(excelService.importQuestions(any(MultipartFile.class), eq(42L)))
                .thenReturn(new ImportResultDto(true, 5L, List.of()));

        SendMessage msg = importService.importFile(CHAT_ID, "content".getBytes(), "questions.xlsx");

        verify(sessionService).clear(CHAT_ID);
        assertThat(msg.getText()).contains("✅").contains("5 ta savol");
    }

    @Test
    void importFile_partialFailure_listsRowErrors() {
        when(sessionService.getTempData(CHAT_ID)).thenReturn(Map.of("tg_importTopicId", "42"));
        when(excelService.importQuestions(any(MultipartFile.class), eq(42L)))
                .thenReturn(new ImportResultDto(false, 2L, List.of("Row 3: xato")));

        SendMessage msg = importService.importFile(CHAT_ID, "content".getBytes(), "questions.xlsx");

        assertThat(msg.getText()).contains("qisman").contains("Row 3: xato");
    }

    @Test
    void remindToSendFile_returnsHint() {
        SendMessage msg = importService.remindToSendFile(CHAT_ID);

        assertThat(msg.getText()).contains(".xlsx");
    }
}

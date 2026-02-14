package behzoddev.testproject.service;

import behzoddev.testproject.dto.answer.AnswerShortDto;
import behzoddev.testproject.dto.excel.ImportResultDto;
import behzoddev.testproject.dto.question.QuestionSaveDto;
import behzoddev.testproject.validation.Validation;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ExcelService {

    private final QuestionService questionService;
    private final DataFormatter formatter = new DataFormatter();
    private final AnswerService answerService;
    private final Validation validation;

    @Transactional
    public ImportResultDto importQuestions(MultipartFile file, Long topicId) {

        List<String> errors = new ArrayList<>();
        Long imported = 0L;

        try (Workbook wb = WorkbookFactory.create(file.getInputStream())) {

            Sheet sheet = wb.getSheetAt(0);

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {

                Row row = sheet.getRow(i);
                if (row == null) continue;

                imported = getValuesFromCellAndSaveToDataBase(topicId, row, imported, errors, i);
            }

        } catch (Exception e) {
            return new ImportResultDto(false, 0L, List.of("Invalid Excel file"));
        }

        if (!errors.isEmpty()) {
            return new ImportResultDto(false, imported, errors);
        }

        return new ImportResultDto(true, imported, List.of());
    }

    private @Nullable Long getValuesFromCellAndSaveToDataBase(Long topicId, Row row, Long imported, List<String> errors, int i) {
        try {
            String qText = cell(row, 0);
            String a = cell(row, 1);
            String b = cell(row, 2);
            String c = cell(row, 3);
            String d = cell(row, 4);
            String correct = cell(row, 5);
            String comment = cell(row, 6);

            validation.textFieldMustNotBeEmpty(qText);
            validation.textFieldMustNotBeEmpty(a);
            validation.textFieldMustNotBeEmpty(b);
            validation.textFieldMustNotBeEmpty(c);
            validation.textFieldMustNotBeEmpty(d);
            validation.textFieldMustNotBeEmpty(correct);
            validation.textFieldMustNotBeEmpty(comment);

            int correctIndex = parseCorrect(correct);

            String commentOfWrongAnswer = "Noto'g'ri javob";

            List<AnswerShortDto> answerShortDtoList = new ArrayList<>();
            answerShortDtoList.add(new AnswerShortDto(a, correctIndex == 0, correctIndex == 0 ? comment : commentOfWrongAnswer));
            answerShortDtoList.add(new AnswerShortDto(b, correctIndex == 1, correctIndex == 1 ? comment : commentOfWrongAnswer));
            answerShortDtoList.add(new AnswerShortDto(c, correctIndex == 2, correctIndex == 2 ? comment : commentOfWrongAnswer));
            answerShortDtoList.add(new AnswerShortDto(d, correctIndex == 3, correctIndex == 3 ? comment : commentOfWrongAnswer));

            List<String> answersText = List.of(a, b, c, d);

            boolean isUnique = answerService.isUnique(answersText); //Javoblarni bir xil masligini tekshiradi.

            if (!isUnique) {
                throw new IllegalArgumentException("❌Javoblar bir xil bo'lishi mumkin emas.");
            }

            QuestionSaveDto newQuestion = QuestionSaveDto.builder()
                    .questionText(qText)
                    .topicId(topicId)
                    .answers(answerShortDtoList)
                    .build();

            //Yangi testni DB da bor-yo'qligini tekshirish
            List<QuestionSaveDto> existingQuestions = questionService.getQuestionSaveDtoByTopicId(topicId);
            boolean questionWithAnswersExists = questionService.isQuestionWithAnswersExists(existingQuestions, newQuestion);

            if (questionWithAnswersExists) {
                throw new IllegalArgumentException("Bu test ayni shu javoblar bilan allaqachon bazada mavjud.");
            }

            questionService.save(newQuestion);

            imported++;

        } catch (Exception e) {
            errors.add("Row " + (i + 1) + ": " + e.getMessage());
        }
        return imported;
    }

    private String cell(Row row, int i) {
        if (row.getCell(i) == null) return "";
        return formatter.formatCellValue(row.getCell(i)).trim();
    }

    private int parseCorrect(String c) {
        return switch (c.toUpperCase()) {
            case "A" -> 0;
            case "B" -> 1;
            case "C" -> 2;
            case "D" -> 3;
            default -> throw new IllegalArgumentException("❌To'g'ri javob varianti faqat A/B/C/D dan biri bo'lishi mumkin.");
        };
    }

}

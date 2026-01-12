package behzoddev.testproject.service;

import behzoddev.testproject.dto.AnswerShortDto;
import behzoddev.testproject.dto.ImportResultDto;
import behzoddev.testproject.dto.QuestionSaveDto;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ExcelService {

    private final QuestionService questionService;
    private final DataFormatter formatter = new DataFormatter();

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

            if (qText.isBlank() || a.isBlank() || b.isBlank() || c.isBlank() || d.isBlank()) {
                throw new RuntimeException("Empty field");
            }

            int correctIndex = parseCorrect(correct);

            String commentOfWrongAnswer = "Noto'g'ri javob";

            List<AnswerShortDto> answerShortDtoList = new ArrayList<>();
            answerShortDtoList.add(new AnswerShortDto(a, correctIndex == 0, correctIndex == 0 ? comment : commentOfWrongAnswer));
            answerShortDtoList.add(new AnswerShortDto(b, correctIndex == 1, correctIndex == 1 ? comment : commentOfWrongAnswer));
            answerShortDtoList.add(new AnswerShortDto(c, correctIndex == 2, correctIndex == 2 ? comment : commentOfWrongAnswer));
            answerShortDtoList.add(new AnswerShortDto(d, correctIndex == 3, correctIndex == 3 ? comment : commentOfWrongAnswer));

            boolean isUnique = questionService.isUnique(answerShortDtoList); //Javoblarni bir xil masligini tekshiradi.

            if (!isUnique) {
                throw new IllegalArgumentException("Answers must be unique");
            }

            QuestionSaveDto newQuestion = QuestionSaveDto.builder()
                    .questionText(qText)
                    .topicId(topicId)
                    .answers(answerShortDtoList)
                    .build();

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
            default -> throw new RuntimeException("Correct must be A/B/C/D");
        };
    }

}

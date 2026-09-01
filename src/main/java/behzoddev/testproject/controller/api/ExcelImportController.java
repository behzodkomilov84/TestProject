package behzoddev.testproject.controller.api;

import behzoddev.testproject.dto.export.ExportedFileDto;
import behzoddev.testproject.service.ExamVariantService;
import behzoddev.testproject.service.ExcelService;
import behzoddev.testproject.service.WordService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ExcelImportController {

    private final ExcelService excelService;
    private final WordService wordService;
    private final ExamVariantService examVariantService;

    @GetMapping("/export/template")
    public ResponseEntity<Resource> downloadTemplate() throws Exception {

        String shablonFile = "templates/template_For_Import.xlsx";
        ClassPathResource file = new ClassPathResource(shablonFile);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=template_For_Import.xlsx")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(file);
    }

    @PostMapping("/import/excel")
    public ResponseEntity<?> importExcel(
            @RequestParam MultipartFile file,
            @RequestParam Long topicId
    ) {
        return ResponseEntity.ok(excelService.importQuestions(file, topicId));
    }

    // "📥 Excel'ga eksport" — shu mavzudagi barcha faol savollarni import
    // shabloni bilan bir xil formatdagi .xlsx faylga yozib, yuklab beradi
    // (question.js — controls qatoridagi tugma).
    @GetMapping("/export/questions")
    public ResponseEntity<byte[]> exportQuestions(@RequestParam Long topicId) {
        return attachment(excelService.exportQuestions(topicId), ".xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    }

    // "📊 Excel'ga eksport" (Bo'lim miqyosida) — shu Bo'limdagi BARCHA
    // mavzularning savollarini BITTA faylga yig'ib beradi (topicSection.js).
    @GetMapping("/export/questions/section")
    public ResponseEntity<byte[]> exportQuestionsForSection(@RequestParam Long sectionId) {
        return attachment(excelService.exportQuestionsForSection(sectionId), ".xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    }

    // "📊 Excel'ga eksport" (Fan miqyosida) — shu Fandagi BARCHA
    // mavzularning savollarini BITTA faylga yig'ib beradi (science.js).
    @GetMapping("/export/questions/science")
    public ResponseEntity<byte[]> exportQuestionsForScience(@RequestParam Long scienceId) {
        return attachment(excelService.exportQuestionsForScience(scienceId), ".xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    }

    // "📝 Word'ga eksport" — shu mavzudagi barcha faol savollarni chop
    // etishga tayyor .docx faylga yozib beradi (izohsiz, to'g'ri javobsiz —
    // question.js, Excel eksport tugmasi yonida).
    @GetMapping("/export/questions/word")
    public ResponseEntity<byte[]> exportQuestionsToWord(@RequestParam Long topicId) {
        return attachment(wordService.exportQuestionsToWord(topicId), ".docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
    }

    // "📝 Word'ga eksport" (Bo'lim miqyosida) — shu Bo'limdagi BARCHA
    // mavzularning savollarini BITTA .docx faylga yig'ib beradi
    // (topicSection.js), har bir mavzu o'z sahifasida.
    @GetMapping("/export/questions/word/section")
    public ResponseEntity<byte[]> exportQuestionsForSectionToWord(@RequestParam Long sectionId) {
        return attachment(wordService.exportQuestionsForSection(sectionId), ".docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
    }

    // "📝 Word'ga eksport" (Fan miqyosida) — shu Fandagi BARCHA mavzularning
    // savollarini BITTA .docx faylga yig'ib beradi (science.js), har bir
    // mavzu o'z sahifasida.
    @GetMapping("/export/questions/word/science")
    public ResponseEntity<byte[]> exportQuestionsForScienceToWord(@RequestParam Long scienceId) {
        return attachment(wordService.exportQuestionsForScience(scienceId), ".docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
    }

    // "🎲 Variantlar yaratish" — Word eksport oynasidagi checkbox orqali
    // (question.js/topic.js/topicSection.js/science.js). Har biri BOSHQA
    // savollardan iborat "variantCount" ta .docx + javoblar kaliti,
    // BITTA .zip faylga yig'ilgan holda qaytariladi.
    @GetMapping("/export/questions/word/variants")
    public ResponseEntity<byte[]> exportVariantsForTopic(
            @RequestParam Long topicId,
            @RequestParam int variantCount,
            @RequestParam int perVariant,
            @RequestParam(defaultValue = "true") boolean shuffleAnswers
    ) {
        return attachment(examVariantService.generateVariantsForTopic(topicId, variantCount, perVariant, shuffleAnswers),
                ".zip", "application/zip");
    }

    @GetMapping("/export/questions/word/variants/section")
    public ResponseEntity<byte[]> exportVariantsForSection(
            @RequestParam Long sectionId,
            @RequestParam int variantCount,
            @RequestParam int perVariant,
            @RequestParam(defaultValue = "true") boolean shuffleAnswers
    ) {
        return attachment(examVariantService.generateVariantsForSection(sectionId, variantCount, perVariant, shuffleAnswers),
                ".zip", "application/zip");
    }

    @GetMapping("/export/questions/word/variants/science")
    public ResponseEntity<byte[]> exportVariantsForScience(
            @RequestParam Long scienceId,
            @RequestParam int variantCount,
            @RequestParam int perVariant,
            @RequestParam(defaultValue = "true") boolean shuffleAnswers
    ) {
        return attachment(examVariantService.generateVariantsForScience(scienceId, variantCount, perVariant, shuffleAnswers),
                ".zip", "application/zip");
    }

    // Barcha eksport turlari uchun umumiy — fayl nomi mavzu/bo'lim/fan
    // NOMIDAN olinadi (ExportedFileDto#filenameBase, xizmat qatlamida
    // hisoblangan). ContentDisposition.filename(..., UTF_8) orqali —
    // Uzbek nomlar (masalan "Bo'lim: Kimyo") kirill/lotin, apostrof va
    // boshqa ASCII bo'lmagan belgilar bilan ham TO'G'RI ko'rsatiladi
    // (RFC 5987 "filename*=UTF-8''..." + eski brauzerlar uchun ASCII
    // "filename=" fallback'i, ikkalasi ham avtomatik qo'yiladi).
    private ResponseEntity<byte[]> attachment(ExportedFileDto file, String extension, String contentType) {
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(file.filenameBase() + extension, StandardCharsets.UTF_8)
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(MediaType.parseMediaType(contentType))
                .body(file.data());
    }

}

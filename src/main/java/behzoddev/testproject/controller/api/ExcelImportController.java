package behzoddev.testproject.controller.api;

import behzoddev.testproject.service.ExamVariantService;
import behzoddev.testproject.service.ExcelService;
import behzoddev.testproject.service.WordService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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
        byte[] data = excelService.exportQuestions(topicId);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=savollar_" + topicId + ".xlsx")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(data);
    }

    // "📊 Excel'ga eksport" (Bo'lim miqyosida) — shu Bo'limdagi BARCHA
    // mavzularning savollarini BITTA faylga yig'ib beradi (topicSection.js).
    @GetMapping("/export/questions/section")
    public ResponseEntity<byte[]> exportQuestionsForSection(@RequestParam Long sectionId) {
        byte[] data = excelService.exportQuestionsForSection(sectionId);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=savollar_bolim_" + sectionId + ".xlsx")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(data);
    }

    // "📊 Excel'ga eksport" (Fan miqyosida) — shu Fandagi BARCHA
    // mavzularning savollarini BITTA faylga yig'ib beradi (science.js).
    @GetMapping("/export/questions/science")
    public ResponseEntity<byte[]> exportQuestionsForScience(@RequestParam Long scienceId) {
        byte[] data = excelService.exportQuestionsForScience(scienceId);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=savollar_fan_" + scienceId + ".xlsx")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(data);
    }

    // "📝 Word'ga eksport" — shu mavzudagi barcha faol savollarni chop
    // etishga tayyor .docx faylga yozib beradi (izohsiz, to'g'ri javobsiz —
    // question.js, Excel eksport tugmasi yonida).
    @GetMapping("/export/questions/word")
    public ResponseEntity<byte[]> exportQuestionsToWord(@RequestParam Long topicId) {
        byte[] data = wordService.exportQuestionsToWord(topicId);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=savollar_" + topicId + ".docx")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .body(data);
    }

    // "📝 Word'ga eksport" (Bo'lim miqyosida) — shu Bo'limdagi BARCHA
    // mavzularning savollarini BITTA .docx faylga yig'ib beradi
    // (topicSection.js), har bir mavzu o'z sahifasida.
    @GetMapping("/export/questions/word/section")
    public ResponseEntity<byte[]> exportQuestionsForSectionToWord(@RequestParam Long sectionId) {
        byte[] data = wordService.exportQuestionsForSection(sectionId);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=savollar_bolim_" + sectionId + ".docx")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .body(data);
    }

    // "📝 Word'ga eksport" (Fan miqyosida) — shu Fandagi BARCHA mavzularning
    // savollarini BITTA .docx faylga yig'ib beradi (science.js), har bir
    // mavzu o'z sahifasida.
    @GetMapping("/export/questions/word/science")
    public ResponseEntity<byte[]> exportQuestionsForScienceToWord(@RequestParam Long scienceId) {
        byte[] data = wordService.exportQuestionsForScience(scienceId);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=savollar_fan_" + scienceId + ".docx")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .body(data);
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
        byte[] data = examVariantService.generateVariantsForTopic(topicId, variantCount, perVariant, shuffleAnswers);
        return zipAttachment(data, "variantlar_mavzu_" + topicId);
    }

    @GetMapping("/export/questions/word/variants/section")
    public ResponseEntity<byte[]> exportVariantsForSection(
            @RequestParam Long sectionId,
            @RequestParam int variantCount,
            @RequestParam int perVariant,
            @RequestParam(defaultValue = "true") boolean shuffleAnswers
    ) {
        byte[] data = examVariantService.generateVariantsForSection(sectionId, variantCount, perVariant, shuffleAnswers);
        return zipAttachment(data, "variantlar_bolim_" + sectionId);
    }

    @GetMapping("/export/questions/word/variants/science")
    public ResponseEntity<byte[]> exportVariantsForScience(
            @RequestParam Long scienceId,
            @RequestParam int variantCount,
            @RequestParam int perVariant,
            @RequestParam(defaultValue = "true") boolean shuffleAnswers
    ) {
        byte[] data = examVariantService.generateVariantsForScience(scienceId, variantCount, perVariant, shuffleAnswers);
        return zipAttachment(data, "variantlar_fan_" + scienceId);
    }

    private ResponseEntity<byte[]> zipAttachment(byte[] data, String baseFilename) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + baseFilename + ".zip")
                .contentType(MediaType.parseMediaType("application/zip"))
                .body(data);
    }

}

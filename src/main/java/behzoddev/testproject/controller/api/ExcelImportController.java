package behzoddev.testproject.controller.api;

import behzoddev.testproject.service.ExcelService;
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

}

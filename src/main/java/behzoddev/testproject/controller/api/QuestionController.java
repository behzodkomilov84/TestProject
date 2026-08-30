package behzoddev.testproject.controller.api;

import behzoddev.testproject.dto.answer.AnswerShortDto;
import behzoddev.testproject.dto.ModalCommentSaveDto;
import behzoddev.testproject.dto.question.QuestionDto;
import behzoddev.testproject.dto.question.QuestionSaveDto;
import behzoddev.testproject.dto.question.QuestionScienceTrashDto;
import behzoddev.testproject.dto.question.QuestionTrashDto;
import behzoddev.testproject.exception.ErrorResponse;
import behzoddev.testproject.service.AnswerService;
import behzoddev.testproject.service.FileStorageService;
import behzoddev.testproject.service.QuestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class QuestionController {
    private final QuestionService questionService;
    private final AnswerService answerService;
    private final FileStorageService fileStorageService;

    // Savol yoki javob variantiga (masalan, geometrik chizmaga) rasm yuklash.
    // Frontend avval shu endpoint orqali rasmni yuklaydi, qaytgan URL'ni esa
    // savolni saqlashda (QuestionSaveDto/AnswerShortDto ichida) jo'natadi.
    @PostMapping("/api/question/upload-image")
    @ResponseBody
    public ResponseEntity<?> uploadImage(@RequestParam("image") MultipartFile image) {
        try {
            String url = fileStorageService.storeQuestionImage(image);
            return ResponseEntity.ok(Map.of("url", url));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // Izoh (commentary) uchun rasm yuklash — matn, rasm va video birga bo'lishi mumkin.
    @PostMapping("/api/question/upload-commentary-image")
    @ResponseBody
    public ResponseEntity<?> uploadCommentaryImage(@RequestParam("image") MultipartFile image) {
        try {
            String url = fileStorageService.storeCommentaryImage(image);
            return ResponseEntity.ok(Map.of("url", url));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // Izoh (commentary) uchun video yuklash.
    @PostMapping("/api/question/upload-commentary-video")
    @ResponseBody
    public ResponseEntity<?> uploadCommentaryVideo(@RequestParam("video") MultipartFile video) {
        try {
            String url = fileStorageService.storeCommentaryVideo(video);
            return ResponseEntity.ok(Map.of("url", url));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/api/question")
    public ResponseEntity<Page<QuestionDto>> getPage(
            @RequestParam Long topicId,
            @PageableDefault(size = 10, page = 0) Pageable pageable,
            @RequestParam(required = false) String searchQuestionText
    ) {
        Page<QuestionDto> questionDtoPageByTopicId = questionService.getQuestionDtoPageByTopicId(
                topicId,
                searchQuestionText,
                pageable
        );

            return ResponseEntity.ok(questionDtoPageByTopicId);
    }

    @GetMapping("/api/question/all")
    public ResponseEntity<List<QuestionDto>> getAll(
            @RequestParam Long topicId,
            @RequestParam(required = false) String searchQuestionText
    ) {
        return ResponseEntity.ok(
                questionService.findAll(topicId, searchQuestionText)
        );
    }

    // Savollar tartibini qayta belgilash ("⬆⬇" yoki A-Z/Z-A saralashdan
    // keyin, faqat "Hammasi" rejimida) — to'liq yangi tartibdagi id ro'yxati.
    @PostMapping("/api/question/reorder")
    @ResponseBody
    public ResponseEntity<?> reorder(@RequestParam Long topicId, @RequestBody List<Long> orderedQuestionIds) {
        try {
            questionService.reorderQuestions(topicId, orderedQuestionIds);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/api/question/save")
    @ResponseBody
    public ResponseEntity<?> saveQuestion(@RequestBody Map<Object, Object> payload) {
        try {
            long topicId = Long.parseLong(payload.get("topicId").toString());

            String questionText = payload.get("questionText").toString();

            // Savolga biriktirilgan rasm (ixtiyoriy) — frontend avval
            // /api/question/upload-image orqali yuklab, qaytgan URL'ni shu yerga jo'natadi.
            String questionImageUrl = payload.get("imageUrl") != null
                    ? payload.get("imageUrl").toString()
                    : null;

            var answers = (List<Map<Object, Object>>) payload.get("answers");
            List<AnswerShortDto> answerShortDto = new ArrayList<>();
            List<String> answerTextList = new ArrayList<>();

            for (Map<Object, Object> answer : answers) {

                String answerText = answer.get("answerText").toString();
                answerTextList.add(answerText);

                boolean isTrue = Boolean.parseBoolean(answer.get("isTrue").toString());

                String commentary = "Noto'g'ri javob";
                if (isTrue) {
                    commentary = "To'g'ri javob";
                }

                if (answer.get("commentary") != null) {
                    commentary = answer.get("commentary").toString();
                }

                String answerImageUrl = answer.get("imageUrl") != null
                        ? answer.get("imageUrl").toString()
                        : null;

                // Izohga (commentary) biriktirilgan rasm/video — ixtiyoriy,
                // matnni almashtirmaydi, unga qo'shimcha sifatida saqlanadi.
                String commentaryImageUrl = answer.get("commentaryImageUrl") != null
                        ? answer.get("commentaryImageUrl").toString()
                        : null;

                String commentaryVideoUrl = answer.get("commentaryVideoUrl") != null
                        ? answer.get("commentaryVideoUrl").toString()
                        : null;

                answerShortDto.add(new AnswerShortDto(
                        answerText, isTrue, commentary, answerImageUrl,
                        commentaryImageUrl, commentaryVideoUrl));
            }

            boolean isUnique = answerService.isUnique(answerTextList); //Javoblarni bir xil masligini tekshiradi.

            if (!isUnique) {
                throw new IllegalArgumentException("❌Javoblar bir xil bo'lishi mumkin emas.");
            }

            QuestionSaveDto newQuestion = QuestionSaveDto.builder()
                    .topicId(topicId)
                    .questionText(questionText)
                    .imageUrl(questionImageUrl)
                    .answers(answerShortDto)
                    .build();

            List<QuestionSaveDto> existingQuestions = questionService.getQuestionSaveDtoByTopicId(topicId);

            if (questionService.isQuestionWithAnswersExists(existingQuestions, newQuestion)) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(new ErrorResponse(
                                "Bunday javoblarga ega savol allaqachon mavjud.",
                                HttpStatus.CONFLICT.value()
                        ));
            }

            questionService.save(newQuestion);

            return ResponseEntity.ok(Map.of("message", "Muvaffaqiyatli saqlandi."));
        }catch (Exception e) {
            return ResponseEntity
                    .badRequest()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/science/{scienceId}/topic/{topicId}/question")
    public ResponseEntity<List<QuestionDto>> getQuestionsByIds(@PathVariable Long scienceId, @PathVariable Long topicId) {
        List<QuestionDto> questionDto = questionService.getQuestionsByIds(scienceId, topicId);

        return ResponseEntity.ok(questionDto);
    }

    @GetMapping("/question/{questionId}")
    public ResponseEntity<QuestionDto> getQuestionById(@PathVariable Long questionId) {
        QuestionDto questionDto = questionService.getQuestionById(questionId);

        return ResponseEntity.ok(questionDto);
    }

    @PutMapping("/api/question/update")
    public ResponseEntity<?> updateQuestion(@RequestBody QuestionDto payload) {

        try {
            questionService.updateQuestion(payload);

            return ResponseEntity.ok(
                    Map.of("message", "Updated")
            );
        } catch (IllegalArgumentException e) {
            return ResponseEntity
                    .badRequest()
                    .body(Map.of("error", e.getMessage()));
        }

    }

    @PatchMapping("/api/question/updateComment")
    public ResponseEntity<?> updateComment(@RequestBody ModalCommentSaveDto payload) {

        try {
            answerService.updateCommentOfTrueAnswer(payload);

            return ResponseEntity.ok(
                    Map.of("message", "Muvaffaqiyatli o'zgartirildi.")
            );
        } catch (Exception e) {
            return ResponseEntity
                    .badRequest()
                    .body(Map.of("error", e.getMessage()));
        }

    }


    @DeleteMapping("/api/question/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        try {
            questionService.deleteQuestion(id);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity
                    .badRequest()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    // Guruh holatida o'chirish — jadvaldagi checkbox'lar orqali
    // belgilangan savollar (question.js#deleteSelectedQuestions). Spring
    // literal "/bulk" segmentini yuqoridagi "/{id}"dan ANIQROQ deb
    // hisoblab, to'g'ri metodga yo'naltiradi (id="bulk" deb noto'g'ri
    // talqin qilinmaydi).
    @DeleteMapping("/api/question/bulk")
    public ResponseEntity<?> deleteBulk(@RequestBody List<Long> ids) {
        try {
            int deleted = questionService.deleteQuestions(ids);
            return ResponseEntity.ok(Map.of("deleted", deleted));
        } catch (IllegalArgumentException e) {
            return ResponseEntity
                    .badRequest()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    // "O'chirilganlar savati" (savol/test darajasida).
    @GetMapping("/api/question/deleted")
    @ResponseBody
    public ResponseEntity<List<QuestionTrashDto>> getDeleted(@RequestParam Long topicId) {
        return ResponseEntity.ok(questionService.getDeletedQuestions(topicId));
    }

    // "O'chirilganlar savati" — BUTUN FAN bo'yicha (topics.html'dagi
    // global savol savati, barcha mavzular birga).
    @GetMapping("/api/question/deleted-by-science")
    @ResponseBody
    public ResponseEntity<List<QuestionScienceTrashDto>> getDeletedByScience(@RequestParam Long scienceId) {
        return ResponseEntity.ok(questionService.getDeletedQuestionsByScience(scienceId));
    }

    @PostMapping("/api/question/{id}/restore")
    @ResponseBody
    public ResponseEntity<?> restore(@PathVariable Long id) {
        try {
            questionService.restoreQuestion(id);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // Guruh holatida "♻️ Tiklash" — savatdagi checkbox'lar orqali
    // belgilangan savollar (question.js#restoreSelectedQuestions /
    // topicScienceTrash.js). Literal "/bulk/restore" — "/{id}/restore"dan
    // ANIQROQ deb hisoblanadi (Spring'ning o'zi to'g'ri yo'naltiradi,
    // xuddi "/bulk" va "/bulk/permanent"dagi kabi).
    @PostMapping("/api/question/bulk/restore")
    @ResponseBody
    public ResponseEntity<?> restoreBulk(@RequestBody List<Long> ids) {
        try {
            int restored = questionService.restoreQuestions(ids);
            return ResponseEntity.ok(Map.of("restored", restored));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/api/question/{id}/permanent")
    @ResponseBody
    public ResponseEntity<?> permanentDelete(@PathVariable Long id) {
        try {
            questionService.permanentlyDeleteQuestion(id);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // Guruh holatida BUTUNLAY o'chirish — savatdagi checkbox'lar orqali
    // belgilangan savollar (question.js#permanentlyDeleteSelectedQuestions).
    // Literal "/bulk/permanent" — "/{id}/permanent"dan ANIQROQ deb
    // hisoblanadi (Spring'ning o'zi to'g'ri yo'naltiradi).
    @DeleteMapping("/api/question/bulk/permanent")
    @ResponseBody
    public ResponseEntity<?> permanentDeleteBulk(@RequestBody List<Long> ids) {
        try {
            int deleted = questionService.permanentlyDeleteQuestions(ids);
            return ResponseEntity.ok(Map.of("deleted", deleted));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
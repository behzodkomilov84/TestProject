package behzoddev.testproject.controller.api;

import behzoddev.testproject.dto.question.QuestionShortDto;
import behzoddev.testproject.dto.science.ScienceDto;
import behzoddev.testproject.dto.science.ScienceIdAndNameDto;
import behzoddev.testproject.dto.science.ScienceNameDto;
import behzoddev.testproject.dto.science.ScienceTrashDto;
import behzoddev.testproject.dto.topic.TopicIdAndNameDto;
import behzoddev.testproject.dto.topic.TopicNameDto;
import behzoddev.testproject.entity.Question;
import behzoddev.testproject.entity.Science;
import behzoddev.testproject.entity.Topic;
import behzoddev.testproject.exception.ErrorResponse;
import behzoddev.testproject.service.QuestionService;
import behzoddev.testproject.service.ScienceService;
import behzoddev.testproject.service.TopicService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.*;

@Controller
@RequiredArgsConstructor
public class ScienceController {

    private final ScienceService scienceService;
    private final TopicService topicService;
    private final QuestionService questionService;

    @GetMapping("/api/science")
    @ResponseBody
    public ResponseEntity<Set<ScienceIdAndNameDto>> getSciences() {
        Set<ScienceIdAndNameDto> scienceIdsAndNames = scienceService.getAllScienceIdAndNameDto();

        return ResponseEntity.ok(scienceIdsAndNames);
    }

    // Fanlar tartibini qayta belgilash ("⬆⬇" yoki A-Z/Z-A saralashdan
    // keyin) — to'liq yangi tartibdagi id ro'yxati.
    @PostMapping("/api/science/reorder")
    @ResponseBody
    public ResponseEntity<Void> reorderSciences(@RequestBody List<Long> orderedScienceIds) {
        scienceService.reorderSciences(orderedScienceIds);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/api/science/save")
    @ResponseBody
    public ResponseEntity<?> saveScience(@RequestBody Map<String, Object> payload) {

        // "new" — {name, fieldId} obyektlar ro'yxati (fieldId — IXTIYORIY,
        // Yo'nalish tanlanmagan bo'lsa null/berilmagan). Ilgari oddiy
        // String[] edi — Yo'nalish qo'shilishi bilan obyektga o'tkazildi
        // (science.js — yagona chaqiruvchi, orqaga moslikka hojat yo'q).
        var newSubjects = (List<Map<String, Object>>) payload.get("new");
        var needToUpdateSubjects = (List<Map<String, Object>>) payload.get("updated");

        List<Long> deletedScienceIds = new ArrayList<>();
        for (Object obj : (List<Object>) payload.get("deletedIds")) {
            deletedScienceIds.add(((Number) obj).longValue());
        }

        // Добавляем новые
        for (Map<String, Object> item : newSubjects) {
            String name = (String) item.get("name");
            Long fieldId = item.get("fieldId") == null ? null : ((Number) item.get("fieldId")).longValue();
            scienceService.saveScience(new ScienceNameDto(name, fieldId));
        }

        // Обновляем существующие
        for (Map<String, Object> item : needToUpdateSubjects) {
            Long id = ((Number) item.get("id")).longValue();
            String name = (String) item.get("name");
            scienceService.updateScienceName(id, name);
            // fieldId — science.js HAR DOIM joriy qiymatni yuboradi (nom
            // o'zgarmagan, faqat Yo'nalish o'zgargan holatlar ham shu
            // "updated" ro'yxatiga tushadi) — shu sabab shartsiz chaqiriladi.
            Long fieldId = item.get("fieldId") == null ? null : ((Number) item.get("fieldId")).longValue();
            scienceService.assignField(id, fieldId);
        }

        // Удаление
        for (Long id : deletedScienceIds) {
            scienceService.removeScience(id);
        }

        return ResponseEntity.ok(Map.of("message", "✅ Ma'lumotlar bazaga saqlandi!"));
    }

    @GetMapping("/science/full")
    public ResponseEntity<Set<ScienceDto>> getSciencesFull() {
        Set<ScienceDto> sciences = scienceService.getAllSciencesDto();

        return ResponseEntity.ok(sciences);
    }

    @GetMapping("/science/{scienceId}")
    public ResponseEntity<ScienceIdAndNameDto> getScienceNameById(@PathVariable Long scienceId) {
        ScienceIdAndNameDto scienceNameDto = scienceService.getScienceNameById(scienceId).orElseThrow();
        return ResponseEntity.ok(scienceNameDto);
    }

    @GetMapping("/science/{scienceId}/full")
    public ResponseEntity<ScienceDto> getScience(@PathVariable Long scienceId) {
        ScienceDto scienceDto = scienceService.getScienceById(scienceId).orElseThrow();
        return ResponseEntity.ok(scienceDto);
    }

    @PostMapping("/science")
    public ResponseEntity<?> createScience(@Valid @RequestBody ScienceNameDto scienceNameDto) {

        Optional<Science> existing = scienceService.getByName(scienceNameDto.name());
        if (existing.isPresent()) {
            ErrorResponse error = new ErrorResponse(
                    "Science with name '" + scienceNameDto.name() + "' already exists",
                    HttpStatus.CONFLICT.value()
            );
            return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
        }

        Science science = scienceService.saveScience(scienceNameDto);
        return ResponseEntity
                .created(URI.create("sciences/" + science.getId())
                ).body("Science with name '" + scienceNameDto.name() + "' was created");
    }

    @PostMapping("/science/{scienceId}/topic")
    public ResponseEntity<?> createTopic(
            @PathVariable Long scienceId,
            @Valid @RequestBody TopicNameDto topicNameDto
    ) {
        List<TopicIdAndNameDto> existingTopics =
                topicService.getTopicsByScienceId(scienceId);

        boolean exists = existingTopics.stream()
                .anyMatch(t -> t.name().equalsIgnoreCase(topicNameDto.name()));

        if (exists) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ErrorResponse(
                            "Topic with name '" + topicNameDto.name() + "' already exists",
                            HttpStatus.CONFLICT.value()
                    ));
        }

        Topic topic = topicService.saveTopic(scienceId, topicNameDto);

        return ResponseEntity.created(
                URI.create("science/" + scienceId + "/topic/" + topic.getId())
        ).build();
    }

    @PostMapping("topic/{topicId}")
    public ResponseEntity<?> createQuestion(@PathVariable Long topicId,
                                            @Valid @RequestBody QuestionShortDto newQuestion) {

        List<QuestionShortDto> existingQuestions =
                questionService.getQuestionsByTopicId(topicId);

        if (questionService.isQuestionWithAnswersExists(existingQuestions, newQuestion)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ErrorResponse(
                            "A question with such answers already exists.",
                            HttpStatus.CONFLICT.value()
                    ));
        }

        Question question = questionService.saveQuestion(topicId, newQuestion);

        return ResponseEntity.created(
                URI.create("science/" + scienceService.getScienceIdByTopicId(topicId)
                        + "/topic/" + topicId + "/question/" + question.getId())
        ).build();
    }

    @PutMapping("/science")
    public ResponseEntity<?> updateScience(@Valid @RequestBody Science science) {

        boolean scienceNameExist = scienceService.isScienceNameExist(science.getName());
        boolean scienceIdExist = scienceService.isScienceIdExist(science.getId());

        if (scienceIdExist && !scienceNameExist) {
            scienceService.saveScience(science);
            return ResponseEntity.noContent().build();
        }

        if (!scienceIdExist) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ErrorResponse(
                            "Science with id '" + science.getId() + "' is not exists",
                            HttpStatus.CONFLICT.value()
                    ));
        }

        if (scienceNameExist) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ErrorResponse(
                            "Science with name '" + science.getName() + "' is already exists",
                            HttpStatus.CONFLICT.value()
                    ));
        }
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/science")
    public ResponseEntity<Void> updateScienceName(@RequestParam Long id, @Valid @RequestParam String name) {
        scienceService.updateScienceName(id, name);
        return ResponseEntity.ok().build();
    }

    // "🔀 Yo'nalishga biriktirish" — science.js'da qator EDIT rejimida
    // Yo'nalish select'i o'zgartirilganda darhol chaqiriladi (Save to
    // DB'dan mustaqil). {"fieldId": null} — Yo'nalishdan chiqarish (unlink).
    @PatchMapping("/api/science/{scienceId}/field")
    @ResponseBody
    @PreAuthorize("hasAnyAuthority('ROLE_OWNER','ROLE_ADMIN')")
    public ResponseEntity<Void> assignScienceField(@PathVariable Long scienceId, @RequestBody Map<String, Long> body) {
        scienceService.assignField(scienceId, body.get("fieldId"));
        return ResponseEntity.ok().build();
    }

    // DIQQAT: ilgari @PreAuthorize'siz edi (istalgan login qilgan
    // foydalanuvchi chaqira olardi) — /topic, /question'dagi bilan bir
    // xil sabab bilan tuzatildi (bu controller'dagi boshqa amallar ham
    // hali himoyasiz — alohida ko'rib chiqilishi kerak).
    @DeleteMapping("/science/{scienceId}")
    @PreAuthorize("hasAnyAuthority('ROLE_OWNER','ROLE_ADMIN')")
    public ResponseEntity<Void> deleteScience(@PathVariable Long scienceId) {
        scienceService.removeScience(scienceId);
        return ResponseEntity.noContent().build();
    }

    // "O'chirilganlar savati" (fan darajasida).
    @GetMapping("/api/science/deleted")
    @ResponseBody
    @PreAuthorize("hasAnyAuthority('ROLE_OWNER','ROLE_ADMIN')")
    public ResponseEntity<List<ScienceTrashDto>> getDeletedSciences() {
        return ResponseEntity.ok(scienceService.getDeletedSciences());
    }

    @PostMapping("/api/science/{scienceId}/restore")
    @ResponseBody
    @PreAuthorize("hasAnyAuthority('ROLE_OWNER','ROLE_ADMIN')")
    public ResponseEntity<Void> restoreScience(@PathVariable Long scienceId) {
        scienceService.restoreScience(scienceId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/api/science/{scienceId}/permanent")
    @ResponseBody
    @PreAuthorize("hasAnyAuthority('ROLE_OWNER','ROLE_ADMIN')")
    public ResponseEntity<Void> permanentDeleteScience(@PathVariable Long scienceId) {
        scienceService.permanentlyDeleteScience(scienceId);
        return ResponseEntity.ok().build();
    }

    // DIQQAT: bu ikkala endpoint ilgari @PreAuthorize'siz edi (istalgan
    // login qilgan foydalanuvchi, hatto ROLE_USER ham, chaqira olardi —
    // /api/** prefiksiz bo'lgani uchun SecurityConfig'dagi ROLE_OWNER/
    // ROLE_ADMIN qoidasi ularga tegmasdi). Endi "O'chirilganlar savati"
    // (restore/permanent) qo'shilgani munosabati bilan tuzatildi —
    // boshqa barcha topic/question mutatsiya endpoint'lari bilan bir xil
    // ruxsat qoidasi.
    @DeleteMapping("/topic/{topicId}")
    @PreAuthorize("hasAnyAuthority('ROLE_OWNER','ROLE_ADMIN')")
    public ResponseEntity<Void> deleteTopic(@PathVariable Long topicId) {
        topicService.removeTopic(topicId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/question/{questionId}")
    @PreAuthorize("hasAnyAuthority('ROLE_OWNER','ROLE_ADMIN')")
    public ResponseEntity<Void> deleteQuestion(@PathVariable Long questionId) {
        questionService.deleteQuestion(questionId);
        return ResponseEntity.noContent().build();
    }
}

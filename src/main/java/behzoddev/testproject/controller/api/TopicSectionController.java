package behzoddev.testproject.controller.api;

import behzoddev.testproject.dto.section.TopicSectionIdAndNameDto;
import behzoddev.testproject.dto.section.TopicSectionNameDto;
import behzoddev.testproject.dto.section.TopicSectionTrashDto;
import behzoddev.testproject.entity.User;
import behzoddev.testproject.service.ScienceService;
import behzoddev.testproject.service.TopicSectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// "Bo'lim" (TopicSection) CRUD — TopicController/ScienceController bilan
// bir xil andoza (batched save: {new, updated, deletedIds}).
@Controller
@RequiredArgsConstructor
public class TopicSectionController {

    private final TopicSectionService topicSectionService;
    private final ScienceService scienceService;

    // ADMIN o'zi yaratmagan Fanning Bo'limlarini UMUMAN ko'rmasligi kerak
    // (foydalanuvchi so'rovi, 2026-09-08: "Boshqalarniki ko'rinmasin").
    @GetMapping("/api/topic-section")
    @ResponseBody
    public ResponseEntity<List<TopicSectionIdAndNameDto>> getSections(@RequestParam Long scienceId,
                                                                        @AuthenticationPrincipal User user) {
        scienceService.requireManageableScience(scienceId, user);
        return ResponseEntity.ok(topicSectionService.getSectionsByScienceId(scienceId));
    }

    @PostMapping("/api/topic-section/save")
    @ResponseBody
    public ResponseEntity<Object> saveSections(@RequestBody Map<Object, Object> payload, @AuthenticationPrincipal User user) {

        var newSections = (List<Map<Object, Object>>) payload.get("new");
        var needToUpdateSections = (List<Map<Object, Object>>) payload.get("updated");

        List<Long> deletedSectionIds = new ArrayList<>();
        for (Object obj : (List<Object>) payload.get("deletedIds")) {
            deletedSectionIds.add(((Number) obj).longValue());
        }

        for (Map<Object, Object> item : newSections) {
            Long scienceId = Long.parseLong(item.get("science_id").toString());
            String name = (String) item.get("name");
            topicSectionService.saveSection(scienceId, new TopicSectionNameDto(name), user);
        }

        for (Map<Object, Object> item : needToUpdateSections) {
            Long id = ((Number) item.get("id")).longValue();
            String name = (String) item.get("name");
            topicSectionService.updateSectionName(id, name, user);
        }

        for (Long id : deletedSectionIds) {
            topicSectionService.removeSection(id, user);
        }

        return ResponseEntity.ok(Map.of("message", "✅ Ma'lumotlar bazaga saqlandi!"));
    }

    @PostMapping("/api/topic-section/reorder")
    @ResponseBody
    public ResponseEntity<Void> reorder(@RequestParam Long scienceId, @RequestBody List<Long> orderedSectionIds,
                                          @AuthenticationPrincipal User user) {
        topicSectionService.reorderSections(scienceId, orderedSectionIds, user);
        return ResponseEntity.ok().build();
    }

    // Mavzuni bo'limga biriktirish/bo'shatish — topics.html'dagi Bo'lim
    // tanlash dropdown'i shu orqali ishlaydi ({"sectionId": 5} yoki
    // {"sectionId": null}).
    @PostMapping("/api/topic/{topicId}/section")
    @ResponseBody
    public ResponseEntity<Void> assignTopicSection(@PathVariable Long topicId, @RequestBody Map<String, Long> body,
                                                     @AuthenticationPrincipal User user) {
        topicSectionService.assignTopicToSection(topicId, body.get("sectionId"), user);
        return ResponseEntity.ok().build();
    }

    // "🗑️ Bo'sh bo'limlarni o'chirish" tugmasi — shu Fanda hech qanday
    // mavzuga biriktirilmagan BARCHA bo'limlarni bir yo'la o'chiradi.
    @DeleteMapping("/api/topic-section/empty")
    @ResponseBody
    public ResponseEntity<Map<String, Integer>> deleteEmptySections(@RequestParam Long scienceId,
                                                                       @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(Map.of("deleted", topicSectionService.deleteEmptySections(scienceId, user)));
    }

    // "O'chirilganlar savati" (Bo'lim darajasida, Fan ichida).
    @GetMapping("/api/topic-section/deleted")
    @ResponseBody
    public ResponseEntity<List<TopicSectionTrashDto>> getDeleted(@RequestParam Long scienceId,
                                                                    @AuthenticationPrincipal User user) {
        scienceService.requireManageableScience(scienceId, user);
        return ResponseEntity.ok(topicSectionService.getDeletedSections(scienceId));
    }

    @PostMapping("/api/topic-section/{id}/restore")
    @ResponseBody
    public ResponseEntity<Void> restore(@PathVariable Long id, @AuthenticationPrincipal User user) {
        topicSectionService.restoreSection(id, user);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/api/topic-section/{id}/permanent")
    @ResponseBody
    public ResponseEntity<Void> permanentDelete(@PathVariable Long id, @AuthenticationPrincipal User user) {
        topicSectionService.permanentlyDeleteSection(id, user);
        return ResponseEntity.ok().build();
    }
}

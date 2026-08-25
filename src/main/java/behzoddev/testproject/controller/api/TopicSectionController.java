package behzoddev.testproject.controller.api;

import behzoddev.testproject.dto.section.TopicSectionIdAndNameDto;
import behzoddev.testproject.dto.section.TopicSectionNameDto;
import behzoddev.testproject.service.TopicSectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
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

    @GetMapping("/api/topic-section")
    @ResponseBody
    public ResponseEntity<List<TopicSectionIdAndNameDto>> getSections(@RequestParam Long scienceId) {
        return ResponseEntity.ok(topicSectionService.getSectionsByScienceId(scienceId));
    }

    @PostMapping("/api/topic-section/save")
    @ResponseBody
    public ResponseEntity<Object> saveSections(@RequestBody Map<Object, Object> payload) {

        var newSections = (List<Map<Object, Object>>) payload.get("new");
        var needToUpdateSections = (List<Map<Object, Object>>) payload.get("updated");

        List<Long> deletedSectionIds = new ArrayList<>();
        for (Object obj : (List<Object>) payload.get("deletedIds")) {
            deletedSectionIds.add(((Number) obj).longValue());
        }

        for (Map<Object, Object> item : newSections) {
            Long scienceId = Long.parseLong(item.get("science_id").toString());
            String name = (String) item.get("name");
            topicSectionService.saveSection(scienceId, new TopicSectionNameDto(name));
        }

        for (Map<Object, Object> item : needToUpdateSections) {
            Long id = ((Number) item.get("id")).longValue();
            String name = (String) item.get("name");
            topicSectionService.updateSectionName(id, name);
        }

        for (Long id : deletedSectionIds) {
            topicSectionService.removeSection(id);
        }

        return ResponseEntity.ok(Map.of("message", "✅ Ma'lumotlar bazaga saqlandi!"));
    }

    @PostMapping("/api/topic-section/reorder")
    @ResponseBody
    public ResponseEntity<Void> reorder(@RequestParam Long scienceId, @RequestBody List<Long> orderedSectionIds) {
        topicSectionService.reorderSections(scienceId, orderedSectionIds);
        return ResponseEntity.ok().build();
    }

    // Mavzuni bo'limga biriktirish/bo'shatish — topics.html'dagi Bo'lim
    // tanlash dropdown'i shu orqali ishlaydi ({"sectionId": 5} yoki
    // {"sectionId": null}).
    @PostMapping("/api/topic/{topicId}/section")
    @ResponseBody
    public ResponseEntity<Void> assignTopicSection(@PathVariable Long topicId, @RequestBody Map<String, Long> body) {
        topicSectionService.assignTopicToSection(topicId, body.get("sectionId"));
        return ResponseEntity.ok().build();
    }

    // "🗑️ Bo'sh bo'limlarni o'chirish" tugmasi — shu Fanda hech qanday
    // mavzuga biriktirilmagan BARCHA bo'limlarni bir yo'la o'chiradi.
    @DeleteMapping("/api/topic-section/empty")
    @ResponseBody
    public ResponseEntity<Map<String, Integer>> deleteEmptySections(@RequestParam Long scienceId) {
        return ResponseEntity.ok(Map.of("deleted", topicSectionService.deleteEmptySections(scienceId)));
    }

    // "🗑️ Bo'lim + mavzularni birga o'chirish" — DARHOL (batch/saveToDb
    // orqali emas) ishlaydi, chunki QAYTARIB BO'LMAYDI (savollar bilan
    // birga butunlay o'chadi). Xatolik (masalan biror mavzu kursga
    // bog'langan) bo'lsa — {"error": "..."} qaytadi, frontend shuni
    // ko'rsatadi.
    @DeleteMapping("/api/topic-section/{id}/with-topics")
    @ResponseBody
    public ResponseEntity<Void> removeSectionWithTopics(@PathVariable Long id) {
        topicSectionService.removeSectionWithTopics(id);
        return ResponseEntity.ok().build();
    }
}

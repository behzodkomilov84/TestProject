package behzoddev.testproject.controller.api;

import behzoddev.testproject.dto.topic.TopicCourseLinkDto;
import behzoddev.testproject.dto.topic.TopicIdAndNameDto;
import behzoddev.testproject.dto.topic.TopicNameDto;
import behzoddev.testproject.service.TopicSectionService;
import behzoddev.testproject.service.TopicService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Controller
@RequiredArgsConstructor

public class TopicController {
    private final TopicService topicService;
    private final TopicSectionService topicSectionService;

    @GetMapping("/api/topic")
    public ResponseEntity<List<TopicIdAndNameDto>> getTopicsByScience(@RequestParam Long scienceId) {
        List<TopicIdAndNameDto> topicIdAndNameDtos = topicService.getTopicsByScienceId(scienceId);

        return ResponseEntity.ok(topicIdAndNameDtos);
    }

    @PostMapping("/api/topic/save")
//    @ResponseBody
    public ResponseEntity<Object> saveTopic(@RequestBody Map<Object, Object> payload) {

        var newTopics = (List<Map<Object, Object>>) payload.get("new");


        var needToUpdateTopics = (List<Map<Object, Object>>) payload.get("updated");

        List<Long> deletedScienceIds = new ArrayList<>();
        for (Object obj : (List<Object>) payload.get("deletedIds")) {
            deletedScienceIds.add(((Number) obj).longValue());
        }

        // Добавляем новые
        for (Map<Object, Object> item : newTopics) {

            Long scienceId = Long.parseLong(item.get("science_id").toString());

            String name = (String) item.get("name");
            Long sectionId = item.get("sectionId") != null
                    ? ((Number) item.get("sectionId")).longValue() : null;
            topicService.saveTopic(scienceId, new TopicNameDto(name), sectionId);
        }

        // Обновляем существующие
        for (Map<Object, Object> item : needToUpdateTopics) {
            Long id = ((Number) item.get("id")).longValue();
            String name = (String) item.get("name");
            topicService.updateTopic(id, name);
            if (item.containsKey("sectionId")) {
                Long sectionId = item.get("sectionId") != null
                        ? ((Number) item.get("sectionId")).longValue() : null;
                topicSectionService.assignTopicToSection(id, sectionId);
            }
        }

        // Удаление
        for (Long id : deletedScienceIds) {
            topicService.removeTopic(id);
        }

        return ResponseEntity.ok(Map.of("message", "✅ Ma'lumotlar bazaga saqlandi!"));
    }

    @GetMapping("/science/{scienceId}/topic/{topicId}")
    public ResponseEntity<TopicIdAndNameDto> getTopicByIds(@PathVariable Long scienceId, @PathVariable Long topicId) {
        TopicIdAndNameDto topicIdAndNameDto = topicService.getTopicByIds(scienceId, topicId);

        return ResponseEntity.ok(topicIdAndNameDto);
    }

    // Test yaratish formasidagi "🔗 Mavzuga havola qo'shish" tugmasi shu
    // orqali joriy mavzu qaysi kurs bo'limiga bog'langanini bilib oladi.
    // Bog'lanmagan bo'lsa — 404 (frontend tugmani yashiradi).
    @GetMapping("/api/topic/{topicId}/course-link")
    public ResponseEntity<TopicCourseLinkDto> getCourseLink(@PathVariable Long topicId) {
        return topicService.getCourseLinkForTopic(topicId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

}

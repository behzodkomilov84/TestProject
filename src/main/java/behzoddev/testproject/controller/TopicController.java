package behzoddev.testproject.controller;

import behzoddev.testproject.dto.TopicIdAndNameDto;
import behzoddev.testproject.dto.TopicNameDto;
import behzoddev.testproject.service.TopicService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Controller
@RequiredArgsConstructor

public class TopicController {
    private final TopicService topicService;

    @GetMapping("/api/topic")
    public ResponseEntity<Set<TopicIdAndNameDto>> getTopicsByScience(@RequestParam Long scienceId) {
        Set<TopicIdAndNameDto> topicIdAndNameDtos = topicService.getTopicsByScienceId(scienceId);

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
            topicService.saveTopic(scienceId, new TopicNameDto(name));
        }

        // Обновляем существующие
        for (Map<Object, Object> item : needToUpdateTopics) {
            Long id = ((Number) item.get("id")).longValue();
            String name = (String) item.get("name");
            topicService.updateTopic(id, name);
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

}

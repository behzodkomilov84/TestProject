package behzoddev.testproject.controller;

import behzoddev.testproject.dto.*;
import behzoddev.testproject.service.ScienceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

@RestController
@RequiredArgsConstructor
@RequestMapping("/sciences")
public class ScienceController {

    private final ScienceService scienceService;

    @GetMapping
    public ResponseEntity<Set<ScienceNameDto>> getSciences() {
        Set<ScienceNameDto> scienceNameDtos = scienceService.getAllScienceNameDto();

        return ResponseEntity.ok(scienceNameDtos);
    }

    @GetMapping("/full")
    public ResponseEntity<Set<ScienceDto>> getSciencesFull() {
        Set<ScienceDto> sciences = scienceService.getAllSciencesDto();

        return ResponseEntity.ok(sciences);
    }

    @GetMapping("/{scienceId}")
    public ResponseEntity<ScienceNameDto> getScienceNameById(@PathVariable Long scienceId) {
        ScienceNameDto scienceNameDto = scienceService.getScienceNameById(scienceId).orElseThrow();
        return ResponseEntity.ok(scienceNameDto);
    }

    @GetMapping("/{scienceId}/full")
    public ResponseEntity<ScienceDto> getScience(@PathVariable Long scienceId) {
        ScienceDto scienceDto = scienceService.getScienceById(scienceId).orElseThrow();
        return ResponseEntity.ok(scienceDto);
    }

    @GetMapping("/{scienceId}/topic")
    public ResponseEntity<Set<TopicNameDto>> getTopicsOfScience(@PathVariable Long scienceId) {
        Set<TopicNameDto> topicNameDtos = scienceService.getTopicsByScienceId(scienceId);

        return ResponseEntity.ok(topicNameDtos);
    }

    @GetMapping("/{scienceId}/topic/{topicId}")
    public ResponseEntity<TopicNameDto> getTopicByIds(@PathVariable Long scienceId, @PathVariable Long topicId) {
        TopicNameDto topicNameDto = scienceService.getTopicByIds(scienceId, topicId);

        return ResponseEntity.ok(topicNameDto);
    }

    @GetMapping("/{scienceId}/topic/{topicId}/questions")
    public ResponseEntity<List<QuestionDto>> getQuestionsByIds(@PathVariable Long scienceId, @PathVariable Long topicId) {
        List<QuestionDto> questionDto = scienceService.getQuestionsByIds(scienceId, topicId);

        return ResponseEntity.ok(questionDto);
    }
}

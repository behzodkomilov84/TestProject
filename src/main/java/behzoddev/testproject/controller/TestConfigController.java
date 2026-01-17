package behzoddev.testproject.controller;

import behzoddev.testproject.dao.AnswerRepository;
import behzoddev.testproject.dao.QuestionRepository;
import behzoddev.testproject.dao.ScienceRepository;
import behzoddev.testproject.dao.TopicRepository;
import behzoddev.testproject.dto.ScienceIdAndNameDto;
import behzoddev.testproject.dto.TopicIdsDto;
import behzoddev.testproject.dto.TopicWithQuestionCountDto;
import behzoddev.testproject.mapper.QuestionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tests")
@RequiredArgsConstructor
public class TestConfigController {

    private final ScienceRepository scienceRepository;
    private final TopicRepository topicRepository;
    private final QuestionRepository questionRepository;

    // 1. Subjects
    @GetMapping("/sciences")
    @PreAuthorize("isAuthenticated()")
    public List<ScienceIdAndNameDto> getSciences() {
        return scienceRepository.findAll()
                .stream()
                .map(s -> new ScienceIdAndNameDto(s.getId(), s.getName()))
                .toList();
    }

    // 2. Topics of selected science
    @GetMapping("/science/{scienceId}/topics")
    @PreAuthorize("isAuthenticated()")
    public List<TopicWithQuestionCountDto> getTopics(@PathVariable Long scienceId) {
        List<TopicWithQuestionCountDto> topicsWithQuestionCount =
                topicRepository.getTopicsWithQuestionCount(scienceId);
        return topicsWithQuestionCount;
    }

    // 3. Max questions
    @PostMapping("/max")
    @PreAuthorize("isAuthenticated()")
    public int getMax(@RequestBody TopicIdsDto dto) {
        return questionRepository.countByTopicIds(dto.topicIds());
    }


}


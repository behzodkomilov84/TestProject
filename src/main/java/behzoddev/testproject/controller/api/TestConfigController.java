package behzoddev.testproject.controller.api;

import behzoddev.testproject.dao.QuestionRepository;
import behzoddev.testproject.dao.ScienceRepository;
import behzoddev.testproject.dao.TopicRepository;
import behzoddev.testproject.dto.MaxRequestDto;
import behzoddev.testproject.dto.ScienceIdAndNameDto;
import behzoddev.testproject.dto.TopicWithQuestionCountDto;
import behzoddev.testproject.entity.User;
import behzoddev.testproject.service.ScienceService;
import behzoddev.testproject.service.TopicService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tests")
@RequiredArgsConstructor
public class TestConfigController {

    private final ScienceRepository scienceRepository;
    private final TopicRepository topicRepository;
    private final QuestionRepository questionRepository;
    private final ScienceService scienceService;
    private final TopicService topicService;

    // 1. Subjects
    @GetMapping("/sciences")
    @PreAuthorize("isAuthenticated()")
    public List<ScienceIdAndNameDto> getSciences() {
        return scienceService.getSciences();
    }

    // 2. Topics of selected science
    @GetMapping("/science/{scienceId}/topics")
    @PreAuthorize("isAuthenticated()")
    public List<TopicWithQuestionCountDto> getTopics(@PathVariable Long scienceId) {
        return topicService.getTopicsWithQuestionCount(scienceId);
    }

    // 3. Max questions
    @PostMapping("/max")
    @PreAuthorize("isAuthenticated()")
    public int getMax(
            @RequestBody MaxRequestDto req,
            @AuthenticationPrincipal User user
    ) {
        if ("hard".equals(req.testMode())) {

            return questionRepository
                    .findHardForUser(user.getId(), req.topicIds())
                    .size();
        }

        return questionRepository.countByTopicIds(req.topicIds());
    }


}


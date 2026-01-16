package behzoddev.testproject.controller;

import behzoddev.testproject.dto.StartTestDto;
import behzoddev.testproject.dto.TestQuestionDto;
import behzoddev.testproject.service.TestSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/test-session")
@RequiredArgsConstructor
public class TestSessionController {

    private final TestSessionService testSessionService;

    @PostMapping("/start")
    public List<TestQuestionDto> start(@RequestBody StartTestDto request) {
        return testSessionService.startTest(request.topicIds(), request.limit());
    }

    @PostMapping("/finish")
    public int finish(@RequestBody Map<Long, Long> answers) {
        return testSessionService.checkAnswers(answers);
    }

}

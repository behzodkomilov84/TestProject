package behzoddev.testproject.controller.page;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequiredArgsConstructor
public class QuestionPageController {

    @GetMapping("/question")
    public String getQuestionPage(@RequestParam("topicId") Long topicId,
                                  Model model) {
        model.addAttribute("topicId", topicId);

        return "question"; // question.html
    }

    @GetMapping("/question/{topicId}/create-test-form")
    public String getCreateTestPage(@PathVariable Long topicId,
                                    Model model) {
        model.addAttribute("topicId", topicId);

        return "test-form"; // test-form.html
    }

}

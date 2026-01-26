package behzoddev.testproject.controller.page;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequiredArgsConstructor
public class TopicPageController {
    @GetMapping("/topics")
    public String getTopicsPage(@RequestParam("scienceId") Long scienceId,
                                Model model) {
        model.addAttribute("scienceId", scienceId);

        return "topics"; // topics.html
    }


}

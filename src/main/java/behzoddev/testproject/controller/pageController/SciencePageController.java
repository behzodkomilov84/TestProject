package behzoddev.testproject.controller.pageController;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SciencePageController {
    @GetMapping("/science")
    public String getSciencePage() {
        return "science"; // science.html
    }
}

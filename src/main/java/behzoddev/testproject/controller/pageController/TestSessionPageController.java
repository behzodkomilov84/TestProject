package behzoddev.testproject.controller.pageController;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class TestSessionPageController {

    @GetMapping("/testSession")
    public String startTestPage() {
        return "testSession";
    }


}

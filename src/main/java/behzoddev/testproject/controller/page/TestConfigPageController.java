package behzoddev.testproject.controller.page;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class TestConfigPageController {
    @GetMapping("/testConfigPage")
    public String getTestConfigPage() {
        return "testConfigPage";
    }
}

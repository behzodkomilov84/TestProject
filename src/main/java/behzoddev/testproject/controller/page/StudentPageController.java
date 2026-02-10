package behzoddev.testproject.controller.page;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class StudentPageController {
    @GetMapping("/student")
    public String getPupilPage() {
        return "student";
    }

}

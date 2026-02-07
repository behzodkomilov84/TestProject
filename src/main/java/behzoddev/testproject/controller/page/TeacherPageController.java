package behzoddev.testproject.controller.page;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class TeacherPageController {
    @GetMapping("/teacher")
    public String getTeacherPage() {
        return "teacher";
    }
}

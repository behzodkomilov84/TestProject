package behzoddev.testproject.controller.page;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class AdminAssignmentPageController {

    @GetMapping("/admin-assignment")
    public String adminAssignmentPage() {
        return "admin-assignment"; //admin-assignment.html
    }
}

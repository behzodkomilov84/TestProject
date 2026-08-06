package behzoddev.testproject.controller.page;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

// Foydalanish shartlari va Maxfiylik siyosati — hammaga (ro'yxatdan
// o'tmagan mehmonlarga ham) ochiq statik sahifalar.
@Controller
public class LegalPageController {

    @GetMapping("/terms")
    public String terms() {
        return "terms";
    }

    @GetMapping("/privacy")
    public String privacy() {
        return "privacy";
    }
}

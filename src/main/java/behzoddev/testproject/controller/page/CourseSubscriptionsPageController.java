package behzoddev.testproject.controller.page;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

// Kurslarga obuna berish — barcha kurslar bo'yicha obunalarni bir joyda
// boshqarish sahifasi (OWNER uchun): obuna berish (foydalanuvchini
// qidirib topish bilan), so'rovlarni tasdiqlash/rad etish, bekor qilish.
// Avval bu funksiyalar har bir kursning o'z sahifasida (courseDetail)
// alohida-alohida tarqoq edi — endi shu yerga jamlangan.
@Controller
public class CourseSubscriptionsPageController {

    @GetMapping("/courses/subscriptions")
    @PreAuthorize("hasAuthority('ROLE_OWNER')")
    public String openSubscriptionsPage(Model model) {
        model.addAttribute("role", "OWNER");
        return "courseSubscriptions";
    }
}

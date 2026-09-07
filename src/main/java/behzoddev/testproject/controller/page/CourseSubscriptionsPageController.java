package behzoddev.testproject.controller.page;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

// Kurslarga obuna berish — obunalarni boshqarish sahifasi: obuna berish
// (foydalanuvchini qidirib topish bilan), so'rovlarni tasdiqlash/rad
// etish, bekor qilish. Avval bu funksiyalar har bir kursning o'z
// sahifasida (courseDetail) alohida-alohida tarqoq edi — endi shu yerga
// jamlangan. ROLE_OWNER — cheklovsiz BARCHA kurslar; ROLE_ADMIN (kurs
// muallifi) ham kira oladi, lekin faqat O'ZI yaratgan kurslarning
// obunalarini ko'radi/boshqaradi (CourseSubscriptionService — foydalanuvchi
// so'rovi, 2026-09-07: "билдиришномалар фақат шу админнинг ўзига келсин.
// OWNER учун чеклов йўқ").
@Controller
public class CourseSubscriptionsPageController {

    @GetMapping("/courses/subscriptions")
    @PreAuthorize("hasAnyAuthority('ROLE_OWNER','ROLE_ADMIN')")
    public String openSubscriptionsPage(Model model, Authentication authentication) {
        List<String> authorities = authentication.getAuthorities().stream()
                .map(Object::toString).toList();
        model.addAttribute("role", authorities.contains("ROLE_OWNER") ? "ROLE_OWNER" : "ROLE_ADMIN");
        return "courseSubscriptions";
    }
}

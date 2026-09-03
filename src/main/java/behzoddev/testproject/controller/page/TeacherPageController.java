package behzoddev.testproject.controller.page;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

// Avval /teacher — bitta sahifada 3 ta mustaqil vazifa (Guruhlar/Savollar
// to'plami/Topshiriq berish), 3-panelli "workbench" ko'rinishida edi —
// mobilda amalda ishlatib bo'lmas edi (haqiqiy foydalanuvchi tekshiruvi:
// sidebar kengligi mobilda o'zgarmasdi, Fan/Mavzu tanlash qatori
// pastma-past tushmasdi). Endi 3 ta alohida, to'liq kenglikdagi sahifaga
// bo'lingan (har biri navbar submenyusida). Eski "/teacher" havolasi
// (Telegram deep-link'lar ham) buzilmasin deb — /teacher/groups'ga
// yo'naltiriladi.
@Controller
public class TeacherPageController {

    @GetMapping("/teacher")
    public String legacyRedirect() {
        return "redirect:/teacher/groups";
    }

    @GetMapping("/teacher/groups")
    public String groupsPage() {
        return "teacher-groups";
    }

    @GetMapping("/teacher/builder")
    public String builderPage() {
        return "teacher-builder";
    }

    // "role" — /teacher/assign'da OWNER'ni ADMIN'dan farqlash uchun kerak:
    // OWNER "Savollar to'plami" tanlovida BARCHA o'qituvchilarning
    // to'plamlarini ko'rishi/topshiriq sifatida berishi mumkin, ADMIN esa
    // FAQAT o'zinikini (teacher-assign.js, CourseCatalogPageController
    // bilan bir xil "primaryRole" andozasi).
    @GetMapping("/teacher/assign")
    public String assignPage(Model model, Authentication authentication) {
        model.addAttribute("role", primaryRole(authentication));
        return "teacher-assign";
    }

    // "Barcha savollar to'plamlari" — FAQAT ROLE_OWNER (API darajasida
    // @PreAuthorize bilan tekshiriladi — TeacherController.
    // getAllQuestionSetsForOwner; sahifaning o'zi ADMIN uchun ham
    // ochiladi, lekin API 403 qaytaradi, teacher-all-sets.js shuni
    // ko'rsatadi — boshqa sahifalar bilan bir xil andoza).
    @GetMapping("/teacher/all-sets")
    public String allSetsPage() {
        return "teacher-all-sets";
    }

    private String primaryRole(Authentication authentication) {
        var authorities = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        if (authorities.contains("ROLE_OWNER")) return "ROLE_OWNER";
        if (authorities.contains("ROLE_ADMIN")) return "ROLE_ADMIN";
        return "UNKNOWN";
    }
}

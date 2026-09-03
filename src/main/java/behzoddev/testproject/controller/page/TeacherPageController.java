package behzoddev.testproject.controller.page;

import org.springframework.stereotype.Controller;
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

    @GetMapping("/teacher/assign")
    public String assignPage() {
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
}

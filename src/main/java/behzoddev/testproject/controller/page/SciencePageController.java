package behzoddev.testproject.controller.page;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SciencePageController {

    // "TEST BOSHQARUVI" navbar tugmasi ENDI shu sahifaga olib boradi
    // (foydalanuvchi so'rovi, 2026-09-05: kurslardagi kabi avval
    // Yo'nalishlar, keyin ularning Bo'limlari — coursesCatalog bilan bir
    // xil ierarxiya: Yo'nalish -> Bo'lim -> Mavzu -> Dars).
    @GetMapping("/science/fields")
    @PreAuthorize("hasAnyAuthority('ROLE_OWNER', 'ROLE_ADMIN')")
    public String getScienceFieldsPage() {
        return "science-fields"; // science-fields.html
    }

    // ?fieldId=<id> bo'lsa — faqat shu Yo'nalishning Bo'limlari (science.js
    // window.location.search'dan o'qiydi, alohida @RequestParam shart
    // emas). fieldId'siz chaqirilsa — orqaga moslik uchun BARCHA Bo'limlar
    // (eski xatti-harakat, masalan to'g'ridan-to'g'ri bookmark qilingan
    // bo'lsa).
    @GetMapping("/science")
    @PreAuthorize("hasAnyAuthority('ROLE_OWNER', 'ROLE_ADMIN')")
    public String getSciencePage() {
        return "science"; // science.html
    }
}

package behzoddev.testproject.controller.page;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

// Kurslar katalogi (hammaga ochiq) va kurs sahifasi (dastur/curriculum + bo'lim ko'rish).
@Controller
public class CourseCatalogPageController {

    @GetMapping("/courses")
    public String openCatalog(Model model, Authentication authentication) {
        model.addAttribute("role", primaryRole(authentication));
        return "coursesCatalog";
    }

    // "O'chirilganlar savati" — literal yo'l, "/courses/{id}" pastdagi
    // path-variable'dan Spring tomonidan avtomatik ustun qo'yiladi.
    @GetMapping("/courses/trash")
    public String openTrash(Model model, Authentication authentication) {
        model.addAttribute("role", primaryRole(authentication));
        return "courseTrash";
    }

    @GetMapping("/courses/{id}")
    public String openCourseDetail(@PathVariable Long id, Model model, Authentication authentication) {
        model.addAttribute("role", primaryRole(authentication));
        model.addAttribute("courseId", id);
        return "courseDetail";
    }

    @GetMapping("/courses/{courseId}/sections/{sectionId}")
    public String openSectionView(
            @PathVariable Long courseId,
            @PathVariable Long sectionId,
            Model model,
            Authentication authentication
    ) {
        model.addAttribute("role", primaryRole(authentication));
        model.addAttribute("courseId", courseId);
        model.addAttribute("sectionId", sectionId);
        return "courseSectionView";
    }

    // ADMIN endi o'zi yaratgan kurslarni yarata/boshqara oladi (OWNER esa —
    // barchasini), shuning uchun frontend'ga ROLE_ADMIN ekanini ham bilish
    // kerak (masalan "+ Yangi kurs" tugmasini ko'rsatish uchun). OWNER
    // ustuvor — ikkalasi ham bo'lsa, OWNER qaytariladi.
    private String primaryRole(Authentication authentication) {
        var authorities = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        if (authorities.contains("ROLE_OWNER")) return "ROLE_OWNER";
        if (authorities.contains("ROLE_ADMIN")) return "ROLE_ADMIN";
        return "UNKNOWN";
    }
}

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

    private String primaryRole(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter("ROLE_OWNER"::equals)
                .findFirst()
                .orElse("UNKNOWN");
    }
}

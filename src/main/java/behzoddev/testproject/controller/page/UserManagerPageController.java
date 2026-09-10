package behzoddev.testproject.controller.page;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class UserManagerPageController {


    // Bu sahifalarga @PreAuthorize orqali faqat ROLE_OWNER kira oladi, lekin
    // dual-role tufayli foydalanuvchida boshqa rollar ham bo'lishi mumkin
    // (masalan ROLE_ADMIN). Shu sabab "findFirst()" o'rniga aynan
    // ROLE_OWNER'ni qidiramiz — aks holda Set tartibi tasodifiy bo'lgani
    // uchun boshqa rol birinchi chiqib, JS'dagi tekshiruv xato ishlashi mumkin edi.
    @GetMapping("/users")
    @PreAuthorize("hasAuthority('ROLE_OWNER')")
    public String openUserManagerPage(Model model, Authentication authentication) {
        model.addAttribute("role", primaryOwnerRole(authentication));
        return "userManagerPage"; // Thymeleaf шаблон userManagerPage.html
    }

    // "/users" sahifasidan ajratilgan (foydalanuvchi so'rovi, 2026-09-06:
    // barcha users ustunlari qo'shilgach jadval juda ko'p ustunli bo'lib
    // qoldi — "Admin obunalari" va "Rol tarixi" alohida sahifalarga
    // ko'chirildi).
    @GetMapping("/admin-subscriptions")
    @PreAuthorize("hasAuthority('ROLE_OWNER')")
    public String openAdminSubscriptionsPage(Model model, Authentication authentication) {
        model.addAttribute("role", primaryOwnerRole(authentication));
        return "adminSubscriptionsPage";
    }

    @GetMapping("/role-audit-log")
    @PreAuthorize("hasAuthority('ROLE_OWNER')")
    public String openRoleAuditLogPage(Model model, Authentication authentication) {
        model.addAttribute("role", primaryOwnerRole(authentication));
        return "roleAuditLogPage";
    }

    // "⚙️ To'lov sozlamalari" — /admin-subscriptions'dan ajratildi
    // (foydalanuvchi so'rovi, 2026-09-10: "bu faqat admin uchun
    // bo'lmasa, barcha to'lovlar uchun bo'lsa, bu yerdan olib, alohida
    // ⚙️ Sozlamalar tugmasi bilan OWNER PANEL ga joyla" — Click'ning
    // minimal tranzaksiya summasi ADMIN-rol VA kurs to'lovlarining
    // IKKALASIGA ham tegishli).
    @GetMapping("/payment-settings")
    @PreAuthorize("hasAuthority('ROLE_OWNER')")
    public String openPaymentSettingsPage(Model model, Authentication authentication) {
        model.addAttribute("role", primaryOwnerRole(authentication));
        return "paymentSettingsPage";
    }

    private String primaryOwnerRole(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter("ROLE_OWNER"::equals)
                .findFirst()
                .orElse("UNKNOWN");
    }

}

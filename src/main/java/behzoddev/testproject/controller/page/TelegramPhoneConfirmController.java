package behzoddev.testproject.controller.page;

import behzoddev.testproject.dao.UserRepository;
import behzoddev.testproject.entity.User;
import behzoddev.testproject.service.PhoneNumberService;
import behzoddev.testproject.service.UserServiceImpl;
import behzoddev.testproject.telegram.service.TelegramWidgetLoginService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

// "Telegram orqali kirish"da HAR SAFAR (hisobda allaqachon bor bo'lsa
// ham) telefon raqamni so'rash/tasdiqlash bosqichi — MAJBURIY
// (foydalanuvchi so'rovi, 2026-09-06: "Har safar telegram orqali
// kirishda telefon so'rasin"). Haqiqiy autentifikatsiya (SecurityContext)
// shu bosqich MUVAFFAQIYATLI yakunlangandan KEYIN o'rnatiladi —
// TelegramWidgetLoginController'da resolveUser() dan keyin darhol emas,
// shu oraliq sahifaga (permitAll) yo'naltiriladi, foydalanuvchi ID'si
// vaqtincha sessiyada (PENDING_USER_SESSION_KEY) saqlanadi.
@Slf4j
@Controller
@RequiredArgsConstructor
public class TelegramPhoneConfirmController {

    public static final String PENDING_USER_SESSION_KEY = "telegram_pending_user_id";

    private final UserRepository userRepository;
    private final PhoneNumberService phoneNumberService;
    private final UserServiceImpl userServiceImpl;

    @GetMapping("/telegram-phone-confirm")
    public String showForm(HttpServletRequest request, Model model) {
        User user = pendingUser(request);
        if (user == null) {
            return "redirect:/login";
        }

        model.addAttribute("countries", phoneNumberService.listCountries());
        model.addAttribute("existingPhoneCountryIso", phoneNumberService.regionOf(user.getPhoneNumber()));
        model.addAttribute("existingPhoneNational", phoneNumberService.nationalNumberOf(user.getPhoneNumber()));
        return "telegramPhoneConfirm";
    }

    // Haqiqiy topilgan bug (2026-09-06): foydalanuvchi Telegram orqali
    // kirsa-yu, keyin "🔌 Uzish" bilan uzsa, keyingi urinishda
    // findByTelegramId topolmasligi sababli HAR SAFAR YANGI "tg_..."
    // hisob yaratilardi — garchi u aslida telefon raqami bo'yicha
    // ALLAQACHON mavjud (masalan an'anaviy ro'yxatdan o'tgan) hisobga
    // tegishli bo'lsa ham. Endi: agar shu telefon raqam BOSHQA hisobda
    // bo'lsa — O'SHA hisobga ulanamiz, shu oqimda hozirgina yaratilgan
    // "bo'sh" (email'siz, "tg_..." nomli) hisob esa xavfsiz o'chiriladi
    // ("Yangi foydalanuvchi yaratmasdan" — foydalanuvchi so'rovi).
    @Transactional
    @PostMapping("/telegram-phone-confirm")
    public String submit(@RequestParam String phoneCountry,
                          @RequestParam String phoneNumber,
                          HttpServletRequest request, HttpServletResponse response,
                          RedirectAttributes redirectAttributes) {
        User pendingUser = pendingUser(request);
        if (pendingUser == null) {
            return "redirect:/login";
        }

        if (phoneNumber == null || phoneNumber.isBlank()) {
            redirectAttributes.addFlashAttribute("phoneError", "❌ Telefon raqamni kiriting.");
            return "redirect:/telegram-phone-confirm";
        }

        String normalized;
        try {
            normalized = phoneNumberService.normalize(phoneCountry, phoneNumber);
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("phoneError", e.getMessage());
            return "redirect:/telegram-phone-confirm";
        }

        User user = resolveByPhoneOrKeepPending(pendingUser, normalized);

        user.setPhoneNumber(normalized);
        userRepository.save(user);

        request.getSession(true).removeAttribute(PENDING_USER_SESSION_KEY);

        var authentication = new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        new HttpSessionSecurityContextRepository().saveContext(context, request, response);

        log.info("Telegram orqali kirish: telefon tasdiqlandi, user={}", user.getUsername());
        return "redirect:/index";
    }

    private User resolveByPhoneOrKeepPending(User pendingUser, String normalizedPhone) {
        // Optional emas List — phone_number ustunida UNIQUE cheklov yo'q,
        // bir nechta hisob bir xil raqamga ega bo'lishi mumkin (haqiqiy
        // topilgan xato, 2026-09-06: "Query did not return a unique
        // result"). Shu holatda "haqiqiy" (Telegram orqali avtomatik
        // yaratilmagan) hisobni ustuvor tanlaymiz.
        List<User> matches = userRepository.findAllByPhoneNumber(normalizedPhone).stream()
                .filter(u -> !u.getId().equals(pendingUser.getId()))
                .toList();

        if (matches.isEmpty()) {
            return pendingUser;
        }

        User target = matches.stream()
                .filter(u -> !TelegramWidgetLoginService.isFreshTelegramPlaceholder(u))
                .findFirst()
                .orElse(matches.get(0));

        // Faqat "hozirgina, shu Telegram oqimida, hech qanday boshqa
        // ma'lumotsiz yaratilgan" hisobni "tashlab yuboramiz" — real
        // (masalan email'i bor) hisobni HECH QACHON o'chirmaymiz, faqat
        // "bog'lash imkonsiz" xabarini beramiz.
        if (!TelegramWidgetLoginService.isFreshTelegramPlaceholder(pendingUser)) {
            return pendingUser;
        }

        if (target.getTelegramId() == null) {
            target.setTelegramId(pendingUser.getTelegramId());
            target.setTelegramUsername(pendingUser.getTelegramUsername());
        }
        if (target.getAvatarUrl() == null) target.setAvatarUrl(pendingUser.getAvatarUrl());
        if (target.getFirstName() == null) target.setFirstName(pendingUser.getFirstName());
        if (target.getLastName() == null) target.setLastName(pendingUser.getLastName());

        log.info("Telegram orqali kirish: telefon {} bo'yicha mavjud hisobga ({}) ulandi, vaqtinchalik hisob ({}) o'chirildi",
                normalizedPhone, target.getUsername(), pendingUser.getUsername());
        // FK RESTRICT jadvallarni oldindan tozalamasdan to'g'ridan-to'g'ri
        // o'chirish 409 bilan tugaydi (haqiqiy topilgan bug, 2026-09-06:
        // "Bu amalni bajarib bo'lmadi — bog'liq ma'lumotlar mavjud" — hatto
        // "yangi" ko'ringan hisobda ham avvalgi urinishlardan bildirishnoma
        // va h.k. qoldiq bo'lishi mumkin edi).
        userServiceImpl.deleteFkRestrictedRowsBeforeUserDelete(pendingUser.getId());
        userRepository.delete(pendingUser);

        return target;
    }

    private User pendingUser(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        Long pendingUserId = session != null ? (Long) session.getAttribute(PENDING_USER_SESSION_KEY) : null;
        if (pendingUserId == null) {
            return null;
        }
        return userRepository.findById(pendingUserId).orElse(null);
    }
}

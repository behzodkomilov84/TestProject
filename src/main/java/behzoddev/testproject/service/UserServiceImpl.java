package behzoddev.testproject.service;

import behzoddev.testproject.dao.CourseFieldRepository;
import behzoddev.testproject.dao.CourseRepository;
import behzoddev.testproject.dao.CourseSectionProgressRepository;
import behzoddev.testproject.dao.CourseSubscriptionRepository;
import behzoddev.testproject.dao.EmailVerificationCodeRepository;
import behzoddev.testproject.dao.PasswordResetCodeRepository;
import behzoddev.testproject.dao.PaymentOrderRepository;
import behzoddev.testproject.dao.RoleAuditLogRepository;
import behzoddev.testproject.dao.RoleRepository;
import behzoddev.testproject.dao.SubscriptionRepository;
import behzoddev.testproject.dao.TelegramAutoLoginTokenRepository;
import behzoddev.testproject.dao.TelegramLinkCodeRepository;
import behzoddev.testproject.dao.TestSessionRepository;
import behzoddev.testproject.dao.UserRepository;
import behzoddev.testproject.entity.Course;
import behzoddev.testproject.entity.CourseField;
import behzoddev.testproject.dto.user.ChangeRoleDto;
import behzoddev.testproject.dto.user.LoginDto;
import behzoddev.testproject.dto.user.RegisterDto;
import behzoddev.testproject.dto.user.UpdateUserDto;
import behzoddev.testproject.dto.user.UserDto;
import behzoddev.testproject.entity.Role;
import behzoddev.testproject.entity.User;
import behzoddev.testproject.entity.enums.RoleAuditAction;
import behzoddev.testproject.entity.enums.RoleAuditSource;
import behzoddev.testproject.exception.PasswordsDoNotMatchException;
import behzoddev.testproject.exception.UserAlreadyExistsException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional
public class UserServiceImpl implements UserDetailsService, UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final NotificationService notificationService;
    private final RoleAuditService roleAuditService;
    private final EmailVerificationService emailVerificationService;
    private final PhoneNumberService phoneNumberService;
    // Foydalanuvchini o'chirishdan OLDIN tozalanadigan "yengil" (ephemeral/
    // audit) jadvallar — deleteUser() ichida. Har biri FK RESTRICT bo'lgani
    // uchun (haqiqiy topilgan bug, 2026-09-06 — avval "notifications",
    // keyin "role_audit_logs" bilan bitta-bittalab topilgan).
    private final RoleAuditLogRepository roleAuditLogRepository;
    private final EmailVerificationCodeRepository emailVerificationCodeRepository;
    private final PasswordResetCodeRepository passwordResetCodeRepository;
    private final TelegramAutoLoginTokenRepository telegramAutoLoginTokenRepository;
    private final TelegramLinkCodeRepository telegramLinkCodeRepository;
    private final CourseSectionProgressRepository courseSectionProgressRepository;
    private final CourseRepository courseRepository;
    private final CourseFieldRepository courseFieldRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final CourseSubscriptionRepository courseSubscriptionRepository;
    private final PaymentOrderRepository paymentOrderRepository;
    private final TestSessionRepository testSessionRepository;

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Username '" + username + "' not found"));
    }


    @Override
    @Transactional
    public void register(RegisterDto dto) {

        // 1. Проверка существования пользователя
        if (userRepository.existsByUsername(dto.username())) {
            throw new UserAlreadyExistsException(dto.username());
        }

        // 1.1 Email ENDI IXTIYORIY (foydalanuvchi so'rovi: ko'pchilikda email
        // yo'q yoki o'zi login/parolini bilmaydi). Kiritilgan bo'lsa —
        // hozirgidek unikal bo'lishi va tasdiqlash kodi (emailga) yuborilishi
        // shart; bo'sh bo'lsa — pastda akkaunt TASDIQLASHSIZ darhol
        // faollashtiriladi (vaqtinchalik yechim — kelgusida SMS orqali
        // tasdiqlashga almashtiriladi).
        boolean hasEmail = dto.email() != null && !dto.email().isBlank();

        if (hasEmail && userRepository.existsByEmail(dto.email())) {
            throw new IllegalArgumentException("❌Bu email allaqachon ro'yxatdan o'tgan.");
        }

        // 2. Проверка паролей
        if (!dto.password().equals(dto.confirmPassword())) {
            throw new PasswordsDoNotMatchException("Passwords do not match");
        }

        // 2.1 Ism/Familiya/Ish-o'qish joyi/Lavozim/Telefon — barchasi
        // "to'ldirilishi shart" (foydalanuvchi so'rovi, 2026-09-06 va
        // 2026-09-07: avval workplace/jobTitle bu yerdan olib tashlangan
        // edi — endi qaytadan MAJBURIY qilindi, "registratsiya
        // formasiga qo'shish kerak"). Email hamon ixtiyoriyligicha qoladi.
        // Bu tekshiruv faqat KLASSIK (username/parol) ro'yxatdan o'tishga
        // tegishli — Telegram/Google/Facebook orqali kirish bu metoddan
        // umuman o'tmaydi, shu sabab o'sha foydalanuvchilar uchun bu
        // maydonlar kursga kirishda profil-to'ldirish modali orqali
        // so'raladi (profile-gate.js — u firstName/lastName/workplace/
        // jobTitle/phoneNumber'ning barchasini tekshiradi, xohlagan yo'l
        // bilan ro'yxatdan o'tgan bo'lsa ham bir xil talab ta'minlanishi uchun).
        if (isBlank(dto.firstName())) {
            throw new IllegalArgumentException("❌Ism bo'sh bo'lishi mumkin emas.");
        }
        if (isBlank(dto.lastName())) {
            throw new IllegalArgumentException("❌Familiya bo'sh bo'lishi mumkin emas.");
        }
        if (isBlank(dto.workplace())) {
            throw new IllegalArgumentException("❌Ish yoki o'qish joyingizni kiriting.");
        }
        if (isBlank(dto.jobTitle())) {
            throw new IllegalArgumentException("❌Lavozimingizni kiriting.");
        }
        if (isBlank(dto.phoneNumber())) {
            throw new IllegalArgumentException("❌Telefon raqamingizni kiriting.");
        }

        // 3. Получаем роль USER (роль должна быть создана в БД через Liquibase)
        Role userRole = roleRepository.findByRoleName("ROLE_USER")
                .orElseThrow(() -> new RuntimeException("ROLE_USER not found in database"));

        // 4. Создаём пользователя с ролью.
        // Har bir ro'yxatdan o'tgan foydalanuvchi kamida ROLE_USER (o'quvchi)
        // huquqiga ega bo'ladi. Keyinchalik OWNER uni ROLE_ADMIN (o'qituvchi)
        // sifatida ham belgilashi mumkin — ROLE_USER olib tashlanmaydi, shunda
        // o'qituvchi ham o'quvchi funksiyalaridan (masalan, boshqa fandan test
        // ishlash) foydalana oladi.
        Set<Role> roles = new HashSet<>();
        roles.add(userRole);

        // Telefon endi MAJBURIY (yuqorida tekshirildi) — tekshirib E.164'ga
        // o'giramiz, noto'g'ri bo'lsa ro'yxatdan o'tish shu yerda to'xtaydi
        // (aniq xabar bilan).
        String normalizedPhone = phoneNumberService.normalize(dto.phoneCountry(), dto.phoneNumber());

        // Unikallikni tekshirish (foydalanuvchi so'rovi, 2026-09-07:
        // "registratsiyada ... telefon raqamni unikalligini
        // tekshirsin") — aks holda ikkita hisob bir xil raqamga ega
        // bo'lib qolishi mumkin edi (haqiqiy topilgan holat, 2026-09-06:
        // shu sabab Telegram orqali kirishda dublikat-hisob bug'i
        // yuzaga kelgan edi).
        if (userRepository.existsByPhoneNumber(normalizedPhone)) {
            throw new IllegalArgumentException("❌Bu telefon raqam allaqachon ro'yxatdan o'tgan.");
        }

        User user = User.builder()
                .username(dto.username())
                .firstName(dto.firstName().trim())
                .lastName(dto.lastName().trim())
                .workplace(dto.workplace().trim())
                .position(dto.jobTitle().trim())
                // Bo'sh qatorni emas, aniq NULL saqlaymiz — aks holda bir nechta
                // email'siz foydalanuvchida bo'sh qator unique tekshiruviga
                // (existsByEmail) keyinroq to'g'ri kelmasligi mumkin edi.
                .email(hasEmail ? dto.email() : null)
                .phoneNumber(normalizedPhone)
                .password(passwordEncoder.encode(dto.password()))
                .roles(roles)
                // Email bor bo'lsa — tasdiqlash kodi kiritilmaguncha kirish
                // mumkin emas (isEnabled()). Email YO'Q bo'lsa — tasdiqlash
                // kanali yo'q (SMS hali ulanmagan), shuning uchun akkaunt
                // DARHOL faollashtiriladi (vaqtinchalik yechim).
                .emailVerified(!hasEmail)
                .build();

        // 5. Сохраняем
        userRepository.save(user);

        // 6. Email kiritilgan bo'lsagina tasdiqlash kodi yuboriladi — user
        // login qilishdan oldin shu kodni /verify-email sahifasida kiritishi
        // kerak. Email yo'q bo'lsa — akkaunt yuqorida allaqachon faollashtirildi.
        if (hasEmail) {
            emailVerificationService.sendVerificationCode(user);
        }
    }

    @Override
    @Transactional
    public void checkCredentials(LoginDto dto) {

        // 1. Проверка существования пользователя
        UserDetails user = loadUserByUsername(dto.username());

        // 2. Проверка паролей
        if (!passwordEncoder.matches(dto.password(), user.getPassword())) {
            throw new PasswordsDoNotMatchException("Passwords do not match");
        }
    }

    // Foydalanuvchiga qo'shimcha rol beradi (masalan, o'quvchini o'qituvchi
    // ham qiladi). Mavjud rollar OLIB TASHLANMAYDI — shu tufayli bitta odam
    // bir vaqtning o'zida ham o'qituvchi (ROLE_ADMIN), ham o'quvchi
    // (ROLE_USER) bo'la oladi.
    @Transactional
    public ChangeRoleDto addRole(Long targetUserId, String newRole, Authentication auth) {

        User currentUser = (User) auth.getPrincipal();

        if (currentUser.getId().equals(targetUserId)) {
            throw new AccessDeniedException("⛔ Siz o'z rolingizni o'zgartira olmaysiz.");
        }

        User targetUser = userRepository.findById(targetUserId)
                .orElseThrow(() -> new RuntimeException("⛔ Foydalanuvchi topilmadi"));

        Role role = roleRepository.findByRoleName(newRole)
                .orElseThrow(() -> new RuntimeException(newRole + ": ⛔ Bunday rol topilmadi"));

        targetUser.getRoles().add(role);
        userRepository.save(targetUser);

        roleAuditService.record(targetUser, currentUser, newRole, RoleAuditAction.GRANTED, RoleAuditSource.MANUAL);

        return ChangeRoleDto.builder()
                .userId(targetUser.getId())
                .roles(targetUser.getRoles().stream().map(Role::getRoleName).sorted().toList())
                .build();
    }

    // Foydalanuvchidan bitta rolni olib tashlaydi. Kamida bitta rol doim
    // qolishi shart — aks holda foydalanuvchi hech qanday huquqsiz qolib,
    // tizimga kira olmay qoladi.
    @Transactional
    public ChangeRoleDto removeRole(Long targetUserId, String roleName, Authentication auth) {

        User currentUser = (User) auth.getPrincipal();

        if (currentUser.getId().equals(targetUserId)) {
            throw new AccessDeniedException("⛔ Siz o'z rolingizni o'zgartira olmaysiz.");
        }

        User targetUser = userRepository.findById(targetUserId)
                .orElseThrow(() -> new RuntimeException("⛔ Foydalanuvchi topilmadi"));

        if (targetUser.getRoles().size() <= 1) {
            throw new IllegalArgumentException(
                    "⛔ Foydalanuvchining kamida bitta roli bo'lishi kerak.");
        }

        Role role = roleRepository.findByRoleName(roleName)
                .orElseThrow(() -> new RuntimeException(roleName + ": ⛔ Bunday rol topilmadi"));

        targetUser.getRoles().remove(role);
        userRepository.save(targetUser);

        roleAuditService.record(targetUser, currentUser, roleName, RoleAuditAction.REVOKED, RoleAuditSource.MANUAL);

        return ChangeRoleDto.builder()
                .userId(targetUser.getId())
                .roles(targetUser.getRoles().stream().map(Role::getRoleName).sorted().toList())
                .build();
    }

    // OWNER "Foydalanuvchilar" sahifasidan ma'lumotlarni qo'lda tahrirlashi
    // uchun (foydalanuvchi so'rovi, 2026-09-07: "sahifasiga edit ni
    // qo'shish kerak", keyin "qolgan polyalarni ham qo'shish kerak").
    // Username/email/telefon/Telegram ID/Google ID — har biri boshqa
    // hisoblarda band emasligi (o'zining hozirgi qiymati bundan mustasno)
    // tekshiriladi, xuddi ProfileService'dagi kabi.
    @Transactional
    public User adminUpdateUser(Long targetUserId, UpdateUserDto dto) {
        User user = userRepository.findById(targetUserId)
                .orElseThrow(() -> new RuntimeException("⛔ Foydalanuvchi topilmadi"));

        String newUsername = dto.username() == null ? null : dto.username().trim();
        if (isBlank(newUsername)) {
            throw new IllegalArgumentException("❌Username bo'sh bo'lishi mumkin emas.");
        }
        if (!newUsername.equals(user.getUsername()) && userRepository.existsByUsername(newUsername)) {
            throw new IllegalArgumentException("❌Bu username allaqachon band.");
        }

        String newEmail = isBlank(dto.email()) ? null : dto.email().trim();
        boolean emailChanged = newEmail != null && !newEmail.equalsIgnoreCase(user.getEmail());
        if (emailChanged && userRepository.existsByEmail(newEmail)) {
            throw new IllegalArgumentException("❌Bu email allaqachon band.");
        }

        String newPhone = null;
        if (!isBlank(dto.phoneNumber())) {
            newPhone = phoneNumberService.normalize(null, dto.phoneNumber());
            if (userRepository.existsByPhoneNumberAndIdNot(newPhone, targetUserId)) {
                throw new IllegalArgumentException("❌Bu telefon raqam allaqachon band.");
            }
        }

        // Telegram ID/Google ID — "users" jadvalida UNIQUE cheklovga ega
        // (foydalanuvchi so'rovi, 2026-09-07: "qolgan polyalarni ham
        // qo'shish kerak"). Bo'sh qoldirilsa — bog'lanish butunlay olib
        // tashlanadi (masalan dublikat-hisob muammosini qo'lda tuzatish
        // uchun foydali).
        Long newTelegramId = null;
        if (!isBlank(dto.telegramId())) {
            try {
                newTelegramId = Long.parseLong(dto.telegramId().trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("❌Telegram ID faqat raqamlardan iborat bo'lishi kerak.");
            }
            if (userRepository.existsByTelegramIdAndIdNot(newTelegramId, targetUserId)) {
                throw new IllegalArgumentException("❌Bu Telegram ID allaqachon boshqa hisobga bog'langan.");
            }
        }

        String newGoogleId = isBlank(dto.googleId()) ? null : dto.googleId().trim();
        if (newGoogleId != null && userRepository.existsByGoogleIdAndIdNot(newGoogleId, targetUserId)) {
            throw new IllegalArgumentException("❌Bu Google ID allaqachon boshqa hisobga bog'langan.");
        }

        String newFacebookId = isBlank(dto.facebookId()) ? null : dto.facebookId().trim();
        if (newFacebookId != null && userRepository.existsByFacebookIdAndIdNot(newFacebookId, targetUserId)) {
            throw new IllegalArgumentException("❌Bu Facebook ID allaqachon boshqa hisobga bog'langan.");
        }

        user.setUsername(newUsername);
        user.setFirstName(isBlank(dto.firstName()) ? null : dto.firstName().trim());
        user.setLastName(isBlank(dto.lastName()) ? null : dto.lastName().trim());
        user.setEmail(newEmail);
        user.setPhoneNumber(newPhone);
        user.setWorkplace(isBlank(dto.workplace()) ? null : dto.workplace().trim());
        user.setPosition(isBlank(dto.jobTitle()) ? null : dto.jobTitle().trim());
        user.setTelegramId(newTelegramId);
        user.setTelegramUsername(isBlank(dto.telegramUsername()) ? null : dto.telegramUsername().trim());
        user.setGoogleId(newGoogleId);
        user.setFacebookId(newFacebookId);

        return userRepository.save(user);
    }

    @Transactional
    public UserDto deleteUser(Long targetUserId, Authentication auth) {
        User currentUser = (User) auth.getPrincipal();

        if (currentUser.getId().equals(targetUserId)) {
            throw new AccessDeniedException("⛔ Siz o'zingizni o'chira olmaysiz");
        }

        User targetUser = userRepository.findById(targetUserId)
                .orElseThrow(() -> new RuntimeException("⛔ Foydalanuvchi topilmadi"));

        List<String> roles = targetUser.getRoles().stream().map(Role::getRoleName).sorted().toList();

        // O'chirilayotgan foydalanuvchi yaratgan kurslar bo'lsa, muallifligi
        // shu amalni bajarayotgan OWNER'ga o'tkaziladi (currentUser.getId()) —
        // pastdagi izohga qarang.
        deleteFkRestrictedRowsBeforeUserDelete(targetUserId, currentUser.getId());

        userRepository.delete(targetUser);

        return UserDto.builder()
                .id(targetUserId)
                .username(targetUser.getUsername())
                .roles(roles)
                .build();
    }

    // Bir nechta jadval "user_id"/"target_user_id" FK RESTRICT bilan
    // bog'langan (haqiqiy topilgan bug, 2026-09-06 — avval faqat
    // "notifications" tuzatilgan edi, keyin xuddi shu muammo
    // "role_audit_logs"da ham topildi) — foydalanuvchini o'chirishdan
    // OLDIN barchasi tozalanishi shart, aks holda 409 bilan tugaydi.
    // Ajratilgan public metod — TelegramPhoneConfirmController ham
    // xuddi shu tozalashga muhtoj (dublikat "tg_..." hisoblarni telefon
    // raqami bo'yicha birlashtirib o'chirishda, haqiqiy topilgan bug,
    // 2026-09-06: "Bu amalni bajarib bo'lmadi — bog'liq ma'lumotlar
    // mavjud" — hisob "yangi" ko'ringan bo'lsa ham, avvalgi
    // login/telefon-tasdiqlash urinishlaridan bildirishnoma va h.k.
    // qoldiqlar yig'ilib qolishi mumkin edi).
    //
    // "reassignToUserId" — o'chirilayotgan foydalanuvchi NOT NULL FK bilan
    // "muallif" sifatida bog'langan qatorlar (masalan courses.created_by)
    // borligi uchun kerak (haqiqiy topilgan bug, 2026-09-07: "BehzodTest"ni
    // o'chirib bo'lmadi — u ilgari sinov uchun kurs yaratgan, hatto
    // "savat"ga tashlangan bo'lsa ham qator bazada qolgan edi). Bunday
    // qatorlar o'chirilmaydi, shu ID'ga o'tkaziladi — deleteUser() chaqirsa
    // amalni bajarayotgan OWNER, TelegramPhoneConfirmController chaqirsa
    // birlashtirilayotgan asosiy hisob ("target").
    @Transactional
    public void deleteFkRestrictedRowsBeforeUserDelete(Long targetUserId, Long reassignToUserId) {
        notificationService.deleteAllForUser(targetUserId);
        roleAuditLogRepository.deleteByTargetUser_Id(targetUserId);
        roleAuditLogRepository.clearChangedBy(targetUserId);
        emailVerificationCodeRepository.deleteByUser_Id(targetUserId);
        passwordResetCodeRepository.deleteByUser_Id(targetUserId);
        telegramAutoLoginTokenRepository.deleteByUser_Id(targetUserId);
        telegramLinkCodeRepository.deleteByUser_Id(targetUserId);
        courseSectionProgressRepository.deleteByUser_Id(targetUserId);
        subscriptionRepository.deleteByUser_Id(targetUserId);
        subscriptionRepository.clearConfirmedBy(targetUserId);
        courseSubscriptionRepository.deleteByUser_Id(targetUserId);
        courseSubscriptionRepository.clearConfirmedBy(targetUserId);
        paymentOrderRepository.deleteByUser_Id(targetUserId);
        testSessionRepository.deleteByUserId(targetUserId);

        List<Course> authoredCourses = courseRepository.findByCreatedBy_Id(targetUserId);
        List<CourseField> authoredFields = courseFieldRepository.findByCreatedBy_Id(targetUserId);
        if (!authoredCourses.isEmpty() || !authoredFields.isEmpty()) {
            User newOwner = userRepository.findById(reassignToUserId)
                    .orElseThrow(() -> new RuntimeException("⛔ Muallifligi o'tkaziladigan foydalanuvchi topilmadi"));
            authoredCourses.forEach(c -> c.setCreatedBy(newOwner));
            courseRepository.saveAll(authoredCourses);
            authoredFields.forEach(f -> f.setCreatedBy(newOwner));
            courseFieldRepository.saveAll(authoredFields);
        }

        // "archived_by_admin_id" — DB'da ON DELETE SET NULL bo'lsa-da,
        // Hibernate'ga oldindan aytib qo'yish kerak (yuqoridagi izohga
        // qarang), aks holda flush vaqtida xato beradi.
        List<Course> archivedCourses = courseRepository.findByArchivedByAdmin_Id(targetUserId);
        if (!archivedCourses.isEmpty()) {
            archivedCourses.forEach(c -> c.setArchivedByAdmin(null));
            courseRepository.saveAll(archivedCourses);
        }
    }

    // Brute-force himoyasi orqali bloklangan hisobni OWNER qo'lda ochadi.
    @Transactional
    public void unlockUser(Long targetUserId) {
        User targetUser = userRepository.findById(targetUserId)
                .orElseThrow(() -> new RuntimeException("⛔ Foydalanuvchi topilmadi"));

        targetUser.setFailedAttempts(0);
        targetUser.setLockedUntil(null);
        userRepository.save(targetUser);

        notificationService.create(targetUser,
                "🔓 Hisobingiz administrator tomonidan blokdan chiqarildi. Endi tizimga kirishingiz mumkin.",
                null);
    }

}
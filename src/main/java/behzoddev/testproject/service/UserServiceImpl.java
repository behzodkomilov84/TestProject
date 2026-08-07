package behzoddev.testproject.service;

import behzoddev.testproject.dao.RoleRepository;
import behzoddev.testproject.dao.UserRepository;
import behzoddev.testproject.dto.user.ChangeRoleDto;
import behzoddev.testproject.dto.user.LoginDto;
import behzoddev.testproject.dto.user.RegisterDto;
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

        // 1.1 Email — parolni tiklash uchun kerak, shuning uchun majburiy va unikal.
        if (dto.email() == null || dto.email().isBlank()) {
            throw new IllegalArgumentException("❌Email bo'sh bo'lishi mumkin emas.");
        }

        if (userRepository.existsByEmail(dto.email())) {
            throw new IllegalArgumentException("❌Bu email allaqachon ro'yxatdan o'tgan.");
        }

        // 2. Проверка паролей
        if (!dto.password().equals(dto.confirmPassword())) {
            throw new PasswordsDoNotMatchException("Passwords do not match");
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

        // Telefon ixtiyoriy — kiritilgan bo'lsa tekshirib E.164'ga o'giramiz,
        // noto'g'ri bo'lsa ro'yxatdan o'tish shu yerda to'xtaydi (aniq xabar bilan).
        String normalizedPhone = null;
        if (dto.phoneNumber() != null && !dto.phoneNumber().isBlank()) {
            normalizedPhone = phoneNumberService.normalize(dto.phoneCountry(), dto.phoneNumber());
        }

        User user = User.builder()
                .username(dto.username())
                .email(dto.email())
                .phoneNumber(normalizedPhone)
                .password(passwordEncoder.encode(dto.password()))
                .roles(roles)
                .emailVerified(false) // Tasdiqlash kodi kiritilmaguncha kirish mumkin emas (isEnabled()).
                .build();

        // 5. Сохраняем
        userRepository.save(user);

        // 6. Emailni tasdiqlash kodini yuboramiz — user login qilishdan oldin
        // shu kodni /verify-email sahifasida kiritishi kerak.
        emailVerificationService.sendVerificationCode(user);
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

    @Transactional
    public UserDto deleteUser(Long targetUserId, Authentication auth) {
        User currentUser = (User) auth.getPrincipal();

        if (currentUser.getId().equals(targetUserId)) {
            throw new AccessDeniedException("⛔ Siz o'zingizni o'chira olmaysiz");
        }

        User targetUser = userRepository.findById(targetUserId)
                .orElseThrow(() -> new RuntimeException("⛔ Foydalanuvchi topilmadi"));

        List<String> roles = targetUser.getRoles().stream().map(Role::getRoleName).sorted().toList();

        userRepository.delete(targetUser);

        return UserDto.builder()
                .id(targetUserId)
                .username(targetUser.getUsername())
                .roles(roles)
                .build();
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
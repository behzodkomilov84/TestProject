package behzoddev.testproject.service;

import behzoddev.testproject.dao.TestSessionRepository;
import behzoddev.testproject.dto.*;
import behzoddev.testproject.dto.profile.ChangeEmailDto;
import behzoddev.testproject.dto.profile.ChangeFullNameDto;
import behzoddev.testproject.dto.profile.ChangeJobTitleDto;
import behzoddev.testproject.dto.profile.ChangePasswordDto;
import behzoddev.testproject.dto.profile.ChangePhoneDto;
import behzoddev.testproject.dto.profile.ChangeUsernameDto;
import behzoddev.testproject.dto.profile.ChangeWorkplaceDto;
import behzoddev.testproject.dto.profile.TestHistoryDto;
import behzoddev.testproject.entity.User;
import behzoddev.testproject.dao.UserRepository;
import behzoddev.testproject.mapper.TestSessionMapper;
import behzoddev.testproject.telegram.service.TelegramAvatarService;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.springframework.http.HttpStatus.*;

@Service
public class ProfileService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TestSessionRepository testSessionRepository;
    private final TestSessionMapper testSessionMapper;
    private final PhoneNumberService phoneNumberService;
    private final FileStorageService fileStorageService;
    // @Lazy — TelegramAvatarService -> TelegramBot -> TelegramProfileService
    // -> ProfileService (o'zimiz) aylanma bog'liqlik hosil qilardi
    // (NotificationService'dagi TelegramBot bilan bir xil muammo/yechim).
    private final TelegramAvatarService telegramAvatarService;

    public ProfileService(UserRepository userRepository,
                           PasswordEncoder passwordEncoder,
                           TestSessionRepository testSessionRepository,
                           TestSessionMapper testSessionMapper,
                           PhoneNumberService phoneNumberService,
                           FileStorageService fileStorageService,
                           @Lazy TelegramAvatarService telegramAvatarService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.testSessionRepository = testSessionRepository;
        this.testSessionMapper = testSessionMapper;
        this.phoneNumberService = phoneNumberService;
        this.fileStorageService = fileStorageService;
        this.telegramAvatarService = telegramAvatarService;
    }

    // 🔹 смена имени
    @Transactional
    public void changeUsername(User user, ChangeUsernameDto changeUsernameDto) {

        if (userRepository.existsByUsername(changeUsernameDto.newUsername())) {
            throw new ResponseStatusException(
                    CONFLICT, "Имя пользователя уже занято"
            );
        }

        user.setUsername(changeUsernameDto.newUsername());
        userRepository.save(user);
    }

    // 🔹 email qo'shish/o'zgartirish (parolni tiklashda zaxira kanal sifatida ishlatiladi)
    @Transactional
    public void changeEmail(User user, ChangeEmailDto dto) {

        String newEmail = dto.newEmail().trim();

        if (newEmail.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "Email bo'sh bo'lishi mumkin emas");
        }

        boolean sameAsBefore = newEmail.equalsIgnoreCase(user.getEmail());

        if (!sameAsBefore && userRepository.existsByEmail(newEmail)) {
            throw new ResponseStatusException(CONFLICT, "Bu email allaqachon band");
        }

        user.setEmail(newEmail);
        userRepository.save(user);
    }

    // 🔹 telefon raqam qo'shish/o'zgartirish — PhoneNumberService orqali
    // tekshiriladi va E.164 formatga o'giriladi (masalan "+998901234567").
    @Transactional
    public void changePhone(User user, ChangePhoneDto dto) {
        String normalized = phoneNumberService.normalize(dto.isoCode(), dto.rawNumber());
        user.setPhoneNumber(normalized);
        userRepository.save(user);
    }

    // 🔹 Ism/Familiya (foydalanuvchi so'rovi, 2026-09-06).
    @Transactional
    public void changeFullName(User user, ChangeFullNameDto dto) {
        user.setFirstName(dto.firstName().trim());
        user.setLastName(dto.lastName().trim());
        userRepository.save(user);
    }

    // 🔹 Ish yoki o'qish joyi.
    @Transactional
    public void changeWorkplace(User user, ChangeWorkplaceDto dto) {
        user.setWorkplace(dto.workplace().trim());
        userRepository.save(user);
    }

    // 🔹 Lavozimi.
    @Transactional
    public void changeJobTitle(User user, ChangeJobTitleDto dto) {
        user.setPosition(dto.jobTitle().trim());
        userRepository.save(user);
    }

    // 🔹 Profil rasmi — qo'lda yuklash (drag&drop yoki fayl tanlash).
    @Transactional
    public String uploadAvatar(User user, MultipartFile file) {
        String url = fileStorageService.storeAvatarImage(file);
        user.setAvatarUrl(url);
        userRepository.save(user);
        return url;
    }

    // 🔹 Profil rasmini Telegram'dan qayta yuklab olish ("🔄 Telegramdan
    // yangilash" tugmasi) — faqat Telegram ulangan hisoblarga ochiq.
    @Transactional
    public String syncAvatarFromTelegram(User user) {
        if (user.getTelegramId() == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Telegram ulanmagan");
        }

        String url = telegramAvatarService.fetchAvatarUrl(user.getTelegramId());
        if (url == null) {
            throw new ResponseStatusException(NOT_FOUND, "Telegram profilida rasm topilmadi");
        }

        user.setAvatarUrl(url);
        userRepository.save(user);
        return url;
    }

    // 🔹 смена пароля
    @Transactional
    public void changePassword(User user, ChangePasswordDto dto) {

        if (!passwordEncoder.matches(dto.currentPassword(), user.getPassword())) {
            throw new ResponseStatusException(
                    BAD_REQUEST, "Текущий пароль неверный"
            );
        }

        user.setPassword(passwordEncoder.encode(dto.newPassword()));
        userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public PageResponseDto<TestHistoryDto> getHistory(User user, Pageable pageable) {

        Page<TestHistoryDto> pageData = testSessionRepository.getPageableTestHistoryDtoByUser(user, pageable);

        List<TestHistoryDto> dtos = pageData.getContent();

        return new PageResponseDto<>(
                dtos,
                pageData.getTotalPages(),
                pageData.getNumber(),
                pageData.isFirst(),
                pageData.isLast()
        );

    }
}

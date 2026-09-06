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

    // MUHIM, haqiqiy topilgan bug (2026-09-07): "user" (@AuthenticationPrincipal
    // orqali kelgan) — HTTP sessiyaga LOGIN vaqtida saqlab qo'yilgan ESKI
    // (stale) nusxa, har bir so'rovda bazadan qayta o'qilmaydi. Shu
    // obyektni to'g'ridan-to'g'ri o'zgartirib saqlasak, sessiya
    // boshlangandan keyin (masalan OWNER admin panelidan, yoki boshqa
    // qurilma/tabdan) shu foydalanuvchiga kiritilgan BARCHA BOSHQA
    // o'zgarishlar sessiyadagi eski qiymatlar bilan qayta yozilib,
    // YO'QOLIB ketardi (masalan: admin panelidan telefon tuzatilgan
    // bo'lsa-yu, foydalanuvchi shu eski sessiya bilan ismini o'zgartirsa,
    // telefon yana eski — noto'g'ri — qiymatga qaytib qolardi). Shuning
    // uchun HAR BIR quyidagi metod DOIM bazadan yangi (fresh) nusxa
    // olib, faqat O'SHA nusxaga o'zgartirish kiritadi.
    private User fresh(User user) {
        return userRepository.findById(user.getId())
                .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Foydalanuvchi topilmadi"));
    }

    // 🔹 смена имени
    @Transactional
    public void changeUsername(User user, ChangeUsernameDto changeUsernameDto) {

        if (userRepository.existsByUsername(changeUsernameDto.newUsername())) {
            throw new ResponseStatusException(
                    CONFLICT, "Имя пользователя уже занято"
            );
        }

        User target = fresh(user);
        target.setUsername(changeUsernameDto.newUsername());
        userRepository.save(target);
    }

    // 🔹 email qo'shish/o'zgartirish (parolni tiklashda zaxira kanal sifatida ishlatiladi)
    @Transactional
    public void changeEmail(User user, ChangeEmailDto dto) {

        String newEmail = dto.newEmail().trim();

        if (newEmail.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "Email bo'sh bo'lishi mumkin emas");
        }

        User target = fresh(user);
        boolean sameAsBefore = newEmail.equalsIgnoreCase(target.getEmail());

        if (!sameAsBefore && userRepository.existsByEmail(newEmail)) {
            throw new ResponseStatusException(CONFLICT, "Bu email allaqachon band");
        }

        target.setEmail(newEmail);
        userRepository.save(target);
    }

    // 🔹 telefon raqam qo'shish/o'zgartirish — PhoneNumberService orqali
    // tekshiriladi va E.164 formatga o'giriladi (masalan "+998901234567").
    @Transactional
    public void changePhone(User user, ChangePhoneDto dto) {
        String normalized = phoneNumberService.normalize(dto.isoCode(), dto.rawNumber());

        // Unikallikni tekshirish (foydalanuvchi so'rovi, 2026-09-07:
        // "profilni tahrirlashda ... telefon raqamni unikalligini
        // tekshirsin") — o'zining hozirgi raqamini qayta saqlasa xato
        // bermasligi uchun o'z ID'si chetlab o'tiladi.
        if (userRepository.existsByPhoneNumberAndIdNot(normalized, user.getId())) {
            throw new ResponseStatusException(CONFLICT, "Bu telefon raqam allaqachon band");
        }

        User target = fresh(user);
        target.setPhoneNumber(normalized);
        userRepository.save(target);
    }

    // 🔹 Telegramni uzish — boshqa Telegram hisobini bog'lash uchun
    // (foydalanuvchi so'rovi, 2026-09-06: "Telegram orqali kirishda, boshqa
    // account orqali kirish ham mumkin bo'lsin" — Telegram'ning o'zi bitta
    // brauzer sessiyasida faqat bitta hisobni "eslab qoladi", widget buni
    // o'zgartira olmaydi, shuning uchun sayt tomonidan mumkin bo'lgan
    // yagona yechim — joriy ulanishni uzib, keyin YANGI Telegram hisobi
    // bilan qayta bog'lash/kirish). Email talab qilinmaydi (foydalanuvchi
    // so'rovi, 2026-09-06: "Shuni so'ramasin" — ilgari email yo'q bo'lsa
    // bloklangandi, chunki tasodifiy parolni bilmasa hisobiga qayta kira
    // olmay qolishi mumkin edi; endi bu tekshiruv olib tashlandi).
    @Transactional
    public void disconnectTelegram(User user) {
        if (user.getTelegramId() == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Telegram ulanmagan");
        }

        User target = fresh(user);
        target.setTelegramId(null);
        userRepository.save(target);
    }

    // 🔹 Ism/Familiya (foydalanuvchi so'rovi, 2026-09-06).
    @Transactional
    public void changeFullName(User user, ChangeFullNameDto dto) {
        User target = fresh(user);
        target.setFirstName(dto.firstName().trim());
        target.setLastName(dto.lastName().trim());
        userRepository.save(target);
    }

    // 🔹 Ish yoki o'qish joyi.
    @Transactional
    public void changeWorkplace(User user, ChangeWorkplaceDto dto) {
        User target = fresh(user);
        target.setWorkplace(dto.workplace().trim());
        userRepository.save(target);
    }

    // 🔹 Lavozimi.
    @Transactional
    public void changeJobTitle(User user, ChangeJobTitleDto dto) {
        User target = fresh(user);
        target.setPosition(dto.jobTitle().trim());
        userRepository.save(target);
    }

    // 🔹 Profil rasmi — qo'lda yuklash (drag&drop yoki fayl tanlash).
    @Transactional
    public String uploadAvatar(User user, MultipartFile file) {
        String url = fileStorageService.storeAvatarImage(file);
        User target = fresh(user);
        target.setAvatarUrl(url);
        userRepository.save(target);
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

        User target = fresh(user);
        target.setAvatarUrl(url);
        userRepository.save(target);
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

        User target = fresh(user);
        target.setPassword(passwordEncoder.encode(dto.newPassword()));
        userRepository.save(target);
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

package behzoddev.testproject.dao;

import behzoddev.testproject.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    List<User> findByRoles_RoleName(String roleName);

    List<User> findAllByGroupId(Long groupId);

    Optional<User> findByTelegramId(Long telegramId);

    boolean existsByTelegramId(Long telegramId);

    Optional<User> findByGoogleId(String googleId);

    boolean existsByEmail(String email);

    Optional<User> findByEmail(String email);

    // Optional emas — phone_number ustunida UNIQUE cheklov yo'q (eski
    // ma'lumotlarda bir nechta hisob bir xil raqamga ega bo'lishi mumkin,
    // haqiqiy topilgan holat: 2026-09-06), shuning uchun "Optional"
    // qaytarilsa Spring Data bitta natijadan ortig'ida xato tashlaydi.
    List<User> findAllByPhoneNumber(String phoneNumber);

    // Ro'yxatdan o'tishda unikallikni tekshirish uchun (foydalanuvchi
    // so'rovi, 2026-09-07: "registratsiyada ... telefon raqamni
    // unikalligini tekshirsin"). "findAllByPhoneNumber" yuqorida BOR
    // ma'lumotlarda takrorlanishga toqat qilish uchun ATAYLAB List
    // qaytaradi — bu metod esa YANGI kiritilgan raqam allaqachon band-
    // emasligini tekshiradi (Optional/List emas, oddiy boolean yetarli).
    boolean existsByPhoneNumber(String phoneNumber);

    // Profilni tahrirlashda unikallikni tekshirish uchun — o'zining
    // (hozirgi) raqamini o'zgartirmasdan saqlab qo'ysa, "band" deb
    // xato bermasligi kerak, shuning uchun o'z ID'si chetlab o'tiladi.
    boolean existsByPhoneNumberAndIdNot(String phoneNumber, Long id);

}

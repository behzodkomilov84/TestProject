package behzoddev.testproject.dao;

import behzoddev.testproject.dto.testsession.UserTestSessionStatsDto;
import behzoddev.testproject.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
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

    Optional<User> findByFacebookId(String facebookId);

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

    // "Foydalanuvchilar" sahifasida qo'lda tahrirlashda (foydalanuvchi
    // so'rovi, 2026-09-07: "qolgan polyalarni ham qo'shish kerak" —
    // Telegram ID/@username/Google ID ham) unikallikni tekshirish uchun.
    // "telegram_id"/"google_id" ustunlarida DB darajasida UNIQUE cheklov
    // bor — qo'lda noto'g'ri (masalan boshqa hisobda allaqachon band)
    // qiymat kiritilsa, aniq xato bilan oldindan to'xtatiladi.
    boolean existsByTelegramIdAndIdNot(Long telegramId, Long id);

    boolean existsByGoogleIdAndIdNot(String googleId, Long id);

    boolean existsByFacebookIdAndIdNot(String facebookId, Long id);

    // "Oxirgi tashrif vaqti" ustuni (foydalanuvchi so'rovi, 2026-09-09) —
    // OnlineUserTracker orqali (throttled) yangilanadi. Bulk UPDATE
    // ataylab ishlatilgan — butun User entity'ni yuklab, boshqa
    // ustunlarni ham qayta yozib (masalan @DynamicUpdate hisobga olsa
    // ham, ortiqcha SELECT+UPDATE) yubormaslik uchun.
    @Modifying
    @Query("UPDATE User u SET u.lastSeenAt = :time WHERE u.id = :id")
    void updateLastSeenAt(@Param("id") Long id, @Param("time") LocalDateTime time);

    // "📊 Statistika" -> "👤 Foydalanuvchilar kesimida test statistikasi"
    // (foydalanuvchi so'rovi, 2026-09-13) — HAR BIR foydalanuvchi uchun
    // BITTA qator, hali birorta ham test yechmagan foydalanuvchilar HAM
    // (0 qiymatlar bilan) ko'rinishi uchun "left join ... on" ATAYLAB
    // ishlatilgan (oddiy "join t.testSessions" kabi mapped bog'lanish
    // o'rniga — User entity'da TestSession'ga to'g'ridan-to'g'ri
    // @OneToMany mavjud emas). Faqat TUGATILGAN (finishedAt != null)
    // sessiyalar hisoblanadi — boshqa test-tarixi so'rovlari bilan bir
    // xil qoida (TestSessionRepository#findByUserId va h.k.).
    @Query("""
            select new behzoddev.testproject.dto.testsession.UserTestSessionStatsDto(
                u.id, u.username, u.firstName, u.lastName, g.name,
                count(t.id),
                sum(t.totalQuestions),
                sum(t.correctAnswers),
                avg(t.percent),
                max(t.percent),
                sum(t.durationSec),
                max(t.finishedAt)
            )
            from User u
            left join u.group g
            left join TestSession t on t.user = u and t.finishedAt is not null
            group by u.id, u.username, u.firstName, u.lastName, g.name
            """)
    List<UserTestSessionStatsDto> findAllUserTestSessionStats();

}

package behzoddev.testproject.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.DynamicUpdate;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

// @DynamicUpdate — HAQIQIY topilgan bug (2026-09-08): ProfileService'ning
// har bir change* metodi bazadan "fresh" nusxa o'qib, faqat BITTA maydonni
// o'zgartirib saqlaydi (ProfileService.fresh() izohiga qarang), lekin
// Hibernate DEFAULT holda save() paytida entity'dagi BARCHA ustunlarni
// qayta yozadi — shu jumladan o'sha "fresh" o'qishda olingan, hali eski
// (boshqa concurrent so'rov hali commit qilmagan) qiymatlarni ham. Profil
// to'ldirish modali (profile-gate.js) ism/familiya + ish joyi + lavozim +
// telefonni Promise.all bilan BIR VAQTDA (4 ta alohida PATCH) yuboradi —
// ikkitasi bir-biridan oldin "fresh" o'qisa, keyin ikkalasi ham commit
// qilganda, KEYIN commit bo'lgani boshqasining o'zgartirgan ustunini
// o'zining eski (null) qiymati bilan qayta ustidan yozib, YO'QOTIB
// yuborardi (masalan: ish joyi saqlanadi, lekin lavozim keyin kelib
// uni nolga qaytarib qo'yadi). @DynamicUpdate Hibernate'ga FAQAT
// haqiqatan o'zgargan ustunlarni UPDATE qilishni buyuradi — shu bilan
// concurrent bitta-maydonli saqlashlar bir-birini endi bosib ketmaydi.
@DynamicUpdate
@Entity
@Table(name = "users")
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
@ToString(exclude = {"password", "roles"})
public class User implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(nullable = false)
    private String password;

    // ❗ Bitta akkaunt bir nechta rolga ega bo'lishi mumkin (masalan, ham
    // o'qituvchi — ROLE_ADMIN, ham o'quvchi — ROLE_USER). EAGER majburiy —
    // rollar avtorizatsiya vaqtida darhol kerak bo'ladi.
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    @Builder.Default
    private Set<Role> roles = new HashSet<>();

    @ManyToOne
    @JoinColumn(name = "group_id")
    private TeacherGroup group;

    @Column(name = "telegram_id", unique = true)
    private Long telegramId;

    // Telegram'ning o'zi ko'rsatadigan @username (raqamli telegramId
    // emas) — profilda "qaysi Telegram hisobiga bog'langan" degan
    // savolga inson TANIY oladigan javob berish uchun (foydalanuvchi
    // so'rovi, 2026-09-06). Har bir Telegram foydalanuvchisida username
    // bo'lmasligi ham mumkin (Telegram'da ixtiyoriy) — shu holda NULL.
    @Column(name = "telegram_username", length = 64)
    private String telegramUsername;

    // Google orqali kirish — Google'ning barqaror "sub" (subject)
    // identifikatori (foydalanuvchi so'rovi, 2026-09-06). telegramId bilan
    // bir xil g'oya, faqat String (Google "sub"i String, garchi ko'rinishda
    // raqamga o'xshasa ham — rasmiy spetsifikatsiyaga ko'ra Long'ga
    // ishonib bo'lmaydi).
    @Column(name = "google_id", unique = true, length = 64)
    private String googleId;

    // Facebook orqali kirish — Facebook'ning barqaror foydalanuvchi ID'si
    // (foydalanuvchi so'rovi, 2026-09-07). googleId bilan bir xil g'oya.
    @Column(name = "facebook_id", unique = true, length = 64)
    private String facebookId;

    // Profilga qo'shimcha ma'lumotlar (foydalanuvchi so'rovi, 2026-09-06) —
    // "qaysi sohadan, qaysi kasbdagilar foydalanayotganini" bilish uchun.
    // Eski foydalanuvchilarda NULL bo'lishi mumkin — "shart"lik faqat YANGI
    // ro'yxatdan o'tishda forma darajasida ta'minlanadi (UserServiceImpl
    // #register), DB darajasida emas (mavjud hisoblarni buzmaslik uchun).
    @Column(name = "first_name", length = 100)
    private String firstName;

    @Column(name = "last_name", length = 100)
    private String lastName;

    // Ish yoki o'qish joyi (masalan "Toshkent tibbiyot akademiyasi").
    @Column(name = "workplace")
    private String workplace;

    // Lavozimi (masalan "shifokor", "talaba", "o'qituvchi") — SQL'da
    // "POSITION" band so'z bo'lgani uchun ustun nomi "job_title".
    @Column(name = "job_title")
    private String position;

    // Profil rasmi — "/uploads/avatars/..." (FileStorageService), qo'lda
    // yuklangan YOKI Telegram orqali kirish/ulanishda avtomatik olingan
    // (TelegramAvatarService). NULL bo'lsa — frontend standart placeholder
    // ko'rsatadi.
    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    // Parolni tiklash uchun (email kanali). Eski userlarda bo'sh bo'lishi
    // mumkin — faqat yangi ro'yxatdan o'tishda majburiy qilingan.
    @Column(unique = true)
    private String email;

    // Xalqaro E.164 formatda saqlanadi (masalan "+998901234567") —
    // PhoneNumberService orqali tekshirilib shu ko'rinishga keltiriladi.
    // Ixtiyoriy (barcha eski userlarda bo'sh).
    @Column(name = "phone_number", length = 20)
    private String phoneNumber;

    // Ro'yxatdan o'tishda email tasdiqlash. DB darajasida DEFAULT TRUE
    // (mavjud userlar login qilishda davom etishi uchun) — faqat yangi
    // ro'yxatdan o'tishda (UserServiceImpl.register) explicit "false"
    // qo'yiladi, tasdiqlash kodi bilan tekshirilgach true'ga o'tadi.
    @Column(name = "email_verified", nullable = false)
    @Builder.Default
    private boolean emailVerified = true;

    // Brute-force himoyasi: ketma-ket noto'g'ri parol urinishlari soni.
    // Muvaffaqiyatli login'da 0'ga tushiriladi.
    @Column(name = "failed_attempts", nullable = false)
    @Builder.Default
    private int failedAttempts = 0;

    // Shu vaqtgacha hisob bloklangan (5-marta noto'g'ri urinishdan keyin).
    // null bo'lsa — bloklanmagan.
    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;

    /**
     * Foydalanuvchida berilgan nomdagi rol bor-yo'qligini tekshiradi.
     * Masalan: user.hasRole("ROLE_ADMIN")
     */
    public boolean hasRole(String roleName) {
        return roles != null && roles.stream()
                .anyMatch(r -> roleName.equals(r.getRoleName()));
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return roles == null
                ? Set.of()
                : roles.stream()
                        .map(r -> new SimpleGrantedAuthority(r.getRoleName()))
                        .collect(Collectors.toSet());
    }

    @Override
    public @Nullable String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return lockedUntil == null || lockedUntil.isBefore(LocalDateTime.now());
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return emailVerified;
    }
}

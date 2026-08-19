package behzoddev.testproject.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

// Botdagi havolalar ("saytda ko'ring") bosilganda foydalanuvchini avtomatik
// (parolsiz) login qildirish uchun — bitta martalik, qisqa muddatli token.
// Faqat allaqachon /link orqali ulangan (tasdiqlangan) Telegram akkauntlar
// uchun beriladi — token o'zi tasodifiy, taxmin qilib bo'lmaydigan (256 bit).
@Entity
@Table(name = "telegram_auto_login_tokens")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TelegramAutoLoginToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // DIQQAT: bu yerda TOKENNING O'ZI emas, uning SHA-256 xeshi saqlanadi
    // (TokenHasher). Asl token faqat foydalanuvchiga yuborilgan URL'da
    // bor — baza biror sabab bilan o'qilsa ham (zaxira nusxasi, o'qish
    // huquqi bilan hujum va h.k.), undan asl tokenni tiklab bo'lmaydi.
    @Column(nullable = false, length = 64)
    private String token;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // Login'dan keyin yo'naltiriladigan sahifa (masalan "/courses") —
    // faqat serverda o'zimiz belgilagan qiymat, foydalanuvchidan kelmaydi.
    @Column(name = "redirect_path")
    private String redirectPath;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Builder.Default
    private boolean used = false;
}

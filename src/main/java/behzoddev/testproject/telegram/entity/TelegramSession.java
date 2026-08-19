package behzoddev.testproject.telegram.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

// Botdagi ko'p bosqichli suhbatlar (masalan "profilni tahrirlash: avval
// yangi ismni yozing, keyin tasdiqlang") uchun holat. Bazada saqlanadi
// (xotirada emas) — production tez-tez qayta ishga tushadi (deploy),
// xotiradagi holat har safar yo'qolib, foydalanuvchini suhbat o'rtasida
// "uzib qo'yardi". Bitta chat uchun bitta qator (chatId — PRIMARY KEY).
@Entity
@Table(name = "telegram_sessions")
@Getter
@Setter
public class TelegramSession {

    @Id
    @Column(name = "chat_id")
    private Long chatId;

    // BotState.name() — joriy bosqich (masalan "AWAITING_USERNAME").
    @Column(nullable = false, length = 50)
    private String state;

    // Oqim davomida vaqtincha saqlanadigan ma'lumot (masalan parolni
    // o'zgartirishda 1-bosqichda kiritilgan "hozirgi parol") — JSON
    // (Map<String,String>) ko'rinishida.
    @Lob
    @Column(name = "temp_data")
    private String tempData;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}

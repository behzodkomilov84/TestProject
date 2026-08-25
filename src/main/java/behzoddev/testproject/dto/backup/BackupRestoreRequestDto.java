package behzoddev.testproject.dto.backup;

import java.time.LocalDateTime;
import java.util.List;

// "♻️ Tanlanganlarni tiklash" so'rovi — /preview'da ko'rsatilgan
// vaqt oralig'i AYNAN shu qiymatlar bilan qayta tekshiriladi (frontend
// firibgarlik/eskirgan holat bilan boshqa kursni yubormasin uchun).
public record BackupRestoreRequestDto(LocalDateTime from, LocalDateTime to, List<Long> courseIds) {
}

package behzoddev.testproject.dto.backup;

import java.time.LocalDateTime;

// "📦 Backup orqali tiklash" panelidagi bitta zaxira fayli (BackupRestoreService.listBackups).
public record BackupFileDto(String fileName, LocalDateTime capturedAt, long sizeBytes) {
}

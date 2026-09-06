package behzoddev.testproject.telegram.service;

import behzoddev.testproject.service.FileStorageService;
import behzoddev.testproject.telegram.TelegramBot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.GetUserProfilePhotos;
import org.telegram.telegrambots.meta.api.objects.PhotoSize;
import org.telegram.telegrambots.meta.api.objects.UserProfilePhotos;

import java.io.File;
import java.nio.file.Files;
import java.util.List;

// Telegram orqali kirgan/ulangan foydalanuvchining profil rasmini
// (agar bo'lsa) avtomatik olib, avatar sifatida saqlash (foydalanuvchi
// so'rovi, 2026-09-06: "Agar telegramda rasmi bo'lsa, shu rasmga
// qo'shilsin"). Xatolik (rasm yo'q, tarmoq muammosi va h.k.) HECH QACHON
// yuqoriga otilmaydi — avatar shunchaki bo'sh qoladi, bu login/ro'yxatdan
// o'tish jarayonini to'xtatib qo'ymasligi kerak.
@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramAvatarService {

    private final TelegramBot telegramBot;
    private final FileStorageService fileStorageService;

    public String fetchAvatarUrl(Long telegramId) {
        try {
            UserProfilePhotos photos = telegramBot.execute(
                    GetUserProfilePhotos.builder().userId(telegramId).limit(1).build());

            if (photos == null || photos.getTotalCount() == null || photos.getTotalCount() == 0) {
                return null;
            }

            List<PhotoSize> sizes = photos.getPhotos().get(0);
            // Telegram bir xil rasmni bir nechta o'lchamda qaytaradi, kichigidan
            // kattasiga qarab tartiblangan — oxirgisi eng yuqori sifatli.
            PhotoSize largest = sizes.get(sizes.size() - 1);

            org.telegram.telegrambots.meta.api.objects.File telegramFile =
                    telegramBot.execute(GetFile.builder().fileId(largest.getFileId()).build());

            File tempFile = telegramBot.downloadFile(telegramFile);
            try {
                byte[] bytes = Files.readAllBytes(tempFile.toPath());
                return fileStorageService.storeAvatarFromBytes(bytes);
            } finally {
                //noinspection ResultOfMethodCallIgnored
                tempFile.delete();
            }
        } catch (Exception e) {
            log.warn("Telegram'dan avatar olishda xatolik (e'tiborsiz qoldiriladi, telegramId={}): {}",
                    telegramId, e.getMessage());
            return null;
        }
    }
}

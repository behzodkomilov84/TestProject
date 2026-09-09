package behzoddev.testproject.service;

import behzoddev.testproject.dao.NotificationRepository;
import behzoddev.testproject.dto.notification.NotificationDto;
import behzoddev.testproject.dto.notification.NotificationStatsDto;
import behzoddev.testproject.entity.Notification;
import behzoddev.testproject.entity.User;
import behzoddev.testproject.telegram.TelegramBot;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.List;
import java.util.NoSuchElementException;

// Saytning o'zidagi bildirishnoma markazi — Telegram'dan tashqari, sayt
// ichida ham foydalanuvchiga muhim hodisalar (ADMIN tasdiqlandi, yangi
// topshiriq va h.k.) haqida xabar berish uchun. Foydalanuvchi Telegram'ga
// ulangan bo'lsa (telegramId bor), har bir bildirishnoma avtomatik botga
// ham yuboriladi — "✅ O'qildim" tugmasi bilan, uni bosganda sayt tarafida
// ham "o'qilgan" statusiga o'tadi (TelegramBot.onUpdateReceived, "notif_read_" prefiksi).
@Slf4j
@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    // @Lazy — TelegramBot ham (callback qaytganda) NotificationService'ga
    // muhtoj, shuning uchun to'g'ridan-to'g'ri constructor-injection bilan
    // aylanma bog'liqlik (circular dependency) hosil bo'lardi. @Lazy proksi
        // orqali bu zanjir uziladi.
    private final TelegramBot telegramBot;

    public NotificationService(NotificationRepository notificationRepository,
                                @Lazy TelegramBot telegramBot) {
        this.notificationRepository = notificationRepository;
        this.telegramBot = telegramBot;
    }

    // "/users" sahifasidagi ✉️ ikonkasi — OWNER Telegram bot orqali
    // foydalanuvchiga to'g'ridan-to'g'ri shaxsiy xabar yubora oladi
    // (foydalanuvchi so'rovi, 2026-09-09). Mavjud create()/sendTelegramCopy
    // infratuzilmasi qayta ishlatiladi — shu bilan xabar AVTOMATIK ravishda
    // sayt bildirishnomalar markazida ham saqlanadi (tarix/audit uchun,
    // "✅ O'qildim" tugmasi bilan Telegram'dagi bilan bir xil).
    private static final int OWNER_MESSAGE_MAX_LENGTH = 2000;

    @Transactional
    public void sendOwnerMessage(User targetUser, String text) {
        if (targetUser.getTelegramId() == null) {
            throw new IllegalArgumentException("❌Bu foydalanuvchi Telegram botga ulanmagan — xabar yuborib bo'lmaydi");
        }
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException("❌Xabar matni bo'sh bo'lishi mumkin emas");
        }

        String trimmed = text.trim();
        if (trimmed.length() > OWNER_MESSAGE_MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "❌Xabar juda uzun (ko'pi bilan " + OWNER_MESSAGE_MAX_LENGTH + " ta belgi bo'lishi kerak)");
        }

        create(targetUser, "✉️ Administrator (OWNER) dan shaxsiy xabar:\n\n" + trimmed, null);
        log.info("OWNER foydalanuvchiga shaxsiy xabar yubordi: user={}", targetUser.getUsername());
    }

    @Transactional
    public void create(User user, String message, String link) {
        Notification notification = notificationRepository.save(Notification.builder()
                .user(user)
                .message(message)
                .link(link)
                .build());

        sendTelegramCopy(user, notification);
    }

    private void sendTelegramCopy(User user, Notification notification) {
        Long telegramId = user.getTelegramId();
        if (telegramId == null) return;

        try {
            SendMessage msg = new SendMessage();
            msg.setChatId(telegramId.toString());
            msg.setText("🔔 " + notification.getMessage());

            InlineKeyboardButton readButton = new InlineKeyboardButton();
            readButton.setText("✅ O'qildim");
            readButton.setCallbackData("notif_read_" + notification.getId());

            InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
            markup.setKeyboard(List.of(List.of(readButton)));
            msg.setReplyMarkup(markup);

            telegramBot.execute(msg);
        } catch (Exception e) {
            // Telegram yuborilmasa ham sayt bildirishnomasi saqlangan
            // bo'lishi kerak — shuning uchun xatolikni yutamiz, faqat loglaymiz.
            log.warn("Bildirishnomani Telegram'ga yuborib bo'lmadi: user={}", user.getUsername(), e);
        }
    }

    @Transactional(readOnly = true)
    public List<NotificationDto> list(User user) {
        return notificationRepository.findTop50ByUser_IdOrderByCreatedAtDesc(user.getId())
                .stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public long unreadCount(User user) {
        return notificationRepository.countByUser_IdAndReadFalse(user.getId());
    }

    // /notifications sahifasi uchun — tanlangan tab (Yangi/O'qilgan) bo'yicha
    // to'liq (cheklovsiz) ro'yxat.
    @Transactional(readOnly = true)
    public List<NotificationDto> listByStatus(User user, boolean read) {
        return notificationRepository.findByUser_IdAndReadOrderByCreatedAtDesc(user.getId(), read)
                .stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public NotificationStatsDto stats(User user) {
        long unread = notificationRepository.countByUser_IdAndReadFalse(user.getId());
        long read = notificationRepository.countByUser_IdAndReadTrue(user.getId());

        return NotificationStatsDto.builder()
                .total(unread + read)
                .unread(unread)
                .read(read)
                .build();
    }

    @Transactional
    public void markRead(Long id, User user) {
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Bildirishnoma topilmadi"));

        if (!notification.getUser().getId().equals(user.getId())) {
            throw new AccessDeniedException("Bu bildirishnoma sizga tegishli emas");
        }

        notification.setRead(true);
        notificationRepository.save(notification);
    }

    @Transactional
    public void markAllRead(User user) {
        List<Notification> unread = notificationRepository.findByUser_IdAndReadFalse(user.getId());
        unread.forEach(n -> n.setRead(true));
        notificationRepository.saveAll(unread);
    }

    // Foydalanuvchini BUTUNLAY o'chirishdan OLDIN chaqiriladi
    // (UserServiceImpl#deleteUser) — "notifications.user_id" ustunida FK
    // RESTRICT bo'lgani uchun, aks holda o'chirish 409 (haqiqiy topilgan
    // bug, foydalanuvchi so'rovi, 2026-09-06 — test hisobni o'chirishga
    // urinilganda "Ma'lumotlar bazasi cheklovi buzildi: ... fk_notification_user"
    // xatosi bilan aniqlangan).
    @Transactional
    public void deleteAllForUser(Long userId) {
        notificationRepository.deleteByUser_Id(userId);
    }

    private NotificationDto toDto(Notification n) {
        return NotificationDto.builder()
                .id(n.getId())
                .message(n.getMessage())
                .link(n.getLink())
                .read(n.isRead())
                .createdAt(n.getCreatedAt())
                .build();
    }
}

package behzoddev.testproject.service;

import behzoddev.testproject.dao.NotificationRepository;
import behzoddev.testproject.dto.notification.NotificationDto;
import behzoddev.testproject.entity.Notification;
import behzoddev.testproject.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;

// Saytning o'zidagi bildirishnoma markazi — Telegram'dan tashqari, sayt
// ichida ham foydalanuvchiga muhim hodisalar (ADMIN tasdiqlandi, yangi
// topshiriq va h.k.) haqida xabar berish uchun.
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;

    @Transactional
    public void create(User user, String message, String link) {
        notificationRepository.save(Notification.builder()
                .user(user)
                .message(message)
                .link(link)
                .build());
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

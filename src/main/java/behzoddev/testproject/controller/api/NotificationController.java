package behzoddev.testproject.controller.api;

import behzoddev.testproject.dto.notification.NotificationDto;
import behzoddev.testproject.dto.notification.NotificationStatsDto;
import behzoddev.testproject.entity.User;
import behzoddev.testproject.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public List<NotificationDto> list(@AuthenticationPrincipal User user) {
        return notificationService.list(user);
    }

    // /notifications sahifasi uchun — "Yangi" (read=false) yoki "O'qilgan"
    // (read=true) tab bo'yicha to'liq (top-50 bilan cheklanmagan) ro'yxat.
    @GetMapping("/by-status")
    public List<NotificationDto> listByStatus(@RequestParam boolean read, @AuthenticationPrincipal User user) {
        return notificationService.listByStatus(user, read);
    }

    @GetMapping("/stats")
    public NotificationStatsDto stats(@AuthenticationPrincipal User user) {
        return notificationService.stats(user);
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount(@AuthenticationPrincipal User user) {
        return Map.of("count", notificationService.unreadCount(user));
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<Void> markRead(@PathVariable Long id, @AuthenticationPrincipal User user) {
        notificationService.markRead(id, user);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/read-all")
    public ResponseEntity<Void> markAllRead(@AuthenticationPrincipal User user) {
        notificationService.markAllRead(user);
        return ResponseEntity.ok().build();
    }
}

package behzoddev.testproject.entity;

import behzoddev.testproject.entity.enums.RoleAuditAction;
import behzoddev.testproject.entity.enums.RoleAuditSource;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

// Rol o'zgarishlari tarixi (audit log) — kim, qachon, kimga qanday rol
// berdi/olib tashladi. Checkbox orqali qo'lda qilingan o'zgarishlar ham,
// Subscription orqali avtomatik berilgan/olib tashlangan ADMIN ham shu
// yerda birlashtirilgan holda saqlanadi.
@Entity
@Table(name = "role_audit_logs")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RoleAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Roli o'zgargan foydalanuvchi.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_user_id", nullable = false)
    private User targetUser;

    // Kim o'zgartirdi. SYSTEM manba (masalan avtomatik obuna muddati
    // tugashi) uchun null bo'lishi mumkin — inson ishtirok etmagan.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "changed_by_id")
    private User changedBy;

    @Column(name = "role_name", nullable = false, length = 50)
    private String roleName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RoleAuditAction action;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RoleAuditSource source;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}

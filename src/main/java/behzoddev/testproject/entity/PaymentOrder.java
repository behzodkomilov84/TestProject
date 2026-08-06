package behzoddev.testproject.entity;

import behzoddev.testproject.entity.enums.PaymentOrderStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// Foydalanuvchi ROLE_ADMIN obunasini onlayn (Payme/Click) sotib olishni
// boshlaganda yaratiladigan "buyurtma" — shlyuzga yuboriladigan checkout
// link'ida shu yozuvning ID'si order_id/merchant_trans_id sifatida
// ishlatiladi, shlyuz keyin webhook orqali shu ID bo'yicha to'lovni
// bog'laydi. Bitta order uchun (agar foydalanuvchi bekor qilib qayta
// urinsa) bir nechta PaymentTransaction bo'lishi mumkin.
@Entity
@Table(name = "payment_orders")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PaymentOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "duration_months", nullable = false)
    private int durationMonths;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentOrderStatus status;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    // To'lov muvaffaqiyatli bo'lganda (markPaid) shu order asosida yaratilgan
    // Subscription'ning ID'si — keyinchalik chargeback/qaytarish (CancelTransaction
    // Perform'dan KEYIN kelsa) sodir bo'lsa, aynan SHU obunani bekor qilish
    // uchun kerak (aks holda "boshqa faol obuna bormi" tekshiruvi doim shu
    // obunaning o'zini topib, hech qachon haqiqatda bekor qilinmagan bo'lib chiqardi).
    @Column(name = "subscription_id")
    private Long subscriptionId;
}

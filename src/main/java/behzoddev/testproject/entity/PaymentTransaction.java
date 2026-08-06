package behzoddev.testproject.entity;

import behzoddev.testproject.entity.enums.PaymentProvider;
import behzoddev.testproject.entity.enums.PaymentTransactionState;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// Shlyuz tomonidan yaratilgan haqiqiy tranzaksiya (Payme'ning CreateTransaction
// yoki Click'ning Prepare chaqiruvida paydo bo'ladi). providerTransactionId —
// shlyuzning o'zi bergan ID (Payme: params.id, Click: click_trans_id) —
// keyingi barcha chaqiruvlar (Perform/Cancel/Complete) shu ID orqali keladi,
// shuning uchun bu ustun amaliy kalit vazifasini o'taydi (idempotentlik).
@Entity
@Table(name = "payment_transactions")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PaymentTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private PaymentOrder order;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private PaymentProvider provider;

    // Payme: params.id (24 xonali hex). Click: click_trans_id (raqam, string'ga o'girilgan).
    @Column(name = "provider_transaction_id", nullable = false, length = 100)
    private String providerTransactionId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private PaymentTransactionState state;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @Column(name = "perform_time")
    private LocalDateTime performTime;

    @Column(name = "cancel_time")
    private LocalDateTime cancelTime;

    // Payme: CancelTransaction'dagi "reason" kodi. Click uchun ishlatilmaydi.
    @Column(name = "cancel_reason")
    private Integer cancelReason;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}

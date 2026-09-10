package behzoddev.testproject.dao;

import behzoddev.testproject.entity.PaymentOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PaymentOrderRepository extends JpaRepository<PaymentOrder, Long> {

    List<PaymentOrder> findByUser_IdOrderByCreatedAtDesc(Long userId);

    // Foydalanuvchini o'chirishdan oldin — "payment_orders.user_id" NOT
    // NULL FK RESTRICT (haqiqiy topilgan bug, 2026-09-07). O'zining
    // to'lov buyurtmalari — o'chirilib ketaveradi.
    void deleteByUser_Id(Long userId);

    // HAQIQIY TOPILGAN BUG (foydalanuvchi so'rovi, 2026-09-09/10: Click
    // panelida to'lov "muvaffaqiyatli" ko'rinsa-da, /payments'da butunlay
    // yo'q edi) — "subscription_id" haqiqiy FK EMAS (oddiy Long ustun),
    // shuning uchun Subscription/CourseSubscription.delete() (🗑️
    // O'chirish) chaqirilganda bu ustun tozalanmasdi — Click'dan real
    // to'langan buyurtma "osilib qolgan" (dangling) subscription_id bilan
    // qolib, hech qanday joyda ko'rinmay qolardi. clearConfirmedBy() bilan
    // bir xil andoza — o'chirilgan obunaga bo'lgan havola NULL qilinadi.
    @Modifying
    @Query("UPDATE PaymentOrder p SET p.subscriptionId = NULL WHERE p.subscriptionId = :subscriptionId")
    void clearSubscriptionId(@Param("subscriptionId") Long subscriptionId);
}

package behzoddev.testproject.dao;

import behzoddev.testproject.entity.PaymentTransaction;
import behzoddev.testproject.entity.enums.PaymentProvider;
import behzoddev.testproject.entity.enums.PaymentTransactionState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {

    Optional<PaymentTransaction> findByProviderAndProviderTransactionId(PaymentProvider provider, String providerTransactionId);

    // Bitta order uchun hali bekor qilinmagan (CREATED yoki PERFORMED) tranzaksiya
    // bormi — Prepare'da "bu order allaqachon band" tekshiruvi uchun.
    List<PaymentTransaction> findByOrder_IdAndStateNot(Long orderId, PaymentTransactionState state);

    // Foydalanuvchini o'chirishdan OLDIN — "payment_transactions.order_id"
    // NOT NULL FK RESTRICT bo'lgani uchun (haqiqiy topilgan bug,
    // 2026-09-09: "Firuz"ni o'chirib bo'lmadi, "Cannot delete or update a
    // parent row: a foreign key constraint fails (payment_transactions,
    // fk_payment_transactions_order)" — UserServiceImpl.deleteUser()
    // paymentOrderRepository.deleteByUser_Id() chaqirardi, lekin shu
    // buyurtmalarga bog'langan tranzaksiyalarni OLDIN tozalamas edi).
    void deleteByOrder_User_Id(Long userId);
}

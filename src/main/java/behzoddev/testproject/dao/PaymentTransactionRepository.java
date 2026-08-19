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
}

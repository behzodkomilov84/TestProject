package behzoddev.testproject.dao;

import behzoddev.testproject.entity.PaymentOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaymentOrderRepository extends JpaRepository<PaymentOrder, Long> {

    List<PaymentOrder> findByUser_IdOrderByCreatedAtDesc(Long userId);

    // Foydalanuvchini o'chirishdan oldin — "payment_orders.user_id" NOT
    // NULL FK RESTRICT (haqiqiy topilgan bug, 2026-09-07). O'zining
    // to'lov buyurtmalari — o'chirilib ketaveradi.
    void deleteByUser_Id(Long userId);
}

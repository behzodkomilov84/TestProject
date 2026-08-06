package behzoddev.testproject.dao;

import behzoddev.testproject.entity.PaymentOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaymentOrderRepository extends JpaRepository<PaymentOrder, Long> {

    List<PaymentOrder> findByUser_IdOrderByCreatedAtDesc(Long userId);
}

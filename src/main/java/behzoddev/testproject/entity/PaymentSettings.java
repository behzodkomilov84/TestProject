package behzoddev.testproject.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

// Bitta qatorli (singleton, id=1) sozlamalar jadvali — hozircha faqat
// Click uchun minimal tranzaksiya summasini saqlaydi. Kelajakda
// boshqa to'lov sozlamalari ham shu yerga qo'shilishi mumkin.
@Entity
@Table(name = "payment_settings")
@Getter
@Setter
public class PaymentSettings {

    @Id
    private Long id;

    @Column(name = "min_amount_som", nullable = false, precision = 12, scale = 2)
    private BigDecimal minAmountSom;
}

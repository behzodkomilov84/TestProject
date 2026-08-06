package behzoddev.testproject.entity.enums;

// Payme/Click'ning o'z holatlariga umumlashtirilgan mos keladi:
// CREATED = Payme "1" / Click "0-tayyorlangan", PERFORMED = Payme "2" /
// Click "1-tugallangan", CANCELLED = Payme "-1/-2" / Click "-9".
public enum PaymentTransactionState {
    CREATED,
    PERFORMED,
    CANCELLED
}

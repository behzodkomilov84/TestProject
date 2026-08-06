package behzoddev.testproject.service.payment;

import lombok.Getter;

// Payme JSON-RPC xato kodlari bilan aniq javob qaytarish uchun (PaymeService.handle
// ichida ushlanadi va {"error": {"code":..., "message":...}} shakliga o'giriladi).
@Getter
public class PaymeException extends RuntimeException {

    private final int code;
    private final String field; // account xatolari uchun "data" maydoni (masalan "order_id"), aks holda null

    public PaymeException(int code, String message) {
        this(code, message, null);
    }

    public PaymeException(int code, String message, String field) {
        super(message);
        this.code = code;
        this.field = field;
    }
}

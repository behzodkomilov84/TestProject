package behzoddev.testproject.service.payment;

import lombok.Getter;

@Getter
public class ClickException extends RuntimeException {
    private final int code;

    public ClickException(int code, String message) {
        super(message);
        this.code = code;
    }
}

package behzoddev.testproject.validation;

import java.util.List;
import java.util.Objects;

public final class Validation {

    public static void validateName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("This field must not be empty");
        }
    }


}

package behzoddev.testproject.validation;

public final class Validation {

    public static void validateName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("This field must not be empty");
        }
    }
}

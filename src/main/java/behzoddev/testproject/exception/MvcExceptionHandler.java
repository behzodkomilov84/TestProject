package behzoddev.testproject.exception;

import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class MvcExceptionHandler {

    @ExceptionHandler(UserAlreadyExistsException.class)
    public String handleUserExists(
            UserAlreadyExistsException ex,
            Model model
    ) {
        model.addAttribute(
                "errorMessage",
                ex.getMessage()
        );
        return "registration-error";
    }


    @ExceptionHandler(PasswordsDoNotMatchException.class)
    public String handlePasswordsDoNotMatch(
            PasswordsDoNotMatchException ex,
            Model model
    ) {
        model.addAttribute(
                "errorMessage",
                ex.getMessage()
        );
        return "registration-error";
    }
}

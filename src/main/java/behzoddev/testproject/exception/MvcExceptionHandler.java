package behzoddev.testproject.exception;

import org.springframework.security.core.userdetails.UsernameNotFoundException;
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
        return "app-error";
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
        return "app-error";
    }

    @ExceptionHandler(UsernameNotFoundException.class)
    public String handleUsernameNotFoundException(
            UsernameNotFoundException ex,
            Model model
    ) {
        model.addAttribute(
                "errorMessage",
                ex.getMessage()
        );
        return "app-error";
    }

}

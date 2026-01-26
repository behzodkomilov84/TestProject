package behzoddev.testproject.controller.advice;

import behzoddev.testproject.exception.MethodArgumentNotValidException;
import behzoddev.testproject.exception.PasswordsDoNotMatchException;
import behzoddev.testproject.exception.UserAlreadyExistsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalRestExceptionHandler {

    private static final String ERROR_MESSAGE = "errorMessage";
    private static final String ERROR_PAGE = "app-error";

    @ExceptionHandler(UserAlreadyExistsException.class)
    public String handleUserExists(
            UserAlreadyExistsException ex,
            Model model
    ) {
        model.addAttribute(
                ERROR_MESSAGE,
                ex.getMessage()
        );

        return ERROR_PAGE;
    }


    @ExceptionHandler(PasswordsDoNotMatchException.class)
    public String handlePasswordsDoNotMatch(
            PasswordsDoNotMatchException ex,
            Model model
    ) {
        model.addAttribute(
                ERROR_MESSAGE,
                ex.getMessage()
        );
        return ERROR_PAGE;
    }

    @ExceptionHandler(UsernameNotFoundException.class)
    public String handleUsernameNotFoundException(
            UsernameNotFoundException ex,
            Model model
    ) {
        model.addAttribute(
                ERROR_MESSAGE,
                ex.getMessage()
        );
        return ERROR_PAGE;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public String handleMethodArgumentNotValidException(
            MethodArgumentNotValidException ex,
            Model model) {
        model.addAttribute(
                ERROR_MESSAGE,
                ex.getMessage()
        );
        return ERROR_PAGE;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public String handleIllegalArgumentException(
            IllegalArgumentException ex,
            Model model) {
        model.addAttribute(
                ERROR_MESSAGE,
                ex.getMessage()
        );
        return ERROR_PAGE;
    }

    @ExceptionHandler(Exception.class)
    public String handleAny(Exception ex, Model model) {
        model.addAttribute(ERROR_MESSAGE, "Internal error: " + ex.getMessage());
        return ERROR_PAGE;
    }

}

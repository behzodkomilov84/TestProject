package behzoddev.testproject.controller.advice;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class GlobalRestExceptionHandler {

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleAnyException(Exception ex) {

        return ResponseEntity
                .badRequest()
                .body(Map.of(
                        "error", ex.getMessage()
                ));
    }

}

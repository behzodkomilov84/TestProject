package behzoddev.testproject.validation;

import behzoddev.testproject.service.AnswerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public  class Validation {

    private final AnswerService answerService;

    public void textFieldMustNotBeEmpty(String textField) {
        if (textField == null || textField.trim().isEmpty()) {
            throw new IllegalArgumentException("❌Maydon bo'sh bo'lishi mumkin emas.");
        }
    }

    public void textFieldOfListMustNotBeEmpty(List<String> answerTextList) {

        if (!answerService.isUnique(answerTextList)) {
            throw new IllegalArgumentException("❌Javoblar bir xil bo'lishi mumkin emas.");
        }

        answerTextList.forEach(answerText -> textFieldMustNotBeEmpty(answerText));
    }


}

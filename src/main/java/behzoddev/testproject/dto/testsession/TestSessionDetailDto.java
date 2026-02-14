package behzoddev.testproject.dto.testsession;

public record TestSessionDetailDto (String questionText,
                                   String selectedAnswer,
                                   String correctAnswer,
                                   String commentOfCorrectAnswer,
                                   boolean correct){}

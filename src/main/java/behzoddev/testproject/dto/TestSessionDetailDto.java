package behzoddev.testproject.dto;

public record TestSessionDetailDto (String questionText,
                                   String selectedAnswer,
                                   String correctAnswer,
                                   String commentOfCorrectAnswer,
                                   boolean correct){}

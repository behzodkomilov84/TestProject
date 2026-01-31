package behzoddev.testproject.dto;

public record TestSessionDetailDto (String questionText,
                                   String selectedAnswer,
                                   String correctAnswer,
                                   boolean correct){}

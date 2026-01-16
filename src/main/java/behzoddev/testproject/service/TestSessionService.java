package behzoddev.testproject.service;

import behzoddev.testproject.dao.AnswerRepository;
import behzoddev.testproject.dao.QuestionRepository;
import behzoddev.testproject.dto.AnswerIdAndTextDto;
import behzoddev.testproject.dto.TestQuestionDto;
import behzoddev.testproject.entity.Question;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TestSessionService {

    private final QuestionRepository questionRepository;
    private final AnswerRepository answerRepository;

    public List<TestQuestionDto> startTest(List<Long> topicIds, int limit) {
        List<Question> questions = questionRepository.findRandomQuestionsByTopicIds(topicIds);

        Collections.shuffle(questions);
        questions = questions.stream().limit(limit).toList();

        return questions.stream().map(q -> {
            var answers = q.getAnswers().stream()
                    .map(a -> new AnswerIdAndTextDto(a.getId(), a.getAnswerText()))
                    .collect(Collectors.toList());

            Collections.shuffle(answers);

            return new TestQuestionDto(q.getId(), q.getQuestionText(), answers);
        }).toList();
    }

    public int checkAnswers(Map<Long, Long> answers) {
        int correct = 0;

        for (var entry : answers.entrySet()) {
            if (answerRepository.isCorrect(entry.getKey(), entry.getValue())) {
                correct++;
            }
        }
        return correct;
    }
}

package behzoddev.testproject.service;

import behzoddev.testproject.dao.AnswerRepository;
import behzoddev.testproject.dao.QuestionRepository;
import behzoddev.testproject.dto.AnswerDto;
import behzoddev.testproject.dto.QuestionDto;
import behzoddev.testproject.entity.Question;
import behzoddev.testproject.mapper.AnswerMapper;
import behzoddev.testproject.mapper.QuestionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TestSessionService {

    private final QuestionRepository questionRepository;
    private final AnswerRepository answerRepository;
    private final QuestionMapper questionMapper;
    private final AnswerMapper answerMapper;

    @Transactional
    public List<QuestionDto> startTest(List<Long> topicIds, int limit) {
        List<Question> questions = questionRepository.findRandomQuestionsByTopicIds(topicIds);

        Collections.shuffle(questions);
        questions = questions.stream().limit(limit).toList();

        List<QuestionDto> questionDtoList = questionMapper.mapQuestionListToQuestionDtoList(questions);

        return questionDtoList.stream()
                .map(dto -> {
                    List<AnswerDto> answerDtoList = dto.answers();

                    Collections.shuffle(answerDtoList);

                    return new QuestionDto(dto.id(), dto.questionText(), answerDtoList);
                }).toList();
    }

    @Transactional
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

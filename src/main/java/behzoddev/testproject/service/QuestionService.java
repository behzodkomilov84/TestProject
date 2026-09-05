package behzoddev.testproject.service;

import behzoddev.testproject.dao.AnswerRepository;
import behzoddev.testproject.dao.QuestionRepository;
import behzoddev.testproject.dao.TopicRepository;
import behzoddev.testproject.dto.answer.AnswerDto;
import behzoddev.testproject.dto.answer.AnswerShortDto;
import behzoddev.testproject.dto.question.QuestionDto;
import behzoddev.testproject.dto.question.QuestionSaveDto;
import behzoddev.testproject.dto.question.QuestionScienceSearchDto;
import behzoddev.testproject.dto.question.QuestionScienceTrashDto;
import behzoddev.testproject.dto.question.QuestionShortDto;
import behzoddev.testproject.dto.question.QuestionTrashDto;
import behzoddev.testproject.dto.teacher.ResponseQuestionTextDto;
import behzoddev.testproject.entity.Answer;
import behzoddev.testproject.entity.Question;
import behzoddev.testproject.entity.Topic;
import behzoddev.testproject.mapper.AnswerMapper;
import behzoddev.testproject.mapper.QuestionMapper;
import behzoddev.testproject.validation.Validation;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class QuestionService {
    private final AnswerRepository answerRepository;
    private final QuestionRepository questionRepository;
    private final TopicRepository topicRepository;
    private final QuestionMapper questionMapper;
    private final AnswerMapper answerMapper;
    private final Validation validation;

    @Transactional(readOnly = true)
    public List<QuestionDto> getQuestionsByIds(Long scienceId, Long topicId) {
        List<Question> questions = questionRepository.getQuestionsByIds(scienceId, topicId);
        return questionMapper.mapQuestionListToQuestionDtoList(questions);
    }

    @Transactional(readOnly = true)
    public List<QuestionShortDto> getQuestionsByTopicId(Long topicId) {
        List<Question> questions = questionRepository.getQuestionsByTopicId(topicId);
        return questionMapper.mapQuestionListToQuestionShortDtoList(questions);
    }

    @Transactional(readOnly = true)
    public List<QuestionSaveDto> getQuestionSaveDtoByTopicId(Long topicId) {
        List<Question> questions = questionRepository.getQuestionsByTopicId(topicId);

        List<QuestionSaveDto> questionSaveDtoList = new ArrayList<>();

        for (Question question : questions) {
            questionSaveDtoList.add(
                    QuestionSaveDto.builder()
                            .topicId(question.getTopic().getId())
                            .questionText(question.getQuestionText())
                            .imageUrl(question.getImageUrl())
                            .answers(answerMapper.mapAnswerListToAnswerShorDtoList(question.getAnswers()))
                            .build());
        }

        return questionSaveDtoList;
    }

    @Transactional(readOnly = true)
    public boolean isQuestionWithAnswersExists(
            @NotNull List<QuestionShortDto> existingQuestions,
            QuestionShortDto newQuestion
    ) {
        return existingQuestions.stream()
                .filter(q -> q.questionText().equalsIgnoreCase(newQuestion.questionText()))
                .anyMatch(q -> {
                    List<AnswerShortDto> existingAnswers = q.answers();
                    List<AnswerShortDto> newAnswers = newQuestion.answers();

                    if (existingAnswers.size() != newAnswers.size()) {
                        return false; // количество ответов не совпадает
                    }

                    // проверяем, что каждый ответ newQuestion есть в existingAnswers
                    return newAnswers.stream()
                            .allMatch(newAns -> existingAnswers.stream()
                                    .anyMatch(existingAns -> existingAns.answerText().equalsIgnoreCase(newAns.answerText())
                                            && existingAns.isTrue().equals(newAns.isTrue())
                                    )
                            );
                });
    }

    @Transactional(readOnly = true)
    public boolean isQuestionWithAnswersExists(
            @NotNull List<QuestionSaveDto> existingQuestions,
            QuestionSaveDto newQuestion
    ) {
        Set<String> newAnswerSet = newQuestion.answers().stream()
                .map(this::normalizeAnswer)
                .collect(Collectors.toSet());

        return existingQuestions.stream()
                .filter(q -> q.questionText().equalsIgnoreCase(newQuestion.questionText()))
                .anyMatch(q -> {
                    Set<String> existingAnswerSet = q.answers().stream()
                            .map(this::normalizeAnswer)
                            .collect(Collectors.toSet());

                    return existingAnswerSet.equals(newAnswerSet);
                });
    }

    private String normalizeAnswer(AnswerShortDto a) {
        return a.answerText().trim().toLowerCase();
    }

    @Transactional
    public Question saveQuestion(Long topicId, QuestionShortDto newQuestion) {

        validation.textFieldMustNotBeEmpty(newQuestion.questionText());

        Question question = questionMapper.mapQuestionShortDtoToQuestion(newQuestion);

        if (question.getAnswers() != null) {
            for (Answer answer : question.getAnswers()) {

                validation.textFieldMustNotBeEmpty(answer.getAnswerText());
                validation.textFieldMustNotBeEmpty(answer.getCommentary());

                answer.setQuestion(question);
            }
        }
        question.setTopic(topicRepository.findById(topicId).orElse(null));
        return questionRepository.save(question);
    }

    @Transactional(readOnly = true)
    public QuestionDto getQuestionById(Long questionId) {
        Question question = questionRepository.getQuestionById(questionId);
        return questionMapper.mapQuestiontoQuestionDto(question);
    }

    // REQUIRES_NEW: ExcelService.importQuestions() har bir qatorni shu
    // metod orqali saqlaydi. Agar oddiy @Transactional (REQUIRED) bo'lsa,
    // bitta qatordagi DB xatosi (masalan "Data too long") butun tashqi
    // tranzaksiyani "rollback-only" qilib qo'yardi — natijada BARCHA
    // qatorlar (hatto to'g'rilari ham) import qilinmay qolardi va
    // foydalanuvchiga "hech narsa o'zgarmadi" bo'lib ko'rinardi. Har bir
    // savol O'Z ALOHIDA tranzaksiyasida saqlansa, bitta yomon qator
    // faqat O'ZINI buzadi — qolganlari muvaffaqiyatli import bo'ladi.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void save(QuestionSaveDto questionSaveDto) {
        Topic topic = topicRepository.getTopicById(questionSaveDto.topicId());

        validation.textFieldMustNotBeEmpty(questionSaveDto.questionText());
        Integer maxOrderIndex = questionRepository.findMaxOrderIndexByTopicId(questionSaveDto.topicId());
        Question newQuestion = Question.builder()
                .questionText(questionSaveDto.questionText())
                .imageUrl(questionSaveDto.imageUrl())
                .imageWidth(questionSaveDto.imageWidth())
                .imageHeight(questionSaveDto.imageHeight())
                .topic(topic)
                .orderIndex(maxOrderIndex == null ? 1 : maxOrderIndex + 1)
                .build();

        Question savedQuestion = questionRepository.save(newQuestion);

        List<AnswerShortDto> answerShortDtoList = questionSaveDto.answers();

        List<Answer> answerList = answerShortDtoList.stream().
                map(answerShortDto -> {
                    validation.textFieldMustNotBeEmpty(answerShortDto.answerText());
                    validation.textFieldMustNotBeEmpty(answerShortDto.commentary());
                    Answer answer = answerMapper.mapAnswerShortDtoToAnswer(answerShortDto);
                    answer.setQuestion(savedQuestion);
                    return answer;
                }).toList();

        answerRepository.saveAll(answerList);
    }

    // "O'chirilganlar savati"ga o'tkazish (soft-delete) — DARHOL butunlay
    // o'chirilmaydi, javoblari (Answer) HAM tegilmay saqlanadi — "♻️
    // Tiklash" bilan bir zumda qaytadi (CourseService.deleteCourse bilan
    // bir xil g'oya).
    @Transactional
    public void deleteQuestion(Long questionId) {
        Question question = getQuestionOrThrow(questionId);
        question.setDeletedAt(LocalDateTime.now());
        questionRepository.save(question);
    }

    // Guruh holatida o'chirish (question.js — jadvaldagi checkbox'lar
    // orqali belgilangan savollar) — bitta so'rovda BARCHASI soft-delete
    // qilinadi. Allaqachon o'chirilgan (yoki mavjud bo'lmagan) id'lar jim
    // o'tkazib yuboriladi — bitta noto'g'ri id butun amaliyotni
    // to'xtatib qo'ymasligi uchun. Qaytariladigan son — HAQIQATDA
    // o'chirilganlar (frontend'da tasdiq xabari uchun).
    @Transactional
    public int deleteQuestions(List<Long> questionIds) {
        if (questionIds == null || questionIds.isEmpty()) {
            return 0;
        }
        LocalDateTime now = LocalDateTime.now();
        List<Question> questions = questionRepository.findAllById(questionIds).stream()
                .filter(q -> q.getDeletedAt() == null)
                .toList();
        questions.forEach(q -> q.setDeletedAt(now));
        questionRepository.saveAll(questions);
        return questions.size();
    }

    // "O'chirilganlar savati" ro'yxati (mavzu ichida).
    @Transactional(readOnly = true)
    public List<QuestionTrashDto> getDeletedQuestions(Long topicId) {
        return questionRepository.findDeletedByTopic_Id(topicId);
    }

    // "O'chirilganlar savati" — BUTUN FAN bo'yicha (topics.html'dagi
    // global savol savati, barcha mavzular birga, har biri qaysi
    // mavzu/bo'limga tegishli ekani bilan).
    @Transactional(readOnly = true)
    public List<QuestionScienceTrashDto> getDeletedQuestionsByScience(Long scienceId) {
        return questionRepository.findDeletedByScienceId(scienceId);
    }

    // science.html'dagi 🔍 "Bo'lim ichida qidiruv" modali — butun Fan
    // bo'yicha (barcha Mavzu -> Dars -> Savol ierarxiyasi kesib o'tib)
    // savol matnidan qidiradi. Bo'sh/juda qisqa so'rovda BUTUN ro'yxatni
    // tashlab yubormaslik uchun ataylab bo'sh natija qaytariladi (foydalanuvchi
    // biror narsa yozguncha kutiladi) — natija soni 200 taga cheklangan.
    @Transactional(readOnly = true)
    public List<QuestionScienceSearchDto> searchQuestionsByScience(Long scienceId, String search) {
        if (search == null || search.isBlank()) {
            return List.of();
        }
        return questionRepository.searchByScienceId(scienceId, search.trim(), PageRequest.of(0, 200));
    }

    // Guruh holatida "♻️ Tiklash" (question.js — savatdagi checkbox'lar
    // orqali belgilangan savollar) — bitta so'rovda BARCHASI savatdan
    // qaytariladi. FAQAT allaqachon savatda (soft-delete qilingan)
    // turganlarga nisbatan — deleteQuestions/permanentlyDeleteQuestions
    // bilan bir xil "jim o'tkazib yuborish" qoidasi.
    @Transactional
    public int restoreQuestions(List<Long> questionIds) {
        if (questionIds == null || questionIds.isEmpty()) {
            return 0;
        }
        List<Question> questions = questionRepository.findAllById(questionIds).stream()
                .filter(q -> q.getDeletedAt() != null)
                .toList();
        questions.forEach(q -> q.setDeletedAt(null));
        questionRepository.saveAll(questions);
        return questions.size();
    }

    // "♻️ Tiklash" — savolni savatdan qaytaradi, javoblari avtomatik yana
    // ko'rinadigan bo'ladi (ular hech qachon o'chirilmagan edi).
    @Transactional
    public void restoreQuestion(Long questionId) {
        Question question = getAnyQuestionOrThrow(questionId);
        if (question.getDeletedAt() == null) {
            throw new IllegalArgumentException("❌ Bu savol o'chirilmagan — tiklashning hojati yo'q.");
        }
        question.setDeletedAt(null);
        questionRepository.save(question);
    }

    // "🗑️ Butunlay o'chirish" — FAQAT allaqachon savatda turgan savolga
    // nisbatan. QAYTARIB BO'LMAYDI: javoblar (Answer) JPA orphanRemoval
    // orqali avtomatik o'chiriladi.
    @Transactional
    public void permanentlyDeleteQuestion(Long questionId) {
        Question question = getAnyQuestionOrThrow(questionId);
        if (question.getDeletedAt() == null) {
            throw new IllegalArgumentException(
                    "❌ Bu savolni butunlay o'chirishdan oldin, avval oddiy \"O'chirish\" orqali savatga o'tkazish kerak.");
        }
        questionRepository.delete(question);
    }

    // Guruh holatida BUTUNLAY o'chirish (question.js — savatdagi
    // checkbox'lar orqali belgilangan savollar). FAQAT allaqachon savatda
    // (soft-delete qilingan) turganlarga nisbatan — hali savatga
    // o'tkazilmagan (faol) savol tasodifan shu yerdan o'chib ketmasligi
    // uchun (bitta-bitta permanentlyDeleteQuestion bilan bir xil qoida).
    @Transactional
    public int permanentlyDeleteQuestions(List<Long> questionIds) {
        if (questionIds == null || questionIds.isEmpty()) {
            return 0;
        }
        List<Question> questions = questionRepository.findAllById(questionIds).stream()
                .filter(q -> q.getDeletedAt() != null)
                .toList();
        questionRepository.deleteAll(questions);
        return questions.size();
    }

    private Question getQuestionOrThrow(Long questionId) {
        Question question = getAnyQuestionOrThrow(questionId);
        if (question.getDeletedAt() != null) {
            throw new NoSuchElementException("Savol topilmadi");
        }
        return question;
    }

    // FAQAT "O'chirilganlar savati" amallari (restoreQuestion,
    // permanentlyDeleteQuestion, getDeletedQuestions) uchun — soft-delete
    // qilingan savolni ham topa oladi.
    private Question getAnyQuestionOrThrow(Long questionId) {
        return questionRepository.findById(questionId)
                .orElseThrow(() -> new NoSuchElementException("Savol topilmadi"));
    }

    @Transactional
    public void updateQuestion(QuestionDto dto) {
        // 1️⃣ ВАЛИДАЦИЯ (СНАЧАЛА!)
        List<String> answerTextList = dto.answers().stream()
                .map(AnswerDto::answerText)
                .toList();

        validation.textFieldOfListMustNotBeEmpty(answerTextList);

        // 2️⃣ ЗАГРУЗКА
        Question question = questionRepository.findById(dto.id())
                .orElseThrow(() ->
                        new IllegalArgumentException("Savol ma'lumotlar bazasida topilmadi."));

        // O'zini o'ziga "dublikat" deb hisoblab qo'ymaslik uchun hozir tahrirlanayotgan
        // savolni "mavjud savollar" ro'yxatidan chiqarib tashlaymiz (aks holda matn/javoblar
        // o'zgarmagan holda faqat rasm/video qo'shilganda ham dublikat xatosi chiqadi).
        List<Question> existingQuestions = questionRepository.getQuestionsByTopicId(question.getTopic().getId())
                .stream()
                .filter(q -> !q.getId().equals(question.getId()))
                .toList();

        List<QuestionSaveDto> existingQuestionSaveDtos =
                existingQuestions.stream()
                        .map(q ->
                                QuestionSaveDto.builder()
                                        .topicId(q.getTopic().getId())
                                        .questionText(q.getQuestionText())
                                        .imageUrl(q.getImageUrl())
                                        .answers(answerMapper.mapAnswerListToAnswerShorDtoList(q.getAnswers()))
                                        .build()
                        ).toList();

        QuestionSaveDto newUpdatingQuestion =
                QuestionSaveDto.builder()
                        .topicId(question.getTopic().getId())
                        .questionText(dto.questionText())
                        .imageUrl(dto.imageUrl())
                        .answers(answerMapper.mapAnswerDtoListToAnswerShorDtoList(dto.answers()))
                        .build();

        boolean questionWithAnswersExists = isQuestionWithAnswersExists(existingQuestionSaveDtos, newUpdatingQuestion);
        if (questionWithAnswersExists) {
            throw new IllegalArgumentException("Bunday javoblarga ega savol allaqachon mavjud.");
        }


        // 3️⃣ ОБНОВЛЕНИЕ ВОПРОСА
        validation.textFieldMustNotBeEmpty(dto.questionText().trim());
        question.setQuestionText(dto.questionText().trim());
        question.setImageUrl(dto.imageUrl());
        question.setImageWidth(dto.imageWidth());
        question.setImageHeight(dto.imageHeight());

        // 4️⃣ СБРОС ВСЕХ ОТВЕТОВ
        for (Answer answer : question.getAnswers()) {
            answer.setIsTrue(false);
            answer.setCommentary("noto'g'ri javob");
        }

        // 5️⃣ УСТАНОВКА НОВЫХ ЗНАЧЕНИЙ
        for (AnswerDto aDto : dto.answers()) {

            Answer answer = answerRepository.findById(aDto.id())
                    .orElseThrow(() ->
                            new IllegalArgumentException("Javob ma'lumotlar bazasida topilmadi."));

            answer.setAnswerText(aDto.answerText().trim());
            answer.setIsTrue(aDto.isTrue());
            answer.setImageUrl(aDto.imageUrl());
            answer.setImageWidth(aDto.imageWidth());
            answer.setImageHeight(aDto.imageHeight());
            answer.setCommentaryImageUrl(aDto.commentaryImageUrl());
            answer.setCommentaryVideoUrl(aDto.commentaryVideoUrl());

            if (Boolean.TRUE.equals(aDto.isTrue())) {
                validation.textFieldMustNotBeEmpty(aDto.commentary());
                answer.setCommentary(aDto.commentary().trim());
            }
        }
    }

    @Transactional(readOnly = true)
    public Page<QuestionDto> getQuestionDtoPageByTopicId(Long topicId, String search, Pageable pageable) {
        Page<Question> page;

        if (search == null || search.isBlank()) {
            page = questionRepository.findByTopicId(topicId, pageable);
        } else {
            page = questionRepository
                    .findByTopicIdAndQuestionTextContainingIgnoreCase(
                            topicId,
                            search,
                            pageable
                    );
        }

        return page.map(question -> questionMapper.mapQuestiontoQuestionDto(question));
    }

    @Transactional(readOnly = true)
    public List<QuestionDto> findAll(Long topicId, String q) {

        List<Question> list;

        if (q == null || q.isBlank()) {
            list = questionRepository.findByTopicIdAndDeletedAtIsNullOrderByOrderIndexAsc(topicId);
        } else {
            list = questionRepository
                    .findByTopicIdAndQuestionTextContainingIgnoreCaseAndDeletedAtIsNullOrderByOrderIndexAsc(
                            topicId,
                            q
                    );
        }

        return questionMapper.mapQuestionListToQuestionDtoList(list);
    }

    // Savollar tartibini qayta belgilash ("⬆⬇" yoki A-Z/Z-A saralashdan
    // keyin, faqat "Hammasi" (isAllMode) rejimida, question.js) —
    // to'liq yangi tartibdagi id ro'yxati (TopicService.reorderTopics /
    // ScienceService.reorderSciences bilan bir xil andoza).
    @Transactional
    public void reorderQuestions(Long topicId, List<Long> orderedQuestionIds) {
        List<Question> questions = questionRepository.findActiveByTopicIdOrderByOrderIndex(topicId);
        Map<Long, Question> byId = new LinkedHashMap<>();
        for (Question q : questions) byId.put(q.getId(), q);
        if (orderedQuestionIds.size() != questions.size() || !byId.keySet().containsAll(orderedQuestionIds)) {
            throw new IllegalArgumentException("❌Savollar ro'yxati mavzuning savollariga mos kelmayapti.");
        }
        int index = 1;
        for (Long id : orderedQuestionIds) byId.get(id).setOrderIndex(index++);
        questionRepository.saveAll(questions);
    }

    public List<ResponseQuestionTextDto> getQuestionsByTopic(Long topicId) {
        // Преобразуем сущности Question в DTO
        return questionRepository.findByTopicIdAndDeletedAtIsNullOrderByOrderIndexAsc(topicId)
                .stream()
                .map(q -> questionMapper.mapQuestionToResponseQuestionTextDto(q))
                .toList();
    }
}



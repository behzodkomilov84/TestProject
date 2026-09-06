package behzoddev.testproject.service;

import behzoddev.testproject.dao.CourseForumReplyRepository;
import behzoddev.testproject.dao.CourseForumThreadRepository;
import behzoddev.testproject.dao.CourseRepository;
import behzoddev.testproject.dto.course.ForumReplyDto;
import behzoddev.testproject.dto.course.ForumThreadDto;
import behzoddev.testproject.dto.course.ForumThreadReplyCountDto;
import behzoddev.testproject.dto.course.ForumThreadsResponseDto;
import behzoddev.testproject.entity.Course;
import behzoddev.testproject.entity.CourseForumReply;
import behzoddev.testproject.entity.CourseForumThread;
import behzoddev.testproject.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

// "Forum" — kurs foydalanuvchisi kurs muallifiga (yoki umuman kursga)
// savol berishi, XOHLAGAN boshqa foydalanuvchi esa shu savolga javob
// yozishi mumkin bo'lgan muhokama taxtasi (foydalanuvchi so'rovi,
// 2026-09-06: "Курсдан фойдаланувчилар курс яратувчисига саволларини
// бера олиши учун ва бошқа фойдаланувчилар ҳам бу саволга жавоб бера
// олсалар"). Kirish huquqi — CourseService#getDetail bilan BIR XIL
// oddiy qoida (nashr qilingan YOKI shu kursni boshqara oladigan
// foydalanuvchi) — forum kurs OBUNASIGA emas, kursning O'ZIGA tegishli
// (hatto obuna bo'lmagan, lekin nashr etilgan kursni ko'rayotgan
// foydalanuvchi ham savol berishi mumkin).
@Service
@RequiredArgsConstructor
public class CourseForumService {

    private final CourseForumThreadRepository threadRepository;
    private final CourseForumReplyRepository replyRepository;
    private final CourseRepository courseRepository;

    @Transactional(readOnly = true)
    public ForumThreadsResponseDto listThreads(Long courseId, User currentUser) {
        Course course = requireViewableCourse(courseId, currentUser);
        List<CourseForumThread> threads = threadRepository
                .findByCourse_IdAndDeletedAtIsNullOrderByCreatedAtDesc(courseId);

        List<Long> threadIds = threads.stream().map(CourseForumThread::getId).toList();
        Map<Long, Long> replyCounts = threadIds.isEmpty()
                ? Map.of()
                : replyRepository.countGroupedByThreadIds(threadIds).stream()
                        .collect(Collectors.toMap(ForumThreadReplyCountDto::threadId, ForumThreadReplyCountDto::count));

        Long creatorId = creatorId(course);
        List<ForumThreadDto> dtos = threads.stream()
                .map(t -> toDto(t, creatorId, replyCounts.getOrDefault(t.getId(), 0L)))
                .toList();
        return new ForumThreadsResponseDto(dtos, canManageCourse(course, currentUser));
    }

    @Transactional
    public ForumThreadDto createThread(Long courseId, String questionText, User currentUser) {
        Course course = requireViewableCourse(courseId, currentUser);
        String trimmed = requireNonBlank(questionText, "❌ Savol matnini kiriting.");

        CourseForumThread thread = CourseForumThread.builder()
                .course(course)
                .author(currentUser)
                .questionText(trimmed)
                .build();
        threadRepository.save(thread);

        return toDto(thread, creatorId(course), 0);
    }

    @Transactional(readOnly = true)
    public List<ForumReplyDto> listReplies(Long threadId, User currentUser) {
        CourseForumThread thread = getThreadOrThrow(threadId);
        requireViewableCourse(thread.getCourse().getId(), currentUser);

        Long creatorId = creatorId(thread.getCourse());
        return replyRepository.findByThread_IdAndDeletedAtIsNullOrderByCreatedAtAsc(threadId).stream()
                .map(r -> toReplyDto(r, creatorId))
                .toList();
    }

    @Transactional
    public ForumReplyDto createReply(Long threadId, String replyText, User currentUser) {
        CourseForumThread thread = getThreadOrThrow(threadId);
        requireViewableCourse(thread.getCourse().getId(), currentUser);
        String trimmed = requireNonBlank(replyText, "❌ Javob matnini kiriting.");

        CourseForumReply reply = CourseForumReply.builder()
                .thread(thread)
                .author(currentUser)
                .replyText(trimmed)
                .build();
        replyRepository.save(reply);

        return toReplyDto(reply, creatorId(thread.getCourse()));
    }

    // Savolni o'chirish — FAQAT savol muallifi yoki shu kursni boshqara
    // oladigan foydalanuvchi (CourseService#canManageCourse bilan bir
    // xil qoida). Javoblar ALOHIDA o'chirilmaydi (savol o'zi
    // "O'chirilganlar savati" g'oyasi bilan yashiriladi, javoblar
    // ma'lumot sifatida saqlanib qoladi — hard-delete emas).
    @Transactional
    public void deleteThread(Long threadId, User currentUser) {
        CourseForumThread thread = getThreadOrThrow(threadId);
        checkCanModify(thread.getAuthor().getId(), thread.getCourse(), currentUser);
        thread.setDeletedAt(LocalDateTime.now());
        threadRepository.save(thread);
    }

    @Transactional
    public void deleteReply(Long replyId, User currentUser) {
        CourseForumReply reply = replyRepository.findById(replyId)
                .filter(r -> r.getDeletedAt() == null)
                .orElseThrow(() -> new NoSuchElementException("Javob topilmadi"));
        checkCanModify(reply.getAuthor().getId(), reply.getThread().getCourse(), currentUser);
        reply.setDeletedAt(LocalDateTime.now());
        replyRepository.save(reply);
    }

    private void checkCanModify(Long authorId, Course course, User currentUser) {
        boolean isAuthor = authorId.equals(currentUser.getId());
        boolean canManage = canManageCourse(course, currentUser);
        if (!isAuthor && !canManage) {
            throw new AccessDeniedException("⛔ Faqat o'zingiz yozgan yozuvni yoki o'zingiz boshqara oladigan kursdagi yozuvlarni o'chira olasiz.");
        }
    }

    private boolean canManageCourse(Course course, User user) {
        return user.hasRole("ROLE_OWNER")
                || (course.getCreatedBy() != null && course.getCreatedBy().getId().equals(user.getId()));
    }

    // CourseService#getDetail'dagi bilan BIR XIL oddiy kirish qoidasi —
    // nashr qilinmagan (qoralama) kursni FAQAT uni boshqara oladigan
    // foydalanuvchi ko'rishi (va shu sabab forumida yozishi) mumkin.
    private Course requireViewableCourse(Long courseId, User currentUser) {
        Course course = courseRepository.findById(courseId)
                .filter(c -> c.getDeletedAt() == null)
                .orElseThrow(() -> new NoSuchElementException("Kurs topilmadi"));

        if (!course.isPublished() && !canManageCourse(course, currentUser)) {
            throw new NoSuchElementException("Kurs topilmadi");
        }
        return course;
    }

    private CourseForumThread getThreadOrThrow(Long threadId) {
        return threadRepository.findById(threadId)
                .filter(t -> t.getDeletedAt() == null)
                .orElseThrow(() -> new NoSuchElementException("Savol topilmadi"));
    }

    private Long creatorId(Course course) {
        return course.getCreatedBy() != null ? course.getCreatedBy().getId() : null;
    }

    private String requireNonBlank(String text, String errorMessage) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException(errorMessage);
        }
        return text.trim();
    }

    private ForumThreadDto toDto(CourseForumThread t, Long creatorId, long replyCount) {
        return new ForumThreadDto(
                t.getId(),
                t.getQuestionText(),
                t.getAuthor().getId(),
                t.getAuthor().getUsername(),
                creatorId != null && creatorId.equals(t.getAuthor().getId()),
                t.getCreatedAt(),
                replyCount
        );
    }

    private ForumReplyDto toReplyDto(CourseForumReply r, Long creatorId) {
        return new ForumReplyDto(
                r.getId(),
                r.getReplyText(),
                r.getAuthor().getId(),
                r.getAuthor().getUsername(),
                creatorId != null && creatorId.equals(r.getAuthor().getId()),
                r.getCreatedAt()
        );
    }
}

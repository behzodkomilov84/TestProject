package behzoddev.testproject.service;

import behzoddev.testproject.dao.FeedbackReplyRepository;
import behzoddev.testproject.dao.FeedbackThreadRepository;
import behzoddev.testproject.dto.feedback.FeedbackReplyDto;
import behzoddev.testproject.dto.feedback.FeedbackThreadDto;
import behzoddev.testproject.dto.feedback.FeedbackThreadReplyCountDto;
import behzoddev.testproject.dto.feedback.FeedbackThreadsResponseDto;
import behzoddev.testproject.entity.FeedbackReply;
import behzoddev.testproject.entity.FeedbackStatus;
import behzoddev.testproject.entity.FeedbackThread;
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

// "Fikr va takliflar" — sayt bo'yicha (kursga bog'liq emas, global) ochiq
// fikr-taklif taxtasi: istalgan login qilgan foydalanuvchi yozadi, XOHLAGAN
// boshqa foydalanuvchi javob beradi (foydalanuvchi so'rovi, 2026-09-06).
// CourseForumService bilan bir xil andoza — farqi, "kursni boshqara
// oladi" o'rniga shunchaki "OWNER yoki ADMIN" tekshiriladi (global
// moderatsiya, muallif tushunchasi yo'q) va statusni o'zgartirish
// funksiyasi qo'shilgan.
@Service
@RequiredArgsConstructor
public class FeedbackService {

    private final FeedbackThreadRepository threadRepository;
    private final FeedbackReplyRepository replyRepository;

    @Transactional(readOnly = true)
    public FeedbackThreadsResponseDto listThreads(User currentUser) {
        List<FeedbackThread> threads = threadRepository.findByDeletedAtIsNullOrderByCreatedAtDesc();

        List<Long> threadIds = threads.stream().map(FeedbackThread::getId).toList();
        Map<Long, Long> replyCounts = threadIds.isEmpty()
                ? Map.of()
                : replyRepository.countGroupedByThreadIds(threadIds).stream()
                        .collect(Collectors.toMap(FeedbackThreadReplyCountDto::threadId, FeedbackThreadReplyCountDto::count));

        List<FeedbackThreadDto> dtos = threads.stream()
                .map(t -> toDto(t, replyCounts.getOrDefault(t.getId(), 0L)))
                .toList();
        return new FeedbackThreadsResponseDto(dtos, isStaff(currentUser));
    }

    @Transactional
    public FeedbackThreadDto createThread(String feedbackText, User currentUser) {
        String trimmed = requireNonBlank(feedbackText, "❌ Fikr-taklif matnini kiriting.");

        FeedbackThread thread = FeedbackThread.builder()
                .author(currentUser)
                .feedbackText(trimmed)
                .status(FeedbackStatus.OPEN)
                .build();
        threadRepository.save(thread);

        return toDto(thread, 0);
    }

    @Transactional(readOnly = true)
    public List<FeedbackReplyDto> listReplies(Long threadId) {
        getThreadOrThrow(threadId);
        return replyRepository.findByThread_IdAndDeletedAtIsNullOrderByCreatedAtAsc(threadId).stream()
                .map(this::toReplyDto)
                .toList();
    }

    @Transactional
    public FeedbackReplyDto createReply(Long threadId, String replyText, User currentUser) {
        FeedbackThread thread = getThreadOrThrow(threadId);
        String trimmed = requireNonBlank(replyText, "❌ Javob matnini kiriting.");

        FeedbackReply reply = FeedbackReply.builder()
                .thread(thread)
                .author(currentUser)
                .replyText(trimmed)
                .build();
        replyRepository.save(reply);

        return toReplyDto(reply);
    }

    // FAQAT OWNER/ADMIN o'zgartira oladi (foydalanuvchining o'zi emas —
    // status moderatsiya vositasi, "Ko'rib chiqilmoqda"/"Amalga oshirildi"
    // kabi belgilarni faqat jamoa qo'yishi kerak).
    @Transactional
    public FeedbackThreadDto updateStatus(Long threadId, String statusRaw, User currentUser) {
        if (!isStaff(currentUser)) {
            throw new AccessDeniedException("⛔ Faqat administratorlar statusni o'zgartira oladi.");
        }
        FeedbackThread thread = getThreadOrThrow(threadId);

        FeedbackStatus status;
        try {
            status = FeedbackStatus.valueOf(statusRaw);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalArgumentException("❌ Noto'g'ri status qiymati.");
        }

        thread.setStatus(status);
        threadRepository.save(thread);

        long replyCount = replyRepository.findByThread_IdAndDeletedAtIsNullOrderByCreatedAtAsc(threadId).size();
        return toDto(thread, replyCount);
    }

    // O'chirish — FAQAT muallif yoki OWNER/ADMIN (CourseForumService
    // #deleteThread bilan bir xil g'oya). Javoblar ALOHIDA o'chirilmaydi
    // (hard-delete emas, "deletedAt" bilan yashiriladi).
    @Transactional
    public void deleteThread(Long threadId, User currentUser) {
        FeedbackThread thread = getThreadOrThrow(threadId);
        checkCanModify(thread.getAuthor().getId(), currentUser);
        thread.setDeletedAt(LocalDateTime.now());
        threadRepository.save(thread);
    }

    @Transactional
    public void deleteReply(Long replyId, User currentUser) {
        FeedbackReply reply = replyRepository.findById(replyId)
                .filter(r -> r.getDeletedAt() == null)
                .orElseThrow(() -> new NoSuchElementException("Javob topilmadi"));
        checkCanModify(reply.getAuthor().getId(), currentUser);
        reply.setDeletedAt(LocalDateTime.now());
        replyRepository.save(reply);
    }

    private void checkCanModify(Long authorId, User currentUser) {
        boolean isAuthor = authorId.equals(currentUser.getId());
        if (!isAuthor && !isStaff(currentUser)) {
            throw new AccessDeniedException("⛔ Faqat o'zingiz yozgan yozuvni yoki administrator sifatida o'chira olasiz.");
        }
    }

    // Global moderatsiya huquqi — kurs tushunchasi yo'q, shuning uchun
    // CourseForumService#canManageCourse'dan farqli, faqat rolga qaraladi.
    private boolean isStaff(User user) {
        return user.hasRole("ROLE_OWNER") || user.hasRole("ROLE_ADMIN");
    }

    private FeedbackThread getThreadOrThrow(Long threadId) {
        return threadRepository.findById(threadId)
                .filter(t -> t.getDeletedAt() == null)
                .orElseThrow(() -> new NoSuchElementException("Fikr-taklif topilmadi"));
    }

    private String requireNonBlank(String text, String errorMessage) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException(errorMessage);
        }
        return text.trim();
    }

    private FeedbackThreadDto toDto(FeedbackThread t, long replyCount) {
        return new FeedbackThreadDto(
                t.getId(),
                t.getFeedbackText(),
                t.getAuthor().getId(),
                t.getAuthor().getUsername(),
                isStaff(t.getAuthor()),
                t.getStatus().name(),
                t.getCreatedAt(),
                replyCount
        );
    }

    private FeedbackReplyDto toReplyDto(FeedbackReply r) {
        return new FeedbackReplyDto(
                r.getId(),
                r.getReplyText(),
                r.getAuthor().getId(),
                r.getAuthor().getUsername(),
                isStaff(r.getAuthor()),
                r.getCreatedAt()
        );
    }
}

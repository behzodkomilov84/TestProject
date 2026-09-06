package behzoddev.testproject.controller.api;

import behzoddev.testproject.dto.feedback.FeedbackReplyDto;
import behzoddev.testproject.dto.feedback.FeedbackReplySaveDto;
import behzoddev.testproject.dto.feedback.FeedbackStatusUpdateDto;
import behzoddev.testproject.dto.feedback.FeedbackThreadDto;
import behzoddev.testproject.dto.feedback.FeedbackThreadSaveDto;
import behzoddev.testproject.dto.feedback.FeedbackThreadsResponseDto;
import behzoddev.testproject.entity.User;
import behzoddev.testproject.service.FeedbackService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// "Fikr va takliflar" — sayt bo'yicha (global) ochiq fikr-taklif taxtasi
// (foydalanuvchi so'rovi, 2026-09-06). DIQQAT: "/api/feedback/**" ATAYLAB
// SecurityConfig'dagi ".authenticated()" (ISTALGAN login qilgan
// foydalanuvchi — OWNER/ADMIN/USER) ro'yxatiga qo'shilgan — aks holda
// umumiy "/api/**" qoidasi (faqat OWNER/ADMIN) ostiga tushib, oddiy
// o'quvchilar (ROLE_USER) fikr yozolmay/javob berolmay qolardi
// (CourseForumController'da avval topilgan xato bilan bir xil sinf).
@RestController
@RequestMapping("/api/feedback")
@RequiredArgsConstructor
public class FeedbackController {

    private final FeedbackService feedbackService;

    @GetMapping("/threads")
    public FeedbackThreadsResponseDto listThreads(@AuthenticationPrincipal User user) {
        return feedbackService.listThreads(user);
    }

    @PostMapping("/threads")
    public FeedbackThreadDto createThread(@RequestBody FeedbackThreadSaveDto dto, @AuthenticationPrincipal User user) {
        return feedbackService.createThread(dto.feedbackText(), user);
    }

    @PatchMapping("/threads/{threadId}/status")
    public FeedbackThreadDto updateStatus(@PathVariable Long threadId,
                                           @RequestBody FeedbackStatusUpdateDto dto,
                                           @AuthenticationPrincipal User user) {
        return feedbackService.updateStatus(threadId, dto.status(), user);
    }

    @DeleteMapping("/threads/{threadId}")
    public void deleteThread(@PathVariable Long threadId, @AuthenticationPrincipal User user) {
        feedbackService.deleteThread(threadId, user);
    }

    @GetMapping("/threads/{threadId}/replies")
    public List<FeedbackReplyDto> listReplies(@PathVariable Long threadId) {
        return feedbackService.listReplies(threadId);
    }

    @PostMapping("/threads/{threadId}/replies")
    public FeedbackReplyDto createReply(@PathVariable Long threadId,
                                         @RequestBody FeedbackReplySaveDto dto,
                                         @AuthenticationPrincipal User user) {
        return feedbackService.createReply(threadId, dto.replyText(), user);
    }

    @DeleteMapping("/threads/{threadId}/replies/{replyId}")
    public void deleteReply(@PathVariable Long threadId, @PathVariable Long replyId,
                             @AuthenticationPrincipal User user) {
        feedbackService.deleteReply(replyId, user);
    }
}

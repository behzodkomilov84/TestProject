package behzoddev.testproject.controller.api;

import behzoddev.testproject.dto.course.ForumReplyDto;
import behzoddev.testproject.dto.course.ForumReplySaveDto;
import behzoddev.testproject.dto.course.ForumThreadDto;
import behzoddev.testproject.dto.course.ForumThreadSaveDto;
import behzoddev.testproject.dto.course.ForumThreadsResponseDto;
import behzoddev.testproject.entity.User;
import behzoddev.testproject.service.CourseForumService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// "Forum" — kurs foydalanuvchisi kurs muallifiga (yoki umuman kursga)
// savol berishi, XOHLAGAN boshqa foydalanuvchi esa shu savolga javob
// yozishi mumkin (foydalanuvchi so'rovi, 2026-09-06). Kirish huquqi
// (nashr qilingan kurs — hammaga, qoralama — faqat egasi/OWNER) BUTUNLAY
// CourseForumService#requireViewableCourse orqali tekshiriladi.
//
// DIQQAT: barcha yo'llar ATAYLAB "/api/courses/**" ostida (courseId
// path'da har doim bor, servis ichida threadId/replyId orqali ham
// yetarli bo'lsa ham) — SecurityConfig'da "/api/courses/**" ISTALGAN
// login qilgan foydalanuvchiga (OWNER/ADMIN/USER) ochiq, alohida
// "/api/forum/**" bo'lganda esa umumiy "/api/**" qoidasi (faqat
// OWNER/ADMIN) ostiga tushib, oddiy o'quvchilar (ROLE_USER) javob
// yozolmay qolgan bo'lardi (haqiqiy topilgan xato, kod yozish paytida).
@RestController
@RequiredArgsConstructor
public class CourseForumController {

    private final CourseForumService courseForumService;

    @GetMapping("/api/courses/{courseId}/forum/threads")
    public ForumThreadsResponseDto listThreads(@PathVariable Long courseId, @AuthenticationPrincipal User user) {
        return courseForumService.listThreads(courseId, user);
    }

    @PostMapping("/api/courses/{courseId}/forum/threads")
    public ForumThreadDto createThread(@PathVariable Long courseId,
                                        @RequestBody ForumThreadSaveDto dto,
                                        @AuthenticationPrincipal User user) {
        return courseForumService.createThread(courseId, dto.questionText(), user);
    }

    @DeleteMapping("/api/courses/{courseId}/forum/threads/{threadId}")
    public void deleteThread(@PathVariable Long courseId, @PathVariable Long threadId,
                              @AuthenticationPrincipal User user) {
        courseForumService.deleteThread(threadId, user);
    }

    @GetMapping("/api/courses/{courseId}/forum/threads/{threadId}/replies")
    public List<ForumReplyDto> listReplies(@PathVariable Long courseId, @PathVariable Long threadId,
                                            @AuthenticationPrincipal User user) {
        return courseForumService.listReplies(threadId, user);
    }

    @PostMapping("/api/courses/{courseId}/forum/threads/{threadId}/replies")
    public ForumReplyDto createReply(@PathVariable Long courseId, @PathVariable Long threadId,
                                      @RequestBody ForumReplySaveDto dto,
                                      @AuthenticationPrincipal User user) {
        return courseForumService.createReply(threadId, dto.replyText(), user);
    }

    @DeleteMapping("/api/courses/{courseId}/forum/threads/{threadId}/replies/{replyId}")
    public void deleteReply(@PathVariable Long courseId, @PathVariable Long threadId, @PathVariable Long replyId,
                             @AuthenticationPrincipal User user) {
        courseForumService.deleteReply(replyId, user);
    }
}

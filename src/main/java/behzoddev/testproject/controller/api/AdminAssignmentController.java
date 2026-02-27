package behzoddev.testproject.controller.api;

import behzoddev.testproject.dto.teacher.*;
import behzoddev.testproject.entity.User;
import behzoddev.testproject.service.AssignmentService;
import behzoddev.testproject.service.TeacherService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/assignments")
@RequiredArgsConstructor
@PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_OWNER')")
public class AdminAssignmentController {

    private final TeacherService teacherService;
    private final AssignmentService assignmentService;

    @GetMapping
    public ResponseEntity<List<AssignmentAdminRowDto>> getAll(@AuthenticationPrincipal User teacher) {
        return ResponseEntity.ok(teacherService.getAllAssignments(teacher));
    }

    @GetMapping("/{id}/details")
    public ResponseEntity<List<AssignmentStudentDetailDto>> getDetails(
            @PathVariable Long id) {
        return ResponseEntity.ok(teacherService.getAssignmentDetails(id));
    }

    @PutMapping("/{id}/extend")
    public void extend(@PathVariable Long id,
                       @RequestBody ExtendDueDto dto) {
        assignmentService.extendDue(id, dto.dueDate());
    }

    @PostMapping("/{id}/reassign")
    public void reassign(@PathVariable Long id) {
        assignmentService.reassign(id);
    }

    @PostMapping("/bulk-reassign")
    public void bulkReassign(@RequestBody List<Long> ids) {
        assignmentService.bulkReassign(ids);
    }

    @PutMapping("/bulk-extend")
    public void bulkExtend(@RequestBody BulkExtendDto dto) {
        assignmentService.bulkExtend(dto.ids(), dto.dueDate());
    }

    @GetMapping("/{id}/chat")
    public List<ChatMessageDto> getChat(@PathVariable Long id) {
        return assignmentService.getChat(id);
    }

    @PostMapping("/{assignmentId}/chat")
    public void sendChat(@PathVariable Long assignmentId,
                         @RequestBody Map<String,String> body,
                         @AuthenticationPrincipal User sender) {
        assignmentService.sendMessage(assignmentId, sender.getId(), body.get("text"));
    }

    @DeleteMapping("/bulk-delete")
    public ResponseEntity<Void> bulkDelete(
            @RequestBody List<Long> ids,
            @AuthenticationPrincipal User teacher) {

        teacherService.bulkDeleteAssignments(ids, teacher);
        return ResponseEntity.noContent().build();
    }
}

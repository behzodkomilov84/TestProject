package behzoddev.testproject.controller.api;

import behzoddev.testproject.dto.GroupInviteDto;
import behzoddev.testproject.dto.ResponseAssignmentsDto;
import behzoddev.testproject.dto.ResponseGroupMembershipDto;
import behzoddev.testproject.entity.User;
import behzoddev.testproject.service.StudentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/*
Student API
GET  /api/student/invites
POST /api/student/invite/{id}/accept
POST /api/student/invite/{id}/reject
GET  /api/student/tasks
*/
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/student")
@PreAuthorize("hasAnyAuthority('ROLE_USER','ROLE_OWNER')")
public class StudentController {
    private final StudentService studentService;

    @GetMapping("/invites")
    public List<GroupInviteDto> getInvites(@AuthenticationPrincipal User pupil) {

        return studentService.getInvites(pupil);
    }

    @PostMapping("/invite/{id}/accept")
    public void acceptInvitation(
            @PathVariable Long id,
            @AuthenticationPrincipal User pupil) {

        studentService.acceptInvite(id, pupil);
    }

    @PostMapping("/invite/{id}/reject")
    public void rejectInvite(@PathVariable Long id, @AuthenticationPrincipal User pupil) {
        studentService.rejectInvite(id, pupil);
    }

    @GetMapping("/memberships")
    public ResponseEntity<List<ResponseGroupMembershipDto>> getMemberships(@AuthenticationPrincipal User student) {
        return ResponseEntity.ok(studentService.getMemberships(student.getUsername()));
    }

    @GetMapping("/tasks")
    public ResponseEntity<List<ResponseAssignmentsDto>> getTasks(@AuthenticationPrincipal User pupil){
        return ResponseEntity.ok(studentService.getTasks(pupil));
    }

    @GetMapping("/debug")
    public Object debug(@AuthenticationPrincipal User u) {
        return u.getAuthorities();
    }

}

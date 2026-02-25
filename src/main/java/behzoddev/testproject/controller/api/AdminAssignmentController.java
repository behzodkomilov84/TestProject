package behzoddev.testproject.controller.api;

import behzoddev.testproject.dto.teacher.AssignmentAdminRowDto;
import behzoddev.testproject.dto.teacher.AssignmentStudentDetailDto;
import behzoddev.testproject.service.TeacherService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/assignments")
@RequiredArgsConstructor
@PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_OWNER')")
public class AdminAssignmentController {

    private final TeacherService teacherService;

    @GetMapping
    public ResponseEntity<List<AssignmentAdminRowDto>> getAll() {
        return ResponseEntity.ok(teacherService.getAllAssignments());
    }

    @GetMapping("/{id}/details")
    public ResponseEntity<List<AssignmentStudentDetailDto>> getDetails(
            @PathVariable Long id) {
        return ResponseEntity.ok(teacherService.getAssignmentDetails(id));
    }
}

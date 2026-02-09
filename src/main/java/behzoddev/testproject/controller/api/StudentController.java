package behzoddev.testproject.controller.api;

import behzoddev.testproject.entity.User;
import behzoddev.testproject.service.StudentService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

/*
Pupil API
GET  /pupil/invites
POST /pupil/invite/accept
GET  /pupil/assignments
POST /pupil/attempt
*/
@Controller
@RequiredArgsConstructor
public class StudentController {
    private final StudentService studentService;

    @PostMapping("/invite/{id}/accept")
    public void accept(
            @PathVariable Long id,
            @AuthenticationPrincipal User pupil) {

        studentService.acceptInvite(id, pupil);
    }
}

package behzoddev.testproject.controller.api;

import behzoddev.testproject.dto.teacher.ChatMessageDto;
import behzoddev.testproject.entity.User;
import behzoddev.testproject.service.AssignmentService;
import lombok.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/assignments")
@RequiredArgsConstructor
public class AssignmentChatController {

    private final AssignmentService assignmentService;

    @GetMapping("/{id}/chat")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_OWNER','ROLE_USER')")
    public List<ChatMessageDto> getChat(@PathVariable Long id,
                                        @AuthenticationPrincipal User user) {
        return assignmentService.getChatForUser(id, user);
    }

    @PostMapping("/{id}/chat")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_OWNER','ROLE_USER')")
    public void sendChat(@PathVariable Long id,
                         @RequestBody Map<String,String> body,
                         @AuthenticationPrincipal User sender) {
        assignmentService.sendMessage(id, sender.getId(), body.get("text"));
    }
}
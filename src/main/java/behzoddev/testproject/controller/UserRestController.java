package behzoddev.testproject.controller;

import behzoddev.testproject.dao.RoleRepository;
import behzoddev.testproject.dao.UserRepository;
import behzoddev.testproject.dto.ChangeRoleDto;
import behzoddev.testproject.dto.UserDto;
import behzoddev.testproject.service.UserServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class UserRestController {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserServiceImpl userServiceImpl;

    @GetMapping("/api/users")
    @PreAuthorize("hasAuthority('ROLE_OWNER')")
    public List<UserDto> getAllUsers() {
        return userRepository.findAll()
                .stream()
                .map(u -> new UserDto(
                        u.getId(),
                        u.getUsername(),
                        u.getRole().getRoleName()
                ))
                .toList();
    }

    @DeleteMapping("/api/users/{id}")
    @PreAuthorize("hasAuthority('ROLE_OWNER')")
    public ResponseEntity<?> deleteUser(@PathVariable Long id,
                                              Authentication authentication) {

        try {
            UserDto deletedUser = userServiceImpl.deleteUser(id, authentication);
            return ResponseEntity.ok(deletedUser);
        }catch (AccessDeniedException e) {
            // Если пытаются удалить свою роль
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", e.getMessage()));
        }

    }

    @PatchMapping("/api/users/change-role")
    @PreAuthorize("hasAuthority('ROLE_OWNER')")
    public ResponseEntity<?> changeRole(
            @RequestBody ChangeRoleDto changeRoleRequest,
            Authentication authentication
    ) {
        try {
            ChangeRoleDto result = userServiceImpl.changeUserRole(
                    changeRoleRequest.userId(),
                    changeRoleRequest.newRole(),
                    authentication
            );
            return ResponseEntity.ok(result);
        } catch (AccessDeniedException e) {
            // Если пытаются изменить свою роль
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", e.getMessage()));
        }
    }

}




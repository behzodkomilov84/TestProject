
package behzoddev.testproject.controller;

import behzoddev.testproject.dao.RoleRepository;
import behzoddev.testproject.dao.UserRepository;
import behzoddev.testproject.dto.RoleChangeRequestDto;
import behzoddev.testproject.entity.Role;
import behzoddev.testproject.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserManagementController {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;

    @PreAuthorize("hasAuthority('ROLE_OWNER')")
    @PutMapping("/{id}/role")
    public void changeRole(
            @PathVariable Long id,
            @RequestBody RoleChangeRequestDto roleChangeRequestDto
    ) {
        User user = userRepository.findById(id)
                .orElseThrow();

        Role role = roleRepository.findByRoleName(roleChangeRequestDto.role())
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND, "Role not found"
                        ));

        // ❌ защита OWNER
        if ("ROLE_OWNER".equals(user.getRole().getRoleName())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Cannot change OWNER role"
            );
        }

        user.setRole(role);
        userRepository.save(user);
    }
}

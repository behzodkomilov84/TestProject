package behzoddev.testproject.controller.api;

import behzoddev.testproject.dao.UserRepository;
import behzoddev.testproject.dto.audit.RoleAuditLogDto;
import behzoddev.testproject.dto.user.ChangeRoleDto;
import behzoddev.testproject.dto.user.UpdateUserDto;
import behzoddev.testproject.dto.user.UserDto;
import behzoddev.testproject.entity.Role;
import behzoddev.testproject.entity.User;
import behzoddev.testproject.service.RoleAuditService;
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
    private final UserServiceImpl userServiceImpl;
    private final RoleAuditService roleAuditService;

    @GetMapping("/api/users")
    @PreAuthorize("hasAuthority('ROLE_OWNER')")
    public List<UserDto> getAllUsers() {
        return userRepository.findAll()
                .stream()
                .map(this::toDto)
                .toList();
    }

    // "Foydalanuvchilar" sahifasida ma'lumotlarni qo'lda tahrirlash uchun
    // (foydalanuvchi so'rovi, 2026-09-07: "sahifasiga edit ni qo'shish
    // kerak").
    @PutMapping("/api/users/{id}")
    @PreAuthorize("hasAuthority('ROLE_OWNER')")
    public ResponseEntity<?> updateUser(@PathVariable Long id, @RequestBody UpdateUserDto dto) {
        try {
            User updated = userServiceImpl.adminUpdateUser(id, dto);
            return ResponseEntity.ok(toDto(updated));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    private UserDto toDto(User u) {
        return UserDto.builder()
                .id(u.getId())
                .username(u.getUsername())
                .roles(u.getRoles().stream().map(Role::getRoleName).sorted().toList())
                .locked(!u.isAccountNonLocked())
                .email(u.getEmail())
                .phoneNumber(u.getPhoneNumber())
                .telegramId(u.getTelegramId())
                .telegramUsername(u.getTelegramUsername())
                .googleId(u.getGoogleId())
                .facebookId(u.getFacebookId())
                .firstName(u.getFirstName())
                .lastName(u.getLastName())
                .workplace(u.getWorkplace())
                .jobTitle(u.getPosition())
                .avatarUrl(u.getAvatarUrl())
                .build();
    }

    // Rol o'zgarishlari audit tarixi — kim, qachon, kimga qanday rol
    // berdi/olib tashladi (checkbox orqali qo'lda va Subscription orqali
    // avtomatik o'zgarishlarning barchasi).
    @GetMapping("/api/users/roles-audit")
    @PreAuthorize("hasAuthority('ROLE_OWNER')")
    public List<RoleAuditLogDto> rolesAudit() {
        return roleAuditService.listRecent();
    }

    // Brute-force himoyasi tufayli bloklangan hisobni OWNER qo'lda ochadi.
    @PostMapping("/api/users/{id}/unlock")
    @PreAuthorize("hasAuthority('ROLE_OWNER')")
    public ResponseEntity<?> unlockUser(@PathVariable Long id) {
        userServiceImpl.unlockUser(id);
        return ResponseEntity.ok(Map.of("id", id, "locked", false));
    }

    @DeleteMapping("/api/users/{id}")
    @PreAuthorize("hasAuthority('ROLE_OWNER')")
    public ResponseEntity<?> deleteUser(@PathVariable Long id,
                                        Authentication authentication) {

        try {
            UserDto deletedUser = userServiceImpl.deleteUser(id, authentication);
            return ResponseEntity.ok(deletedUser);
        } catch (AccessDeniedException e) {
            // Если пытаются удалить свою роль
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", e.getMessage()));
        }

    }

    // Foydalanuvchiga qo'shimcha rol beradi (masalan, o'quvchini o'qituvchi
    // ham qilib belgilash). Mavjud rollari saqlanib qoladi — dual-role.
    @PostMapping("/api/users/{id}/roles/{roleName}")
    @PreAuthorize("hasAuthority('ROLE_OWNER')")
    public ResponseEntity<?> addRole(
            @PathVariable Long id,
            @PathVariable String roleName,
            Authentication authentication
    ) {
        try {
            ChangeRoleDto result = userServiceImpl.addRole(id, roleName, authentication);
            return ResponseEntity.ok(result);
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // Foydalanuvchidan bitta rolni olib tashlaydi (kamida bitta rol qolishi shart).
    @DeleteMapping("/api/users/{id}/roles/{roleName}")
    @PreAuthorize("hasAuthority('ROLE_OWNER')")
    public ResponseEntity<?> removeRole(
            @PathVariable Long id,
            @PathVariable String roleName,
            Authentication authentication
    ) {
        try {
            ChangeRoleDto result = userServiceImpl.removeRole(id, roleName, authentication);
            return ResponseEntity.ok(result);
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

}




package behzoddev.testproject.dto;

import lombok.Builder;

@Builder
public record ChangeRoleDto(Long userId, String newRole) {
}

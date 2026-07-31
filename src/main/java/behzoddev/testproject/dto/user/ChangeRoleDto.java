package behzoddev.testproject.dto.user;

import lombok.Builder;

import java.util.List;

@Builder
public record ChangeRoleDto(Long userId, List<String> roles) {
}

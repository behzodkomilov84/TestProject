package behzoddev.testproject.dto.user;

import lombok.Builder;

@Builder
public record UserDto(Long id, String username, String role) {
}

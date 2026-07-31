package behzoddev.testproject.dto.user;

import lombok.Builder;

import java.util.List;

@Builder
public record UserDto(Long id, String username, List<String> roles, boolean locked) {
}

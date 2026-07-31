package behzoddev.testproject.dto.profile;

import java.util.List;

public record ProfileDto(
        Long id,
        String username,
        String email,
        List<String> roles
) {}

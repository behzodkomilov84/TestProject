package behzoddev.testproject.dto.teacher;

import lombok.Builder;

import java.util.List;

@Builder
public record AssignResultDto(

        List<Long> assigned,
        List<Long> missing,
        List<Long> alreadyAssigned

) {}

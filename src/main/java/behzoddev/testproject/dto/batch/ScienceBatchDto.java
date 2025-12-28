package behzoddev.testproject.dto.batch;

import lombok.Builder;

import java.util.List;
@Builder
public record ScienceBatchDto(
        List<ScienceCreateDto> newItems,
        List<ScienceUpdateDto> updated,
        List<Long> deletedIds
) {
}

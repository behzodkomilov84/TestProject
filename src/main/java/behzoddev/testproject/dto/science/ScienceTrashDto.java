package behzoddev.testproject.dto.science;

import java.time.LocalDateTime;

// "O'chirilganlar savati" — soft-delete qilingan bitta fan (ScienceService.getDeletedSciences).
public record ScienceTrashDto(Long id, String name, LocalDateTime deletedAt) {
}

package behzoddev.testproject.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class PageResponseDto<T> {
    private List<T> content;
    private int totalPages;
    private int number; // текущая страница
    private boolean first;
    private boolean last;
}

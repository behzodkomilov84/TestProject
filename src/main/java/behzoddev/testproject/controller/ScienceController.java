package behzoddev.testproject.controller;

import behzoddev.testproject.dto.ScienceDto;
import behzoddev.testproject.entity.Science;
import behzoddev.testproject.service.ScienceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

@RestController
@RequiredArgsConstructor
@RequestMapping("/sciences")
public class ScienceController {

    private final ScienceService scienceService;

    @GetMapping
    public ResponseEntity<Set<ScienceDto>> getSciences() {
        Set<ScienceDto> sciences = scienceService.getAllSciencesDto();

        return ResponseEntity.ok(sciences);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ScienceDto> getScience(@PathVariable Long id) {
        ScienceDto scienceDto = scienceService.getScienceById(id).orElseThrow();
        return ResponseEntity.ok(scienceDto);
    }
}

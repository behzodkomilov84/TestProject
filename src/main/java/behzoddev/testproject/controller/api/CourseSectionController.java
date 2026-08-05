package behzoddev.testproject.controller.api;

import behzoddev.testproject.dto.course.CourseSectionContentDto;
import behzoddev.testproject.dto.course.CourseSectionSaveDto;
import behzoddev.testproject.dto.course.CourseSectionSummaryDto;
import behzoddev.testproject.entity.User;
import behzoddev.testproject.service.CourseService;
import behzoddev.testproject.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/courses/{courseId}/sections")
@RequiredArgsConstructor
public class CourseSectionController {

    private final CourseService courseService;
    private final FileStorageService fileStorageService;

    // Bo'lim to'liq kontenti — faqat ochilgan (unlock qilingan) bo'lsa qaytariladi,
    // aks holda CourseService AccessDeniedException otadi (GlobalRestExceptionHandler -> 403).
    @GetMapping("/{sectionId}")
    public CourseSectionContentDto getContent(
            @PathVariable Long courseId,
            @PathVariable Long sectionId,
            @AuthenticationPrincipal User user
    ) {
        return courseService.getSectionContent(courseId, sectionId, user);
    }

    // TEXT bo'lim ochilganda (sahifa yuklanganda) yoki VIDEO oxirigacha
    // ko'rilganda frontend shu endpoint'ni chaqiradi — keyingi bo'limni ochadi.
    @PostMapping("/{sectionId}/complete")
    public void markCompleted(
            @PathVariable Long courseId,
            @PathVariable Long sectionId,
            @AuthenticationPrincipal User user
    ) {
        courseService.markSectionCompleted(courseId, sectionId, user);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ROLE_OWNER')")
    public CourseSectionSummaryDto add(
            @PathVariable Long courseId,
            @RequestBody CourseSectionSaveDto dto
    ) {
        return courseService.addSection(courseId, dto);
    }

    @PutMapping("/{sectionId}")
    @PreAuthorize("hasAuthority('ROLE_OWNER')")
    public void update(
            @PathVariable Long courseId,
            @PathVariable Long sectionId,
            @RequestBody CourseSectionSaveDto dto
    ) {
        courseService.updateSection(courseId, sectionId, dto);
    }

    @DeleteMapping("/{sectionId}")
    @PreAuthorize("hasAuthority('ROLE_OWNER')")
    public void delete(@PathVariable Long courseId, @PathVariable Long sectionId) {
        courseService.deleteSection(courseId, sectionId);
    }

    // Bo'lim uchun video (UPLOAD manba) yuklash — qaytgan URL
    // CourseSectionSaveDto.videoUrl'ga qo'yiladi (videoSourceType=UPLOAD bilan).
    @PostMapping("/upload-video")
    @PreAuthorize("hasAuthority('ROLE_OWNER')")
    public Map<String, String> uploadVideo(
            @PathVariable Long courseId,
            @RequestParam("video") MultipartFile video
    ) {
        return Map.of("url", fileStorageService.storeCourseVideo(video));
    }
}

package behzoddev.testproject.controller.api;

import behzoddev.testproject.dao.QuestionRepository;
import behzoddev.testproject.dto.*;
import behzoddev.testproject.entity.User;
import behzoddev.testproject.mapper.QuestionMapperImpl;
import behzoddev.testproject.service.QuestionService;
import behzoddev.testproject.service.ScienceService;
import behzoddev.testproject.service.TeacherService;
import behzoddev.testproject.service.TopicService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/*
Teacher API
POST /teacher/group
POST /teacher/group/{id}/invite
POST /teacher/questionset
POST /teacher/assign/group
GET  /teacher/results
*/
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/teacher")
@PreAuthorize("hasAnyRole('ROLE_ADMIN','ROLE_OWNER')")
public class TeacherController {

    private final TeacherService teacherService;
    private final ScienceService scienceService;
    private final TopicService topicService;
    private final QuestionRepository questionRepository;
    private final QuestionMapperImpl questionMapper;
    private final QuestionService questionService;

    @PostMapping("/create-group")
    public ResponseEntity<Void> createTeacherGroup(
            @Valid @RequestBody CreateGroupDto newTeacherGroup,
            @AuthenticationPrincipal User teacher) {

        teacherService.createGroup(teacher, newTeacherGroup.name());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/get-groups")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_OWNER')")
    public ResponseEntity<List<ResponseForGetTeacherGroupDto>> getTeacherGroups(
            @AuthenticationPrincipal User teacher
    ) {
        List<ResponseForGetTeacherGroupDto> groupDtoList =
                teacherService.getTeacherGroupsByUser(teacher);

        return ResponseEntity.ok().body(groupDtoList);
    }

    @DeleteMapping("/groups/{id}")
    public void delete(@PathVariable Long id) {
        teacherService.deleteGroup(id);
    }

    @GetMapping("/groups/select")
    public List<GroupDto> getGroupsForSelect(
            @AuthenticationPrincipal User user
    ) {
        return teacherService.getGroupsForSelect(user);
    }

    @PostMapping("/questionset")
    public ResponseEntity<?> createQuestionSet(@RequestBody @Valid CreateQuestionSetDto dto,
                                               @AuthenticationPrincipal User teacher) {
        QuestionSetResponseDto createdQuestionSet = teacherService.createQuestionSet(teacher, dto);

        return ResponseEntity.ok().body(createdQuestionSet + " muvaffaqiyatli yaratildi.");
    }

    @GetMapping("/questionsets")
    public List<QuestionSetDto> sets(@AuthenticationPrincipal User teacher) {
        return teacherService.getSets(teacher);
    }




    @PostMapping("/assign/group")
    public void assign(@RequestBody AssignToGroupDto dto) {
        teacherService.assignToGroup(dto);
    }

    @PatchMapping("/groups/{groupId}")
    public ResponseEntity<Void> updateGroup(
            @PathVariable Long groupId,
            @RequestBody UpdateTeacherGroupDto dto,
            @AuthenticationPrincipal User teacher
    ) {
        teacherService.updateGroup(groupId, dto, teacher);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/sciences")
    public List<ScienceIdAndNameDto> getSciences() {

        return scienceService.getSciences();
    }

    //Topics of selected science
    @GetMapping("/topics/{scienceId}")
    public List<TopicWithQuestionCountDto> getTopics(
            @PathVariable Long scienceId) {

        return topicService.getTopicsWithQuestionCount(scienceId);
    }

    // Получение вопросов по теме
    @GetMapping("/questions/topic/{topicId}")
    public List<ResponseQuestionTextDto> getQuestionsByTopic(@PathVariable Long topicId) {

        return questionService.getQuestionsByTopic(topicId);
    }

}

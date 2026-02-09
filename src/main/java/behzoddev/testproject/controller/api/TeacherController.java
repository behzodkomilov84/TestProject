package behzoddev.testproject.controller.api;

import behzoddev.testproject.dto.*;
import behzoddev.testproject.entity.User;
import behzoddev.testproject.service.*;
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
    private final QuestionService questionService;
    private final StudentService studentService;

    @PostMapping("/create-group")
    public ResponseEntity<Void> createTeacherGroup(
            @Valid @RequestBody CreateGroupDto newTeacherGroup,
            @AuthenticationPrincipal User teacher) {

        teacherService.createGroup(teacher, newTeacherGroup.name());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/get-groups")
    public ResponseEntity<List<ResponseForGetTeacherGroupDto>> getTeacherGroups(
            @AuthenticationPrincipal User teacher
    ) {
        List<ResponseForGetTeacherGroupDto> groupDtoList =
                teacherService.getTeacherGroupsByUser(teacher);

        return ResponseEntity.ok().body(groupDtoList);
    }

    @DeleteMapping("/groups/{id}")
    public void deleteGroup(@PathVariable Long id) {
        teacherService.deleteGroup(id);
    }

    @GetMapping("/groups/select")
    public List<GroupDto> getGroupsForSelect(
            @AuthenticationPrincipal User user
    ) {
        return teacherService.getGroupsForSelect(user);
    }

    @PostMapping("/questionset")
    public ResponseEntity<QuestionSetResponseDto> createQuestionSet(@RequestBody @Valid CreateQuestionSetDto dto,
                                                                    @AuthenticationPrincipal User teacher) {
        QuestionSetResponseDto createdQuestionSet = teacherService.createQuestionSet(teacher, dto);

        return ResponseEntity.ok().body(createdQuestionSet);
    }

    @GetMapping("/questionsets")
    public List<QuestionSetDto> getQuestionSetsForSelect(@AuthenticationPrincipal User teacher) {
        return teacherService.getSets(teacher);
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
    public List<ScienceIdAndNameDto> getSciencesForSelect() {

        return scienceService.getSciences();
    }

    //Topics of selected science
    @GetMapping("/topics/{scienceId}")
    public List<TopicWithQuestionCountDto> getTopicsForSelect(
            @PathVariable Long scienceId) {

        return topicService.getTopicsWithQuestionCount(scienceId);
    }

    // Получение вопросов по теме
    @GetMapping("/questions/topic/{topicId}")
    public List<ResponseQuestionTextDto> getQuestionsByTopicForSelect(@PathVariable Long topicId) {

        return questionService.getQuestionsByTopic(topicId);
    }

    @PostMapping("/group/{groupId}/invite")
    public void invite(
            @PathVariable Long groupId,
            @RequestBody InviteDto dto) {

        teacherService.inviteStudent(groupId, dto.pupilId());
    }

    //список студентов, уже в группе (правый сайдбар).
    @GetMapping("/group/{id}/students")
    public List<GroupStudentRowDto> getStudents(@PathVariable Long id) {

        return teacherService.getGroupStudents(id);
    }

    //список всех студентов/users для invite modal.
    @GetMapping("/group/students")
    public List<GroupStudentDto> getStudentsForGroups() {

        return teacherService.getAllStudentsForGroups();
    }

}

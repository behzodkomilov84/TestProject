package behzoddev.testproject.controller.api;

import behzoddev.testproject.dto.science.ScienceIdAndNameDto;
import behzoddev.testproject.dto.teacher.*;
import behzoddev.testproject.dto.topic.TopicWithQuestionCountDto;
import behzoddev.testproject.entity.User;
import behzoddev.testproject.service.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

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
// ESLATMA: bu yerda avval "hasAnyRole('ROLE_ADMIN','ROLE_OWNER')" edi — bu XATO
// edi, chunki hasAnyRole avtomatik "ROLE_" prefiksini qo'shadi va haqiqatda
// "ROLE_ROLE_ADMIN"/"ROLE_ROLE_OWNER" ni tekshirar edi (hech qachon mos kelmaydi),
// natijada BUTUN /api/teacher/** doim 403 qaytarardi. hasAnyAuthority to'g'ri variant.
@PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_OWNER')")
public class TeacherController {

    private final TeacherService teacherService;
    private final ScienceService scienceService;
    private final TopicService topicService;
    private final QuestionService questionService;

    @GetMapping("/debug")
    public Object debug(@AuthenticationPrincipal User u) {
        return u.getAuthorities();
    }

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

    // "Barcha savol to'plamlari" — FAQAT ROLE_OWNER (sinf darajasidagi
    // "ROLE_ADMIN yoki ROLE_OWNER"dan bu yerda ATAYLAB qattiqroq qilib
    // qo'yilgan) — "/questionsets/{id}" bilan chalkashmasligi uchun aniq
    // literal yo'l ("all"), Spring bunday holatlarda literal segmentni
    // {id} path-variable'dan avtomatik ustun qo'yadi.
    @GetMapping("/questionsets/all")
    @PreAuthorize("hasAuthority('ROLE_OWNER')")
    public List<QuestionSetAdminRowDto> getAllQuestionSetsForOwner() {
        return teacherService.getAllSetsForOwner();
    }

    // "✏️" — savollar paketi nomini o'zgartirish ("Savollar to'plami" sahifasi).
    @PatchMapping("/questionsets/{id}")
    public ResponseEntity<Void> renameQuestionSet(@PathVariable Long id, @RequestBody Map<String, String> body,
                                                   @AuthenticationPrincipal User teacher) {
        teacherService.renameQuestionSet(id, body.get("name"), teacher);
        return ResponseEntity.ok().build();
    }

    // "📂 Tarkibini tahrirlash" — to'plamni TO'LIQ (nomi + har bir savol
    // matni bilan) qaytaradi, qurilmaga qayta yuklash uchun.
    @GetMapping("/questionsets/{id}")
    public QuestionSetDetailDto getQuestionSetDetail(@PathVariable Long id, @AuthenticationPrincipal User teacher) {
        return teacherService.getSetDetail(id, teacher);
    }

    // "📂 Tarkibini tahrirlash" oynasidan "Yangilash" bosilganda — nomi VA
    // savollar ro'yxati (butunlay ALMASHTIRILADI) saqlanadi.
    @PutMapping("/questionsets/{id}")
    public ResponseEntity<QuestionSetResponseDto> updateQuestionSet(@PathVariable Long id, @RequestBody @Valid CreateQuestionSetDto dto,
                                                                      @AuthenticationPrincipal User teacher) {
        return ResponseEntity.ok(teacherService.updateQuestionSet(id, teacher, dto));
    }

    // "🗑️" — savollar paketini o'chirish (allaqachon topshiriqda
    // ishlatilgan bo'lsa TeacherService bloklaydi).
    @DeleteMapping("/questionsets/{id}")
    public ResponseEntity<Void> deleteQuestionSet(@PathVariable Long id, @AuthenticationPrincipal User teacher) {
        teacherService.deleteQuestionSet(id, teacher);
        return ResponseEntity.ok().build();
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

    // "🎲 Avtomatik tanlash" — belgilangan mavzular orasidan, har biriga
    // TENG bo'lib, jami "totalCount" ta savolni tasodifiy tanlab beradi
    // ("Savollar to'plami" sahifasi).
    @PostMapping("/questions/auto-select")
    public List<ResponseQuestionTextDto> autoSelectQuestions(@RequestBody AutoSelectQuestionsDto dto) {
        return teacherService.autoSelectQuestions(dto.topicIds(), dto.totalCount());
    }

    @PostMapping("/group/{groupId}/invite")
    public ResponseEntity<Void> invite(
            @PathVariable Long groupId,
            @RequestBody InviteDto dto) {

        teacherService.inviteStudent(groupId, dto.pupilId());
        return ResponseEntity.ok().build();
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

    @PostMapping("/assign")
    public ResponseEntity<AssignResultDto> assign(@RequestBody AssignDto assignment,
                                                  @AuthenticationPrincipal User teacher) {

        AssignResultDto assignResultDto =
                teacherService.assignQuestionSetToStudents(teacher, assignment);
               return ResponseEntity.ok().body(assignResultDto);
    }


}

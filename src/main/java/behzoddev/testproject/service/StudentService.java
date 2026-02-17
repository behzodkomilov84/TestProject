package behzoddev.testproject.service;

import behzoddev.testproject.dao.*;
import behzoddev.testproject.dto.student.GroupInviteDto;
import behzoddev.testproject.dto.student.ResponseAssignmentsDto;
import behzoddev.testproject.dto.student.ResponseGroupMembershipDto;
import behzoddev.testproject.dto.student.ResponseQuestionSetDto;
import behzoddev.testproject.entity.GroupInvite;
import behzoddev.testproject.entity.GroupMember;
import behzoddev.testproject.entity.QuestionSet;
import behzoddev.testproject.entity.User;
import behzoddev.testproject.entity.enums.InviteStatus;
import behzoddev.testproject.mapper.GroupInviteMapper;
import behzoddev.testproject.mapper.GroupMemberMapper;
import behzoddev.testproject.mapper.QuestionSetMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StudentService {

    private final GroupInviteRepository groupInviteRepository;
    private final GroupInviteMapper groupInviteMapper;
    private final AssignmentRepository assignmentRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final UserRepository userRepository;
    private final GroupMemberMapper groupMemberMapper;
    private final QuestionSetRepository questionSetRepository;
    private final QuestionSetMapper questionSetMapper;

    @Transactional
    public List<GroupInviteDto> getInvites(User pupil) {
        return groupInviteRepository.findByPupilWithGroup(pupil)
                .stream()
                .map(groupInviteMapper::mapGroupInviteToGroupInviteDto)
                .toList();
    }

    @Transactional
    public void acceptInvite(Long inviteId, User student) {

        GroupInvite invite =
                groupInviteRepository.findById(inviteId)
                        .orElseThrow(() -> new RuntimeException("Invite not found"));

        if (!invite.getPupil().getId()
                .equals(student.getId()))
            throw new RuntimeException("Not your invite");

        if (invite.getStatus().equals(InviteStatus.REJECTED)) {
            throw new RuntimeException("Rad etilgan taklifni qabul qilib bo'lmaydi. \nTaklif qayta jo'natilishi kerak.");
        }

        if (invite.getStatus().equals(InviteStatus.ACCEPTED)) {
            throw new RuntimeException("Bu taklif allaqachon qabul qilingan");
        }

        // 1️⃣ обновляем статус приглашения
        invite.setStatus(InviteStatus.ACCEPTED);

        // 2️⃣ создаём membership
        GroupMember member = GroupMember.builder()
                .group(invite.getGroup())
                .pupil(student)
                .joinedAt(LocalDateTime.now())
                .build();

        groupMemberRepository.save(member);
    }

    @Transactional
    public void rejectInvite(Long inviteId, User pupil) {

        GroupInvite invite = groupInviteRepository.findById(inviteId)
                .orElseThrow(() -> new RuntimeException("Bunday taklif topilmadi"));

        if (!invite.getPupil().getId().equals(pupil.getId()))
            throw new RuntimeException("Not allowed");

        if (invite.getStatus().equals(InviteStatus.REJECTED)) {
            throw new RuntimeException("Bu taklif allaqachon rad etilgan.");
        }
        invite.setStatus(InviteStatus.REJECTED);
        groupInviteRepository.save(invite);

        //Gruppa a'zoligidan ham chiqarvorish kerak.
        if (!groupMemberRepository.existsByGroupIdAndPupil(invite.getGroup().getId(), pupil)) {
            throw new RuntimeException("Bu o'quvchi gruppada yo'q");
        }

        groupMemberRepository
                .deleteByGroupIdAndPupil(invite.getGroup().getId(), pupil);
    }

    @Transactional(readOnly = true)
    public List<ResponseGroupMembershipDto> getMemberships(String username) {
        User student = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Foydalanuvchi topilmadi"));

        List<GroupMember> membershipByUser = groupMemberRepository
                .findByUser(student);

        if (membershipByUser.isEmpty()) throw new RuntimeException("A'zolik topilmadi");

        return membershipByUser
                .stream()
                .map(groupMemberMapper::mapGroupMemberToResponseGroupMembershipDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ResponseAssignmentsDto> getTasks(User pupil) {

        return assignmentRepository.findAllByPupil(pupil)
                .stream()
                .map(a ->
                        ResponseAssignmentsDto.builder()
                                .id(a.getId())
                                .questionSetId(a.getQuestionSet().getId())
                                .questionSetName(a.getQuestionSet().getName())
                                .groupId(a.getGroup().getId())
                                .groupName(a.getGroup().getName())
                                .assignerId(a.getAssignedBy().getId())
                                .assignerName(a.getAssignedBy().getUsername())
                                .assignedAt(a.getAssignedAt())
                                .dueDate(a.getDueDate())
                                .build()
                )
                .toList();
    }

    @Transactional(readOnly = true)
    public ResponseQuestionSetDto getQuestionSet(Long id) {

        QuestionSet set = questionSetRepository.fetchFullById(id)
                .orElseThrow(() ->
                        new RuntimeException("Question set not found: " + id)
                );

        return questionSetMapper.mapQuestionSetToResponseQuestionSetDto(set);
    }


}

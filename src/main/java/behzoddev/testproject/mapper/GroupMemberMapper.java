package behzoddev.testproject.mapper;

import behzoddev.testproject.dto.student.ResponseGroupMembershipDto;
import behzoddev.testproject.entity.GroupMember;
import behzoddev.testproject.entity.enums.GroupRole;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface GroupMemberMapper {

    @Mapping(target = "id", source = "group.id")
    @Mapping(target = "groupName", source = "group.name")
    @Mapping(target = "role", expression = "java(mapRole(gm))")
    ResponseGroupMembershipDto mapGroupMemberToResponseGroupMembershipDto(GroupMember gm);

    default String mapRole(GroupMember gm) {

        if (gm == null || gm.getPupil() == null) {
            return GroupRole.MEMBER.name();
        }

        // Dual-role: bitta odam ham ROLE_USER, ham ROLE_ADMIN bo'lishi mumkin,
        // shuning uchun "bittagina rol" emas, balki ROLE_ADMIN/ROLE_OWNER
        // rolining bor-yo'qligini tekshiramiz.
        if (gm.getPupil().hasRole("ROLE_ADMIN") || gm.getPupil().hasRole("ROLE_OWNER")) {
            return GroupRole.TEACHER.name();
        }

        return GroupRole.MEMBER.name();
    }
}

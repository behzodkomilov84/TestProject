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

        if (gm == null || gm.getPupil() == null || gm.getPupil().getRole() == null) {
            return GroupRole.MEMBER.name();
        }

        String authority = gm.getPupil().getRole().getAuthority();

        if ("ROLE_ADMIN".equalsIgnoreCase(authority)) {
            return GroupRole.TEACHER.name();
        }

        return GroupRole.MEMBER.name();
    }
}

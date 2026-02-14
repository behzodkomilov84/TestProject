package behzoddev.testproject.mapper;

import behzoddev.testproject.dto.student.GroupInviteDto;
import behzoddev.testproject.entity.GroupInvite;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface GroupInviteMapper {

    @Mapping(source = "group.name", target = "groupName")
    @Mapping(source = "status", target = "status")
    GroupInviteDto mapGroupInviteToGroupInviteDto(GroupInvite groupInvite);
}

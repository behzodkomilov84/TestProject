package behzoddev.testproject.mapper;

import behzoddev.testproject.dto.GroupDto;
import behzoddev.testproject.dto.ResponseForGetTeacherGroupDto;
import behzoddev.testproject.entity.TeacherGroup;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface TeacherGroupMapper {

    @Mapping(source = "id", target = "teacherGroupId")
    @Mapping(source = "name", target = "groupName")
    ResponseForGetTeacherGroupDto mapTeacherGroupToResponseForGetTeacherGroupDto(TeacherGroup group);

    List<ResponseForGetTeacherGroupDto> mapTeacherGroupListToResponseForGetTeacherGroupDtoList(List<TeacherGroup> teacherGroupsByUserId);

    GroupDto mapTeacherGroupToGroupDto(TeacherGroup teacherGroup);
}

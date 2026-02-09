package behzoddev.testproject.mapper;

import behzoddev.testproject.dto.GroupStudentDto;
import behzoddev.testproject.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface UserMapper {

    GroupStudentDto mapUserToGroupStudentDto(User user);
}

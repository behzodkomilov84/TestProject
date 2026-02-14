package behzoddev.testproject.mapper;

import behzoddev.testproject.dto.teacher.GroupStudentDto;
import behzoddev.testproject.entity.User;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserMapper {

    GroupStudentDto mapUserToGroupStudentDto(User user);
}

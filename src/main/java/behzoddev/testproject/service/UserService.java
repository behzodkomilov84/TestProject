package behzoddev.testproject.service;

import behzoddev.testproject.dto.user.LoginDto;
import behzoddev.testproject.dto.user.RegisterDto;

public interface UserService {
    void register(RegisterDto dto);

    void checkCredentials(LoginDto dto);
}

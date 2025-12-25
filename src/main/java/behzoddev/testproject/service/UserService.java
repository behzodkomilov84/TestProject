package behzoddev.testproject.service;

import behzoddev.testproject.dto.LoginDto;
import behzoddev.testproject.dto.RegisterDto;

public interface UserService {
    void register(RegisterDto dto);

    void checkCredentials(LoginDto dto);
}

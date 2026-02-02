package behzoddev.testproject.service;

import behzoddev.testproject.dto.*;
import behzoddev.testproject.entity.User;
import behzoddev.testproject.dao.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.*;

@Service
@RequiredArgsConstructor
public class ProfileService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    // 🔹 смена имени
    public void changeUsername(User user, ChangeUsernameDto changeUsernameDto) {

        if (userRepository.existsByUsername(changeUsernameDto.newUsername())) {
            throw new ResponseStatusException(
                    CONFLICT, "Имя пользователя уже занято"
            );
        }

        user.setUsername(changeUsernameDto.newUsername());
        userRepository.save(user);
    }

    // 🔹 смена пароля
    public void changePassword(User user, ChangePasswordDto dto) {

        if (!passwordEncoder.matches(dto.currentPassword(), user.getPassword())) {
            throw new ResponseStatusException(
                    BAD_REQUEST, "Текущий пароль неверный"
            );
        }

        user.setPassword(passwordEncoder.encode(dto.newPassword()));
        userRepository.save(user);
    }
}

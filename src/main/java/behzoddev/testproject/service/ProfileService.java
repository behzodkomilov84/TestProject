package behzoddev.testproject.service;

import behzoddev.testproject.dao.TestSessionRepository;
import behzoddev.testproject.dto.*;
import behzoddev.testproject.dto.profile.ChangePasswordDto;
import behzoddev.testproject.dto.profile.ChangeUsernameDto;
import behzoddev.testproject.dto.profile.TestHistoryDto;
import behzoddev.testproject.entity.User;
import behzoddev.testproject.dao.UserRepository;
import behzoddev.testproject.mapper.TestSessionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.springframework.http.HttpStatus.*;

@Service
@RequiredArgsConstructor
public class ProfileService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TestSessionRepository testSessionRepository;
    private final TestSessionMapper testSessionMapper;

    // 🔹 смена имени
    @Transactional
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
    @Transactional
    public void changePassword(User user, ChangePasswordDto dto) {

        if (!passwordEncoder.matches(dto.currentPassword(), user.getPassword())) {
            throw new ResponseStatusException(
                    BAD_REQUEST, "Текущий пароль неверный"
            );
        }

        user.setPassword(passwordEncoder.encode(dto.newPassword()));
        userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public PageResponseDto<TestHistoryDto> getHistory(User user, Pageable pageable) {

        Page<TestHistoryDto> pageData = testSessionRepository.getPageableTestHistoryDtoByUser(user, pageable);

        List<TestHistoryDto> dtos = pageData.getContent();

        return new PageResponseDto<>(
                dtos,
                pageData.getTotalPages(),
                pageData.getNumber(),
                pageData.isFirst(),
                pageData.isLast()
        );

    }
}

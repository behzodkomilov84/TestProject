package behzoddev.testproject.service;

import behzoddev.testproject.dao.RoleRepository;
import behzoddev.testproject.dao.UserRepository;
import behzoddev.testproject.dto.ChangeRoleDto;
import behzoddev.testproject.dto.LoginDto;
import behzoddev.testproject.dto.RegisterDto;
import behzoddev.testproject.dto.UserDto;
import behzoddev.testproject.entity.Role;
import behzoddev.testproject.entity.User;
import behzoddev.testproject.exception.PasswordsDoNotMatchException;
import behzoddev.testproject.exception.UserAlreadyExistsException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class UserServiceImpl implements UserDetailsService, UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Username '" + username + "' not found"));
    }


    @Override
    @Transactional
    public void register(RegisterDto dto) {

        // 1. Проверка существования пользователя
        if (userRepository.existsByUsername(dto.username())) {
            throw new UserAlreadyExistsException(dto.username());
        }

        // 2. Проверка паролей
        if (!dto.password().equals(dto.confirmPassword())) {
            throw new PasswordsDoNotMatchException("Passwords do not match");
        }

        // 3. Получаем роль USER (роль должна быть создана в БД через Liquibase)
        Role userRole = roleRepository.findByRoleName("ROLE_USER")
                .orElseThrow(() -> new RuntimeException("ROLE_USER not found in database"));

        // 4. Создаём пользователя с ролью
        User user = User.builder()
                .username(dto.username())
                .password(passwordEncoder.encode(dto.password()))
                .role(userRole)
                .build();

        // 5. Сохраняем (роль уже существует, пользователь сохранится с role_id)
        userRepository.save(user);
    }

    @Override
    @Transactional
    public void checkCredentials(LoginDto dto) {

        // 1. Проверка существования пользователя
        UserDetails user = loadUserByUsername(dto.username());

        // 2. Проверка паролей
        if (!passwordEncoder.matches(dto.password(), user.getPassword())) {
            throw new PasswordsDoNotMatchException("Passwords do not match");
        }
    }

    @Transactional
    public ChangeRoleDto changeUserRole(Long targetUserId, String newRole, Authentication auth) {

        User currentUser = (User) auth.getPrincipal();

        if (currentUser.getId().equals(targetUserId)) {
            throw new AccessDeniedException("⛔ Siz o'z rolingizni o'zgartira olmaysiz.");
        }

        User targetUser = userRepository.findById(targetUserId)
                .orElseThrow(() -> new RuntimeException("⛔ Foydalanuvchi topilmadi"));

        Role role = roleRepository.findByRoleName(newRole)
                .orElseThrow(() -> new RuntimeException(newRole + ": ⛔ Bunday rol topilmadi"));

        targetUser.setRole(role);

        return ChangeRoleDto.builder()
                .userId(targetUser.getId())
                .newRole(role.getRoleName())
                .build();
    }

    @Transactional
    public UserDto deleteUser(Long targetUserId, Authentication auth) {
        User currentUser = (User) auth.getPrincipal();

        if (currentUser.getId().equals(targetUserId)) {
            throw new AccessDeniedException("⛔ Siz o'zingizni o'chira olmaysiz");
        }

        User targetUser = userRepository.findById(targetUserId)
                .orElseThrow(() -> new RuntimeException("⛔ Foydalanuvchi topilmadi"));

        userRepository.delete(targetUser);

        return UserDto.builder()
                .id(targetUserId)
                .username(targetUser.getUsername())
                .build();
    }

}
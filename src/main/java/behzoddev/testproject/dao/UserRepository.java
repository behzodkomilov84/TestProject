package behzoddev.testproject.dao;

import behzoddev.testproject.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    List<User> findByRoles_RoleName(String roleName);

    List<User> findAllByGroupId(Long groupId);

    Optional<User> findByTelegramId(Long telegramId);

    boolean existsByTelegramId(Long telegramId);

    Optional<User> findByGoogleId(String googleId);

    boolean existsByEmail(String email);

    Optional<User> findByEmail(String email);

    // Optional emas — phone_number ustunida UNIQUE cheklov yo'q (eski
    // ma'lumotlarda bir nechta hisob bir xil raqamga ega bo'lishi mumkin,
    // haqiqiy topilgan holat: 2026-09-06), shuning uchun "Optional"
    // qaytarilsa Spring Data bitta natijadan ortig'ida xato tashlaydi.
    List<User> findAllByPhoneNumber(String phoneNumber);


}

package behzoddev.testproject.telegram.service;

import behzoddev.testproject.dao.TelegramLinkCodeRepository;
import behzoddev.testproject.dao.UserRepository;
import behzoddev.testproject.entity.TelegramLinkCode;
import behzoddev.testproject.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.telegram.telegrambots.meta.api.objects.Message;

import java.time.LocalDateTime;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
@Transactional
public class TelegramLinkCodeService {

    private final TelegramLinkCodeRepository telegramLinkCodeRepository;
    private final UserRepository userRepository;

    public String generateCode(Long userId) {

        String code = String.valueOf(
                ThreadLocalRandom.current().nextInt(100000, 999999)
        );

        TelegramLinkCode link = new TelegramLinkCode();

        link.setCode(code);
        link.setUsed(false);
        link.setCreatedAt(LocalDateTime.now());

        User user = userRepository.findById(userId).orElseThrow();
        link.setUser(user);

        telegramLinkCodeRepository.save(link);

        return code;
    }

    public String linkTelegramUser(Message message, String code) {

        TelegramLinkCode link =
                telegramLinkCodeRepository
                        .findByCodeAndUsedFalseAndCreatedAtAfter(
                                code,
                                LocalDateTime.now().minusMinutes(5)
                        )
                        .orElseThrow(() -> new RuntimeException("Kod noto'g'ri yoki eskirgan"));

        Long telegramId = message.getFrom().getId();

        User user = link.getUser();
        user.setTelegramId(telegramId); // User.telegramId поле уже есть
        userRepository.save(user);

        link.setUsed(true);
        telegramLinkCodeRepository.save(link);

        return "Siz muvaffaqiyatli bog'landingiz!";
    }
}


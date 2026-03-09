package behzoddev.testproject.telegram.controller;

import behzoddev.testproject.security.SecurityUtils;
import behzoddev.testproject.telegram.service.TelegramLinkCodeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequiredArgsConstructor
public class TelegramLinkCodeController {

    private final TelegramLinkCodeService telegramLinkCodeService;

    @PostMapping("/api/telegram/link")
    public Map<String, String> createLink() {

        Long userId = SecurityUtils.getCurrentUserId();

        String code = telegramLinkCodeService.generateCode(userId);

        System.out.println("code: " + code);

        return Map.of("code", code);
    }

}

package behzoddev.testproject.service;

import behzoddev.testproject.dao.UserRepository;
import behzoddev.testproject.dto.testsession.UserTestSessionStatsDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// "📊 Statistika" -> "👤 Foydalanuvchilar kesimida test statistikasi"
// (foydalanuvchi so'rovi, 2026-09-13: "foydalanuvchilar kesimida test
// sessiyasi bo'yicha statistika yaratib Statistika menyusiga qo'sh").
// Qidiruv/saralash — FAQAT frontendda (statistics.js), chunki
// foydalanuvchilar soni odatda yuzlab bilan cheklangan (server
// darajasidagi sahifalash hozircha ortiqcha).
@Service
@RequiredArgsConstructor
public class StatisticsService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<UserTestSessionStatsDto> getUserTestSessionStats() {
        return userRepository.findAllUserTestSessionStats();
    }
}

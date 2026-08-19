package behzoddev.testproject.telegram.service;

import behzoddev.testproject.dao.TelegramAutoLoginTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * telegram_auto_login_tokens jadvali cheksiz o'sib ketmasligi uchun —
 * 1 kundan ortiq oldin muddati o'tgan tokenlarni kunlik tozalaydi
 * (foydalanuvchi "eski tokenlarni bazadan tozalab tashla" deb so'ragan,
 * shu bilan birga bu bir martalik emas, doimiy tozalash bo'lishi kerak).
 */
@ExtendWith(MockitoExtension.class)
class TelegramAutoLoginTokenCleanupServiceTest {

    @Mock
    private TelegramAutoLoginTokenRepository repository;

    private TelegramAutoLoginTokenCleanupService cleanupService;

    @BeforeEach
    void setUp() {
        cleanupService = new TelegramAutoLoginTokenCleanupService(repository);
    }

    @Test
    void cleanupExpiredTokens_deletesRowsOlderThanRetentionWindow() {
        when(repository.deleteByExpiresAtBefore(any())).thenReturn(3L);

        cleanupService.cleanupExpiredTokens();

        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(repository).deleteByExpiresAtBefore(captor.capture());

        // Cutoff — taxminan 1 kun oldin (grace period) bo'lishi kerak,
        // undan yangi emas.
        assertThat(captor.getValue()).isBefore(LocalDateTime.now().minusHours(23));
        assertThat(captor.getValue()).isAfter(LocalDateTime.now().minusHours(25));
    }

    @Test
    void cleanupExpiredTokens_noRowsToDelete_doesNotThrow() {
        when(repository.deleteByExpiresAtBefore(any())).thenReturn(0L);

        cleanupService.cleanupExpiredTokens();

        verify(repository).deleteByExpiresAtBefore(any());
    }
}

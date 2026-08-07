package behzoddev.testproject.service;

import behzoddev.testproject.dto.subscription.MonthlyRevenueDto;
import behzoddev.testproject.dto.subscription.SubscriptionStatsDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

/**
 * SMTP orqali yuborish — sozlamalar bo'sh/xato bo'lsa ham ilova ishlashda
 * davom etishi kerak, shuning uchun har bir metod xatolikni yutib
 * boolean qaytaradi (exception otmasin).
 */
@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    private EmailService emailService;

    @BeforeEach
    void setUp() {
        emailService = new EmailService(mailSender);
        ReflectionTestUtils.setField(emailService, "from", "noreply@smart-test.uz");
    }

    @Test
    void sendPasswordResetCode_success_returnsTrueAndIncludesCode() {
        boolean result = emailService.sendPasswordResetCode("user@mail.com", "123456");

        assertThat(result).isTrue();
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        assertThat(captor.getValue().getTo()).containsExactly("user@mail.com");
        assertThat(captor.getValue().getText()).contains("123456");
    }

    @Test
    void sendPasswordResetCode_mailSenderThrows_returnsFalseWithoutPropagating() {
        doThrow(new RuntimeException("SMTP down")).when(mailSender).send(any(SimpleMailMessage.class));

        boolean result = emailService.sendPasswordResetCode("user@mail.com", "123456");

        assertThat(result).isFalse();
    }

    @Test
    void sendVerificationCode_success_returnsTrue() {
        boolean result = emailService.sendVerificationCode("user@mail.com", "654321");

        assertThat(result).isTrue();
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        assertThat(captor.getValue().getText()).contains("654321");
    }

    @Test
    void sendVerificationCode_mailSenderThrows_returnsFalse() {
        doThrow(new RuntimeException("SMTP down")).when(mailSender).send(any(SimpleMailMessage.class));

        assertThat(emailService.sendVerificationCode("user@mail.com", "654321")).isFalse();
    }

    @Test
    void sendSubscriptionReport_success_bodyContainsAllStats() {
        SubscriptionStatsDto stats = SubscriptionStatsDto.builder()
                .totalRevenue(BigDecimal.valueOf(1_000_000))
                .thisMonthRevenue(BigDecimal.valueOf(200_000))
                .totalConfirmedCount(10)
                .activeSubscribersCount(4)
                .pendingCount(2)
                .monthlyBreakdown(List.of(MonthlyRevenueDto.builder()
                        .month("2026-08").amount(BigDecimal.valueOf(200_000)).count(2).build()))
                .build();

        boolean result = emailService.sendSubscriptionReport("owner@mail.com", stats);

        assertThat(result).isTrue();
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        String body = captor.getValue().getText();
        assertThat(body).contains("1000000").contains("2026-08");
    }

    @Test
    void sendSubscriptionReport_mailSenderThrows_returnsFalse() {
        doThrow(new RuntimeException("SMTP down")).when(mailSender).send(any(SimpleMailMessage.class));
        SubscriptionStatsDto stats = SubscriptionStatsDto.builder()
                .totalRevenue(BigDecimal.ZERO).thisMonthRevenue(BigDecimal.ZERO)
                .totalConfirmedCount(0).activeSubscribersCount(0).pendingCount(0)
                .monthlyBreakdown(List.of()).build();

        assertThat(emailService.sendSubscriptionReport("owner@mail.com", stats)).isFalse();
    }
}

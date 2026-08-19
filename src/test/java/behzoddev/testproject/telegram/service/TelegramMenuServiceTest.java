package behzoddev.testproject.telegram.service;

import behzoddev.testproject.dto.course.CourseDto;
import behzoddev.testproject.dto.notification.NotificationDto;
import behzoddev.testproject.dto.subscription.SubscriptionDto;
import behzoddev.testproject.entity.PaymentOrder;
import behzoddev.testproject.entity.Role;
import behzoddev.testproject.entity.User;
import behzoddev.testproject.entity.enums.PaymentOrderStatus;
import behzoddev.testproject.service.CourseService;
import behzoddev.testproject.service.NotificationService;
import behzoddev.testproject.service.SubscriptionService;
import behzoddev.testproject.service.payment.ClickService;
import behzoddev.testproject.service.payment.PaymentOrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

/**
 * Botning asosiy (rolga qarab) menyusi va umumiy bo'limlar (Bildirishnomalar,
 * Obunam, Kurslar, Yordam).
 */
@ExtendWith(MockitoExtension.class)
class TelegramMenuServiceTest {

    @Mock
    private NotificationService notificationService;
    @Mock
    private SubscriptionService subscriptionService;
    @Mock
    private CourseService courseService;
    @Mock
    private PaymentOrderService paymentOrderService;
    @Mock
    private ClickService clickService;

    @InjectMocks
    private TelegramMenuService menuService;

    private User student() {
        Role role = Role.builder().id(1L).roleName("ROLE_USER").build();
        return User.builder().id(1L).username("student").telegramId(100L)
                .roles(new HashSet<>(Set.of(role))).build();
    }

    private User owner() {
        Role role = Role.builder().id(2L).roleName("ROLE_OWNER").build();
        return User.builder().id(2L).username("owner").telegramId(200L)
                .roles(new HashSet<>(Set.of(role))).build();
    }

    private User admin() {
        Role role = Role.builder().id(3L).roleName("ROLE_ADMIN").build();
        return User.builder().id(3L).username("teacher").telegramId(300L)
                .roles(new HashSet<>(Set.of(role))).build();
    }

    private List<String> allButtonTexts(ReplyKeyboardMarkup keyboard) {
        return keyboard.getKeyboard().stream()
                .flatMap(KeyboardRow::stream)
                .map(org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton::getText)
                .toList();
    }

    // ===== buildMainMenu =====

    @Test
    void buildMainMenu_student_showsAssignmentsResultsAndSubscription() {
        ReplyKeyboardMarkup menu = menuService.buildMainMenu(student());

        List<String> buttons = allButtonTexts(menu);
        assertThat(buttons).contains(
                TelegramMenuService.BTN_MY_ASSIGNMENTS,
                TelegramMenuService.BTN_MY_RESULTS,
                TelegramMenuService.BTN_SUBSCRIPTION,
                TelegramMenuService.BTN_PROFILE
        );
        assertThat(buttons).doesNotContain(TelegramMenuService.BTN_USERS, TelegramMenuService.BTN_MY_GROUPS);
    }

    @Test
    void buildMainMenu_owner_showsOwnerToolsAndNoSubscriptionButton() {
        ReplyKeyboardMarkup menu = menuService.buildMainMenu(owner());

        List<String> buttons = allButtonTexts(menu);
        assertThat(buttons).contains(
                TelegramMenuService.BTN_USERS,
                TelegramMenuService.BTN_PAYMENTS,
                TelegramMenuService.BTN_SETTINGS,
                TelegramMenuService.BTN_BROADCAST
        );
        // OWNER'ga ADMIN obunasi kerak emas.
        assertThat(buttons).doesNotContain(TelegramMenuService.BTN_SUBSCRIPTION);
    }

    @Test
    void buildMainMenu_admin_showsTeachingToolsButNotOwnerTools() {
        ReplyKeyboardMarkup menu = menuService.buildMainMenu(admin());

        List<String> buttons = allButtonTexts(menu);
        assertThat(buttons).contains(
                TelegramMenuService.BTN_MY_GROUPS,
                TelegramMenuService.BTN_NEW_ASSIGNMENT,
                TelegramMenuService.BTN_QUESTIONS
        );
        assertThat(buttons).doesNotContain(TelegramMenuService.BTN_USERS, TelegramMenuService.BTN_BROADCAST);
    }

    // ===== showSubscription =====

    @Test
    void showSubscription_owner_saysNoSubscriptionNeeded() {
        SendMessage msg = menuService.showSubscription(owner());

        assertThat(msg.getText()).contains("OWNER").contains("kerak emas");
    }

    @Test
    void showSubscription_noActiveSubscription_showsInactiveStatus() {
        User user = student();
        when(subscriptionService.listForUser(user.getId())).thenReturn(List.of());
        when(paymentOrderService.getPricePerMonthSom()).thenReturn(50_000L);
        when(clickService.isEnabled()).thenReturn(false);

        SendMessage msg = menuService.showSubscription(user);

        assertThat(msg.getText()).contains("Faol ADMIN obunangiz yo'q");
    }

    @Test
    void showSubscription_activeSubscription_showsEndDateAndClickButton() {
        User user = student();
        SubscriptionDto active = new SubscriptionDto(1L, user.getId(), "student", BigDecimal.valueOf(50_000),
                "ONLINE", "CONFIRMED", LocalDateTime.now().minusDays(5), LocalDateTime.now().plusDays(25),
                null, LocalDateTime.now().minusDays(5));
        when(subscriptionService.listForUser(user.getId())).thenReturn(List.of(active));
        when(paymentOrderService.getPricePerMonthSom()).thenReturn(50_000L);
        when(clickService.isEnabled()).thenReturn(true);

        SendMessage msg = menuService.showSubscription(user);

        assertThat(msg.getText()).contains("ADMIN huquqi faol");
        assertThat(msg.getReplyMarkup()).isNotNull();
    }

    // ===== createClickPaymentLink =====

    @Test
    void createClickPaymentLink_success_returnsCheckoutUrl() {
        User user = student();
        PaymentOrder order = PaymentOrder.builder().id(10L).amount(BigDecimal.valueOf(50_000))
                .durationMonths(1).status(PaymentOrderStatus.CREATED).build();
        when(paymentOrderService.createOrder(user, 1)).thenReturn(order);
        when(clickService.buildPayUrl(order, "/profile")).thenReturn("https://my.click.uz/pay/123");

        SendMessage msg = menuService.createClickPaymentLink(user);

        assertThat(msg.getText()).contains("https://my.click.uz/pay/123");
    }

    @Test
    void createClickPaymentLink_ownerRejected_showsErrorWithoutThrowing() {
        User user = owner();
        when(paymentOrderService.createOrder(user, 1))
                .thenThrow(new IllegalArgumentException("❌OWNER uchun ADMIN obunasi kerak emas"));

        SendMessage msg = menuService.createClickPaymentLink(user);

        assertThat(msg.getText()).contains("❌").contains("OWNER");
    }

    // ===== showNotifications =====

    @Test
    void showNotifications_none_saysEmpty() {
        User user = student();
        when(notificationService.list(user)).thenReturn(List.of());

        SendMessage msg = menuService.showNotifications(user);

        assertThat(msg.getText()).contains("hali bildirishnoma yo'q");
    }

    @Test
    void showNotifications_allRead_showsCount() {
        User user = student();
        NotificationDto read = NotificationDto.builder().id(1L).message("Salom").link(null)
                .read(true).createdAt(LocalDateTime.now()).build();
        when(notificationService.list(user)).thenReturn(List.of(read));

        SendMessage msg = menuService.showNotifications(user);

        assertThat(msg.getText()).contains("Barcha bildirishnomalar o'qilgan");
    }

    @Test
    void showNotifications_hasUnread_listsThemWithMarkAllButton() {
        User user = student();
        NotificationDto unread = NotificationDto.builder().id(1L).message("Yangi topshiriq").link(null)
                .read(false).createdAt(LocalDateTime.now()).build();
        when(notificationService.list(user)).thenReturn(List.of(unread));

        SendMessage msg = menuService.showNotifications(user);

        assertThat(msg.getText()).contains("Yangi topshiriq");
        assertThat(msg.getReplyMarkup()).isNotNull();
    }

    // ===== showCourses =====

    @Test
    void showCourses_none_saysEmpty() {
        User user = student();
        when(courseService.listCatalog(user)).thenReturn(List.of());

        SendMessage msg = menuService.showCourses(user);

        assertThat(msg.getText()).contains("kurslar mavjud emas");
    }

    @Test
    void showCourses_listsTitlesWithSubscriptionStatus() {
        User user = student();
        CourseDto course = CourseDto.builder().id(1L).title("Java Asoslari").description("...")
                .coverImageUrl(null).published(true).sectionCount(5).subscribed(true)
                .createdAt(LocalDateTime.now()).build();
        when(courseService.listCatalog(user)).thenReturn(List.of(course));

        SendMessage msg = menuService.showCourses(user);

        assertThat(msg.getText()).contains("Java Asoslari").contains("5 bo'lim");
    }
}

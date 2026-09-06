package behzoddev.testproject.service;

import behzoddev.testproject.dao.RoleRepository;
import behzoddev.testproject.dao.UserRepository;
import behzoddev.testproject.entity.Role;
import behzoddev.testproject.entity.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.net.URLEncoder;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

// "Google orqali kirish" — Telegram'dan keyingi ijtimoiy login usuli
// (foydalanuvchi so'rovi, 2026-09-06: "Facebook, google, telegram орқали
// кириш" — Telegram tayyor, navbatda Google). Spring Security'ning tayyor
// OAuth2 Login modulini (spring-boot-starter-oauth2-client) ATAYLAB
// ISHLATMAYDI — u standart holda principal sifatida OAuth2User/OidcUser
// qaytaradi, lekin loyihaning DEYARLI HAR BIR kontrolleri
// "@AuthenticationPrincipal User user" (o'zimizning entity) kutadi. Shu
// sabab qo'lda, oddiy "Authorization Code" oqimi (RestClient, hech qanday
// yangi Maven bog'liqligisiz) qo'llanildi — TelegramWidgetLoginService
// bilan bir xil yakuniy natija (o'zimizning User, sessiyaga qo'lda
// yoziladi).
@Service
public class GoogleLoginService {

    private static final String AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final String USERINFO_URL = "https://www.googleapis.com/oauth2/v3/userinfo";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final RestClient restClient = RestClient.create();
    private final SecureRandom random = new SecureRandom();

    @Value("${google.client-id}")
    private String clientId;

    @Value("${google.client-secret}")
    private String clientSecret;

    @Value("${app.public-base-url}")
    private String publicBaseUrl;

    public GoogleLoginService(UserRepository userRepository, RoleRepository roleRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public boolean isConfigured() {
        return clientId != null && !clientId.isBlank() && clientSecret != null && !clientSecret.isBlank();
    }

    public String generateState() {
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    // "prompt=select_account" — Telegram'dagidan farqli, Google BUNI
    // qo'llab-quvvatlaydi: har safar hisob tanlash oynasini majburiy
    // ko'rsatadi (brauzerda avvaldan Google sessiyasi bo'lsa ham), shuning
    // uchun "boshqa hisob bilan kirish" Google uchun HAQIQIY, ishlaydigan
    // funksiya (foydalanuvchi so'rovi, 2026-09-06: "boshqa account orqali
    // kirish ham mumkin bo'lsin" — Telegram'da bu Telegram'ning o'zi
    // tomonidan cheklangan edi, Google'da esa yechim bor).
    public String buildAuthorizationUrl(String state) {
        return UriComponentsBuilder.fromUriString(AUTH_URL)
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", redirectUri())
                .queryParam("response_type", "code")
                .queryParam("scope", "openid email profile")
                .queryParam("state", state)
                .queryParam("prompt", "select_account")
                .build()
                .toUriString();
    }

    // "currentUser" — Telegram'dagi bilan bir xil himoya (haqiqiy topilgan
    // bug, 2026-09-06): agar so'rov ALLAQACHON tizimga kirgan
    // foydalanuvchidan kelayotgan bo'lsa, JORIY hisobiga ulanadi — email
    // moslashtirishga ham ishonib o'tirmasdan (masalan Google email'i sayt
    // hisobinikidan farq qilishi yoki tasdiqlanmagan bo'lishi mumkin).
    @Transactional
    public User handleCallback(String code, User currentUser) {
        GoogleTokenResponse tokenResponse = restClient.post()
                .uri(TOKEN_URL)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(tokenRequestBody(code))
                .retrieve()
                .body(GoogleTokenResponse.class);

        if (tokenResponse == null || tokenResponse.access_token() == null) {
            throw new IllegalStateException("Google token almashinuvida xatolik yuz berdi.");
        }

        GoogleUserInfo info = restClient.get()
                .uri(USERINFO_URL)
                .header("Authorization", "Bearer " + tokenResponse.access_token())
                .retrieve()
                .body(GoogleUserInfo.class);

        if (info == null || info.sub() == null) {
            throw new IllegalStateException("Google profil ma'lumotlarini olib bo'lmadi.");
        }

        Optional<User> existingByGoogle = userRepository.findByGoogleId(info.sub());

        if (currentUser != null) {
            return linkToCurrentUser(info, currentUser, existingByGoogle);
        }

        return existingByGoogle
                .or(() -> linkByVerifiedEmail(info))
                .orElseGet(() -> createUser(info));
    }

    private User linkToCurrentUser(GoogleUserInfo info, User currentUser, Optional<User> existingByGoogle) {
        if (existingByGoogle.isPresent()) {
            User linked = existingByGoogle.get();
            if (!linked.getId().equals(currentUser.getId())) {
                throw new IllegalStateException("❌ Bu Google hisobi allaqachon boshqa foydalanuvchiga ulangan.");
            }
            return linked; // allaqachon o'ziga ulangan
        }

        // MUHIM, haqiqiy topilgan bug (2026-09-07) — TelegramWidgetLoginService
        // bilan bir xil muammo: "currentUser" (@AuthenticationPrincipal) HTTP
        // sessiyaga login vaqtida saqlangan ESKI nusxa, joriy so'rov uchun
        // bazadan qayta o'qilmaydi. To'g'ridan-to'g'ri saqlasak, sessiya
        // boshlangandan keyingi (masalan admin panelidan) BARCHA boshqa
        // o'zgarishlar yo'qolib ketardi. Shuning uchun bazadan yangi nusxa
        // olinadi.
        User fresh = userRepository.findById(currentUser.getId())
                .orElseThrow(() -> new IllegalStateException("⛔ Foydalanuvchi topilmadi"));

        fresh.setGoogleId(info.sub());
        if (fresh.getAvatarUrl() == null) fresh.setAvatarUrl(info.picture());
        if (fresh.getFirstName() == null) fresh.setFirstName(info.given_name());
        if (fresh.getLastName() == null) fresh.setLastName(info.family_name());

        return userRepository.save(fresh);
    }

    private String redirectUri() {
        return publicBaseUrl + "/oauth2/google/callback";
    }

    private String tokenRequestBody(String code) {
        return "code=" + enc(code)
                + "&client_id=" + enc(clientId)
                + "&client_secret=" + enc(clientSecret)
                + "&redirect_uri=" + enc(redirectUri())
                + "&grant_type=authorization_code";
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    // Google email'ni tasdiqlagan bo'lsa (email_verified=true) — allaqachon
    // shu email bilan ro'yxatdan o'tgan hisobga avtomatik bog'laydi
    // (TelegramLinkCodeService'dagi "mavjud rasm ustidan yozilmaydi" bilan
    // bir xil g'oya — bo'sh maydonlarni to'ldiradi, mavjudlarini emas).
    private Optional<User> linkByVerifiedEmail(GoogleUserInfo info) {
        if (info.email() == null || !Boolean.TRUE.equals(info.email_verified())) {
            return Optional.empty();
        }
        return userRepository.findByEmail(info.email()).map(user -> {
            user.setGoogleId(info.sub());
            if (user.getAvatarUrl() == null) user.setAvatarUrl(info.picture());
            if (user.getFirstName() == null) user.setFirstName(info.given_name());
            if (user.getLastName() == null) user.setLastName(info.family_name());
            return userRepository.save(user);
        });
    }

    // Birinchi marta Google orqali kirgan foydalanuvchi uchun —
    // TelegramWidgetLoginService#createUser bilan bir xil g'oya: parol
    // tasodifiy (hech qachon ishlatilmaydi), ism/familiya/rasm Google'dan,
    // ish/lavozim profilda keyinroq to'ldiriladi.
    private User createUser(GoogleUserInfo info) {
        Role userRole = roleRepository.findByRoleName("ROLE_USER")
                .orElseThrow(() -> new IllegalStateException("ROLE_USER bazada topilmadi"));

        Set<Role> roles = new HashSet<>();
        roles.add(userRole);

        byte[] randomPasswordBytes = new byte[24];
        random.nextBytes(randomPasswordBytes);
        String randomPassword = Base64.getUrlEncoder().withoutPadding().encodeToString(randomPasswordBytes);

        boolean hasVerifiedEmail = info.email() != null && Boolean.TRUE.equals(info.email_verified());

        User user = User.builder()
                .username(generateUniqueUsername(info))
                .password(passwordEncoder.encode(randomPassword))
                .roles(roles)
                .googleId(info.sub())
                .firstName(info.given_name())
                .lastName(info.family_name())
                // Google'ning rasm URL'i barqaror va ochiq — Telegram'dan
                // farqli, to'g'ridan-to'g'ri o'zimizga ko'chirib olish
                // (download+rehost) shart emas.
                .avatarUrl(info.picture())
                .email(hasVerifiedEmail ? info.email() : null)
                .emailVerified(true)
                .build();

        return userRepository.save(user);
    }

    private String generateUniqueUsername(GoogleUserInfo info) {
        String base;
        if (info.email() != null && info.email().contains("@")) {
            base = "g_" + info.email().substring(0, info.email().indexOf('@'))
                    .toLowerCase().replaceAll("[^a-z0-9_]", "");
        } else {
            base = "g_" + info.sub();
        }
        if (base.equals("g_")) {
            base = "g_" + info.sub();
        }

        String candidate = base;
        int suffix = 1;
        while (userRepository.existsByUsername(candidate)) {
            candidate = base + "_" + (++suffix);
        }
        return candidate;
    }

    private record GoogleTokenResponse(String access_token, String id_token, String token_type, Integer expires_in) {
    }

    private record GoogleUserInfo(String sub, String email, Boolean email_verified,
                                   String given_name, String family_name, String picture) {
    }
}

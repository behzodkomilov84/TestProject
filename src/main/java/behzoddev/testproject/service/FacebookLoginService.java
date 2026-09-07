package behzoddev.testproject.service;

import behzoddev.testproject.dao.RoleRepository;
import behzoddev.testproject.dao.UserRepository;
import behzoddev.testproject.entity.Role;
import behzoddev.testproject.entity.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

// "Facebook orqali kirish" — Telegram/Google'dan keyingi uchinchi ijtimoiy
// login usuli (foydalanuvchi so'rovi, 2026-09-07: "Facebook, google,
// telegram orqali kirish" — birinchi ikkitasi tayyor edi). GoogleLoginService
// bilan AYNAN BIR XIL andoza: Spring Security'ning tayyor OAuth2 Login
// modulini emas, qo'lda "Authorization Code" oqimini (RestClient) ishlatadi
// — sabab ham bir xil, loyihaning har bir kontrolleri o'zimizning
// "@AuthenticationPrincipal User" kutadi, tayyor modul OAuth2User/OidcUser
// qaytargan bo'lardi.
@Service
public class FacebookLoginService {

    // Facebook Graph API versiyasi — https://developers.facebook.com/docs/graph-api/changelog
    // bo'yicha joriy barqaror versiya. Vaqti-vaqti bilan yangilanishi kerak
    // (Facebook har versiyani taxminan 2 yil qo'llab-quvvatlaydi).
    private static final String GRAPH_VERSION = "v21.0";
    private static final String AUTH_URL = "https://www.facebook.com/" + GRAPH_VERSION + "/dialog/oauth";
    private static final String TOKEN_URL = "https://graph.facebook.com/" + GRAPH_VERSION + "/oauth/access_token";
    private static final String USERINFO_URL = "https://graph.facebook.com/me";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final RestClient restClient = RestClient.create();
    private final SecureRandom random = new SecureRandom();

    @Value("${facebook.app-id}")
    private String appId;

    @Value("${facebook.app-secret}")
    private String appSecret;

    @Value("${app.public-base-url}")
    private String publicBaseUrl;

    public FacebookLoginService(UserRepository userRepository, RoleRepository roleRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public boolean isConfigured() {
        return appId != null && !appId.isBlank() && appSecret != null && !appSecret.isBlank();
    }

    public String generateState() {
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    // Facebook'ning o'zi Google'dagi "prompt=select_account"ga o'xshash
    // rasmiy parametrga ega emas — "auth_type=reauthenticate" eng yaqini,
    // lekin bu har safar parolni qayta so'raydi (boshqa hisob tanlashni
    // emas). Shu sabab Telegram'dagi kabi — hisob almashtirish uchun
    // foydalanuvchi Facebook'ning o'zidan chiqishi kerak bo'lishi mumkin.
    public String buildAuthorizationUrl(String state) {
        return UriComponentsBuilder.fromUriString(AUTH_URL)
                .queryParam("client_id", appId)
                .queryParam("redirect_uri", redirectUri())
                .queryParam("response_type", "code")
                .queryParam("scope", "email public_profile")
                .queryParam("state", state)
                .build()
                .toUriString();
    }

    // "currentUser" — Telegram/Google'dagi bilan bir xil himoya (haqiqiy
    // topilgan bug, 2026-09-06/07): agar so'rov ALLAQACHON tizimga kirgan
    // foydalanuvchidan kelayotgan bo'lsa, JORIY hisobiga ulanadi.
    @Transactional
    public User handleCallback(String code, User currentUser) {
        FacebookTokenResponse tokenResponse = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .scheme("https").host("graph.facebook.com").path("/" + GRAPH_VERSION + "/oauth/access_token")
                        .queryParam("client_id", appId)
                        .queryParam("redirect_uri", redirectUri())
                        .queryParam("client_secret", appSecret)
                        .queryParam("code", code)
                        .build())
                .retrieve()
                .body(FacebookTokenResponse.class);

        if (tokenResponse == null || tokenResponse.access_token() == null) {
            throw new IllegalStateException("Facebook token almashinuvida xatolik yuz berdi.");
        }

        FacebookUserInfo info = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .scheme("https").host("graph.facebook.com").path("/me")
                        .queryParam("fields", "id,first_name,last_name,email,picture.type(large)")
                        .queryParam("access_token", tokenResponse.access_token())
                        .build())
                .retrieve()
                .body(FacebookUserInfo.class);

        if (info == null || info.id() == null) {
            throw new IllegalStateException("Facebook profil ma'lumotlarini olib bo'lmadi.");
        }

        Optional<User> existingByFacebook = userRepository.findByFacebookId(info.id());

        if (currentUser != null) {
            return linkToCurrentUser(info, currentUser, existingByFacebook);
        }

        return existingByFacebook
                .or(() -> linkByVerifiedEmail(info))
                .orElseGet(() -> createUser(info));
    }

    private User linkToCurrentUser(FacebookUserInfo info, User currentUser, Optional<User> existingByFacebook) {
        if (existingByFacebook.isPresent()) {
            User linked = existingByFacebook.get();
            if (!linked.getId().equals(currentUser.getId())) {
                throw new IllegalStateException("❌ Bu Facebook hisobi allaqachon boshqa foydalanuvchiga ulangan.");
            }
            return linked; // allaqachon o'ziga ulangan
        }

        // MUHIM — GoogleLoginService/TelegramWidgetLoginService bilan bir
        // xil, haqiqiy topilgan bug (2026-09-07): "currentUser"
        // (@AuthenticationPrincipal) HTTP sessiyaga login vaqtida
        // saqlangan ESKI nusxa, bazadan qayta o'qilmaydi. To'g'ridan-
        // to'g'ri saqlasak, sessiya boshlangandan keyingi BARCHA boshqa
        // o'zgarishlar yo'qolib ketardi. Shuning uchun bazadan yangi
        // nusxa olinadi.
        User fresh = userRepository.findById(currentUser.getId())
                .orElseThrow(() -> new IllegalStateException("⛔ Foydalanuvchi topilmadi"));

        fresh.setFacebookId(info.id());
        if (fresh.getAvatarUrl() == null) fresh.setAvatarUrl(pictureUrl(info));
        if (fresh.getFirstName() == null) fresh.setFirstName(info.first_name());
        if (fresh.getLastName() == null) fresh.setLastName(info.last_name());

        return userRepository.save(fresh);
    }

    private String redirectUri() {
        return publicBaseUrl + "/oauth2/facebook/callback";
    }

    // Facebook faqat TASDIQLANGAN email'larni "email" maydonida qaytaradi
    // (Google'dagi alohida "email_verified" bayrog'i shart emas) — shuning
    // uchun mavjudligining o'zi yetarli.
    private Optional<User> linkByVerifiedEmail(FacebookUserInfo info) {
        if (info.email() == null || info.email().isBlank()) {
            return Optional.empty();
        }
        return userRepository.findByEmail(info.email()).map(user -> {
            user.setFacebookId(info.id());
            if (user.getAvatarUrl() == null) user.setAvatarUrl(pictureUrl(info));
            if (user.getFirstName() == null) user.setFirstName(info.first_name());
            if (user.getLastName() == null) user.setLastName(info.last_name());
            return userRepository.save(user);
        });
    }

    // Birinchi marta Facebook orqali kirgan foydalanuvchi uchun —
    // GoogleLoginService#createUser bilan bir xil g'oya: parol tasodifiy
    // (hech qachon ishlatilmaydi), ism/familiya/rasm Facebook'dan,
    // ish/lavozim kursga kirishda keyinroq to'ldiriladi (profile-gate.js).
    private User createUser(FacebookUserInfo info) {
        Role userRole = roleRepository.findByRoleName("ROLE_USER")
                .orElseThrow(() -> new IllegalStateException("ROLE_USER bazada topilmadi"));

        Set<Role> roles = new HashSet<>();
        roles.add(userRole);

        byte[] randomPasswordBytes = new byte[24];
        random.nextBytes(randomPasswordBytes);
        String randomPassword = Base64.getUrlEncoder().withoutPadding().encodeToString(randomPasswordBytes);

        boolean hasEmail = info.email() != null && !info.email().isBlank();

        User user = User.builder()
                .username(generateUniqueUsername(info))
                .password(passwordEncoder.encode(randomPassword))
                .roles(roles)
                .facebookId(info.id())
                .firstName(info.first_name())
                .lastName(info.last_name())
                .avatarUrl(pictureUrl(info))
                .email(hasEmail ? info.email() : null)
                .emailVerified(true)
                .build();

        return userRepository.save(user);
    }

    private String generateUniqueUsername(FacebookUserInfo info) {
        String base;
        if (info.email() != null && info.email().contains("@")) {
            base = "fb_" + info.email().substring(0, info.email().indexOf('@'))
                    .toLowerCase().replaceAll("[^a-z0-9_]", "");
        } else {
            base = "fb_" + info.id();
        }
        if (base.equals("fb_")) {
            base = "fb_" + info.id();
        }

        String candidate = base;
        int suffix = 1;
        while (userRepository.existsByUsername(candidate)) {
            candidate = base + "_" + (++suffix);
        }
        return candidate;
    }

    private static String pictureUrl(FacebookUserInfo info) {
        if (info.picture() == null || info.picture().data() == null) return null;
        return info.picture().data().url();
    }

    private record FacebookTokenResponse(String access_token, String token_type, Integer expires_in) {
    }

    private record FacebookUserInfo(String id, String first_name, String last_name, String email,
                                     FacebookPicture picture) {
    }

    private record FacebookPicture(FacebookPictureData data) {
    }

    private record FacebookPictureData(String url) {
    }
}

package behzoddev.testproject.config;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.servlet.FlashMap;
import org.springframework.web.servlet.support.SessionFlashMapManager;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/css/**",
                                "/js/**",
                                "/images/**",
                                "/registration",
                                "/api/test-session/**",
                                "/"
                        ).permitAll()

                        .requestMatchers("/user/tests/**")
                        .hasAuthority("ROLE_USER")

                        .requestMatchers(
                                "/users",
                                "/users/**",
                                "/api/users/**")
                        .hasAuthority("ROLE_OWNER")// <-- доступ только владельцу

                        // API тестов доступно всем авторизованным (USER, ADMIN, OWNER)
                        .requestMatchers(
                                "/api/tests/**",
                                "/api/profile/**",
                                "/profile/**")
                        .authenticated()

                        .requestMatchers("/api/**")
                        .hasAnyAuthority("ROLE_OWNER",
                                "ROLE_ADMIN")

                        .requestMatchers("/pupil",
                                "/pupil/**")
                        .hasAnyAuthority("ROLE_OWNER", "ROLE_USER")

                        .requestMatchers("/teacher",
                                "/teacher/**",
                                "/api/teacher",
                                "/api/teacher/**")

                        .hasAnyAuthority("ROLE_OWNER", "ROLE_ADMIN")

                        .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> ex
                        // Для API → JSON
                        .accessDeniedHandler((request, response, ex1) -> {
                            if (request.getRequestURI().startsWith("/api/")) {
                                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                                response.setContentType("application/json");
                                response.getWriter().write(
                                        "{\"error\":\"" + ex1.getMessage() + "\"}"
                                );
                            }
                            // Для браузера → redirect
                            else {
                                response.sendRedirect(
                                        "/app-error?msg=" +
                                                URLEncoder.encode(ex1.getMessage(), StandardCharsets.UTF_8)
                                );
                            }
                        })
                )
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                        .sessionFixation().migrateSession() //Защита от восстановления сессии. Это запрещает повторное использование старых идентификаторов.
                        .maximumSessions(1)
                        .maxSessionsPreventsLogin(false)
                )//Включаем уничтожение сессии при закрытии браузера

                .formLogin(form -> form
                        .loginPage("/login")
                        .loginProcessingUrl("/login")
                        .defaultSuccessUrl("/index", true)
                        .failureHandler((request, response, exception) -> {
                            var flashMap = new FlashMap();
                            flashMap.put("LOGIN_ERROR", exception.getMessage());

                            var flashManager = new SessionFlashMapManager();
                            flashManager.saveOutputFlashMap(flashMap, request, response);

                            response.sendRedirect("/login");
                        })

                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .invalidateHttpSession(true) //Включаем уничтожение сессии при закрытии браузера
                        .deleteCookies("JSESSIONID") //Включаем уничтожение сессии при закрытии браузера
                        .logoutSuccessUrl("/login")
                        .permitAll()
                );

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
package behzoddev.testproject.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/index",
//                                "/login",
                                "/registration",
                                "/registration.html",
                                "/error",
                                "/favicon.ico",
                                "/css/**",
                                "/js/**",
                                "/images/**",
                                "/login-success",
                                "/science",
                                "/topics",
                                "/.well-known/**",
                                "/question",
                                "/question/**",
                                "/"
                        ).permitAll()

                        .requestMatchers("/users")
                        .hasAuthority("ROLE_OWNER") // <-- доступ только владельцу

                        .requestMatchers("/api/**").permitAll() // или authenticated()

                        /*.requestMatchers("/api/**").hasAnyAuthority("ROLE_ADMIN", "ROLE_OWNER")
                        .requestMatchers("/test/**").hasAnyAuthority("ROLE_USER", "ROLE_ADMIN", "ROLE_OWNER") TODO*/
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        .loginProcessingUrl("/login")
                        .defaultSuccessUrl("/index",true)
                        .permitAll()
                )
                .logout(logout -> logout.permitAll());

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
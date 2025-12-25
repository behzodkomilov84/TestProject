package behzoddev.testproject.controller;

import behzoddev.testproject.dao.RoleRepository;
import behzoddev.testproject.dao.UserRepository;
import behzoddev.testproject.entity.Role;
import behzoddev.testproject.entity.User;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;


@RestController
@RequiredArgsConstructor
@RequestMapping("/reg")
public class UserRestController {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public void createUser(@RequestBody RegCredential regCredential) {
        Role userRole = roleRepository.findByRoleName("ROLE_USER")
                .orElseThrow(() -> new RuntimeException("ROLE_USER not found in database"));

        User user = User.builder()
                .username(regCredential.getUsername())
                .password(passwordEncoder.encode(regCredential.getPassword()))
                .role(userRole)
                .build();

        userRepository.save(user);
    }


    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    static class RegCredential {
        private String username;
        private String password;
    }
}

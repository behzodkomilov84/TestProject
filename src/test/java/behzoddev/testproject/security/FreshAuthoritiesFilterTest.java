package behzoddev.testproject.security;

import behzoddev.testproject.dao.UserRepository;
import behzoddev.testproject.entity.Role;
import behzoddev.testproject.entity.User;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * HAQIQIY TOPILGAN KAMCHILIKNI yopadi: Spring Security rollarni login
 * paytida sessiyaga "muzlatib" saqlaydi — bu filter har so'rovda
 * (throttled) DB'dagi ASL rollarni tekshirib, farq bo'lsa sessiyani
 * yangilaydi (masalan ADMIN obunasi muddati tugab, kunlik job rolni
 * olib tashlaganda, foydalanuvchi hali chiqmagan bo'lsa ham).
 */
@ExtendWith(MockitoExtension.class)
class FreshAuthoritiesFilterTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private FreshAuthoritiesFilter filter;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private User userWithRoles(Long id, String... roleNames) {
        Set<Role> roles = new HashSet<>();
        long roleId = 1;
        for (String name : roleNames) {
            roles.add(Role.builder().id(roleId++).roleName(name).build());
        }
        return User.builder().id(id).username("student1").roles(roles).build();
    }

    private MockFilterChainSpy runFilter(User sessionUser) throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(sessionUser, "pwd", sessionUser.getAuthorities()));

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChainSpy chain = new MockFilterChainSpy();

        filter.doFilter(request, response, chain);
        return chain;
    }

    // Oddiy FilterChain — chaqirilganini tekshirish uchun.
    private static class MockFilterChainSpy implements FilterChain {
        boolean called = false;

        @Override
        public void doFilter(jakarta.servlet.ServletRequest request, jakarta.servlet.ServletResponse response) {
            called = true;
        }
    }

    @Test
    void doFilter_rolesUnchanged_leavesContextUntouchedAndProceedsChain() throws Exception {
        User sessionUser = userWithRoles(1L, "ROLE_USER", "ROLE_ADMIN");
        User freshUser = userWithRoles(1L, "ROLE_USER", "ROLE_ADMIN");
        when(userRepository.findById(1L)).thenReturn(Optional.of(freshUser));

        MockFilterChainSpy chain = runFilter(sessionUser);

        assertThat(chain.called).isTrue();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isEqualTo(sessionUser);
    }

    @Test
    void doFilter_adminRoleRevokedInDb_updatesSessionAuthoritiesImmediately() throws Exception {
        User sessionUser = userWithRoles(1L, "ROLE_USER", "ROLE_ADMIN");
        // DB'da obuna tugab, kunlik job ROLE_ADMIN'ni allaqachon olib tashlagan.
        User freshUser = userWithRoles(1L, "ROLE_USER");
        when(userRepository.findById(1L)).thenReturn(Optional.of(freshUser));

        MockFilterChainSpy chain = runFilter(sessionUser);

        assertThat(chain.called).isTrue();
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        assertThat(principal).isEqualTo(freshUser);
        assertThat(((User) principal).hasRole("ROLE_ADMIN")).isFalse();
    }

    @Test
    void doFilter_userDeleted_clearsSecurityContext() throws Exception {
        User sessionUser = userWithRoles(1L, "ROLE_USER");
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        MockFilterChainSpy chain = runFilter(sessionUser);

        assertThat(chain.called).isTrue();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void doFilter_noAuthentication_doesNotQueryDatabase() throws Exception {
        SecurityContextHolder.clearContext();

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChainSpy chain = new MockFilterChainSpy();

        filter.doFilter(request, response, chain);

        assertThat(chain.called).isTrue();
        verifyNoInteractions(userRepository);
    }

    @Test
    void doFilter_calledTwiceQuickly_onlyQueriesDatabaseOnce() throws Exception {
        User sessionUser = userWithRoles(1L, "ROLE_USER");
        when(userRepository.findById(1L)).thenReturn(Optional.of(sessionUser));

        runFilter(sessionUser);
        runFilter(sessionUser);

        // Throttle (30s) ichida ikkinchi chaqiruv DB'ga umuman urilmaydi.
        verify(userRepository, org.mockito.Mockito.times(1)).findById(1L);
    }
}

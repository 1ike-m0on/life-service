package io.github.ikemoon.lifeservice.user.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import io.github.ikemoon.lifeservice.common.security.AuthTokenService;
import io.github.ikemoon.lifeservice.common.security.UserContext;
import io.github.ikemoon.lifeservice.common.security.UserPrincipal;
import io.github.ikemoon.lifeservice.user.entity.User;
import io.github.ikemoon.lifeservice.user.mapper.UserMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class UserAuthServiceTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private AuthTokenService authTokenService;

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void loginReturnsTokenForExistingEmailUser() {
        UserAuthService service = new UserAuthService(userMapper, authTokenService);
        User user = user(10L, "demo@life.local", "demo");
        when(userMapper.selectOne(any(Wrapper.class))).thenReturn(user);
        when(authTokenService.issueToken(user)).thenReturn("token-abc");

        AuthResponse response = service.login(new EmailLoginRequest(" DEMO@life.local "));

        assertThat(response.token()).isEqualTo("token-abc");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.userId()).isEqualTo(10L);
        assertThat(response.email()).isEqualTo("demo@life.local");
    }

    @Test
    void loginCreatesUserWhenEmailDoesNotExist() {
        UserAuthService service = new UserAuthService(userMapper, authTokenService);
        when(userMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(userMapper.insert(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(11L);
            return 1;
        });
        when(authTokenService.issueToken(any(User.class))).thenReturn("token-new");

        AuthResponse response = service.login(new EmailLoginRequest("new-user@life.local"));

        assertThat(response.token()).isEqualTo("token-new");
        assertThat(response.userId()).isEqualTo(11L);
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).insert(userCaptor.capture());
        assertThat(userCaptor.getValue().getEmail()).isEqualTo("new-user@life.local");
        assertThat(userCaptor.getValue().getNickname()).isEqualTo("user_new_user");
        assertThat(userCaptor.getValue().getStatus()).isEqualTo(1);
    }

    @Test
    void currentUserReadsUserContext() {
        UserAuthService service = new UserAuthService(userMapper, authTokenService);
        UserContext.set(new UserPrincipal(10L, "demo@life.local", "demo"));

        CurrentUserResponse response = service.currentUser();

        assertThat(response.userId()).isEqualTo(10L);
        assertThat(response.email()).isEqualTo("demo@life.local");
        assertThat(response.nickname()).isEqualTo("demo");
    }

    @Test
    void logoutRevokesToken() {
        UserAuthService service = new UserAuthService(userMapper, authTokenService);

        service.logout("token-abc");

        verify(authTokenService).revoke("token-abc");
    }

    private static User user(Long id, String email, String nickname) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        user.setNickname(nickname);
        user.setStatus(1);
        return user;
    }
}

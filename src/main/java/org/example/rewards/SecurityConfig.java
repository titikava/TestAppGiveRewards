package org.example.rewards;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.password.*;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import tools.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods = false)
class SecurityConfig {
    @Bean
    PasswordEncoder passwordEncoder() {
        return Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    }

    @Bean
    UserDetailsService accounts(JdbcClient db, PasswordEncoder encoder,
                                @Value("${rewards.admin-password}") String adminPassword) {
        String adminHash = encoder.encode(adminPassword);
        return login -> {
            if ("admin".equals(login)) return User.withUsername("admin").password(adminHash).roles("ADMIN").build();
            return db.sql("SELECT email, password_hash FROM app_user WHERE email = ?")
                    .param(login.toLowerCase(Locale.ROOT))
                    .query((rs, i) -> User.withUsername(rs.getString("email"))
                            .password(rs.getString("password_hash")).roles("USER").build())
                    .optional().orElseThrow(() -> new UsernameNotFoundException("Unknown user"));
        };
    }

    @Bean
    SecurityFilterChain security(HttpSecurity http, ObjectMapper mapper) throws Exception {
        AuthenticationEntryPoint unauthorized = (req, res, ex) -> {
            res.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"rewards\"");
            writeError(req, res, mapper, 401, "UNAUTHORIZED", "Valid Basic credentials are required.");
        };
        return http.csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(c -> c.disable())
                .authorizeHttpRequests(a -> a
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.GET, "/openapi.yaml", "/actuator/health").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/users", "/api/v1/rewards", "/api/v1/grants").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/rewards/*").hasRole("ADMIN")
                        .requestMatchers("/api/v1/rewards/*/grants").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .httpBasic(b -> b.authenticationEntryPoint(unauthorized))
                .exceptionHandling(e -> e.authenticationEntryPoint(unauthorized)
                        .accessDeniedHandler((req, res, ex) ->
                                writeError(req, res, mapper, 403, "FORBIDDEN", "Insufficient permissions.")))
                .build();
    }

    private static void writeError(HttpServletRequest req, HttpServletResponse res, ObjectMapper mapper, int status,
                                   String code, String message) throws IOException {
        ApiRequestLog.failure(req, code, message);
        res.setStatus(status);
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        mapper.writeValue(res.getOutputStream(), new Models.ApiError(code, message));
    }
}

package com.example.demo.security;

import com.example.demo.model.Roles;
import com.example.demo.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.util.matcher.AnyRequestMatcher;
import org.springframework.security.web.util.matcher.RegexRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * Session-based security. Identity always comes from the server-side session established by
 * {@code POST /api/auth/login}; request parameters/headers such as {@code email} or {@code X-User-Email}
 * are never trusted to identify the caller.
 *
 * <p>Role checks here are coarse (which role may call a URL). Ownership checks ("is this <em>your</em>
 * order/product") live in the controllers and services via {@link CurrentUser}.
 */
@Configuration
public class SecurityConfig {

    private static final String ADMIN = Roles.ADMIN;
    private static final String ARTISAN = Roles.ARTISAN;
    private static final String CUSTOMER = Roles.CUSTOMER;

    /**
     * Admin pages are all named /admin..., artisan pages /artisanXxx (but not /artisan-common.js).
     * Note: RegexRequestMatcher matches against path + query string.
     */
    private static final RequestMatcher ADMIN_PAGES = RegexRequestMatcher.regexMatcher("^/admin.*");
    private static final RequestMatcher ADMIN_ACTIONS = RegexRequestMatcher.regexMatcher(
            HttpMethod.POST, "^/(approve|reject|reconsider|suspend)-(artisan|user)/.*");
    private static final RequestMatcher ARTISAN_PAGES = RegexRequestMatcher.regexMatcher("^/artisan[A-Z][^/.?]*(\\?.*)?$");

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
            SecurityContextRepository securityContextRepository,
            UserRepository userRepository) throws Exception {

        // The JS front end reads the XSRF-TOKEN cookie and echoes it in the X-XSRF-TOKEN header
        // (see static/app-core.js). Thymeleaf forms get a hidden _csrf field automatically.
        CookieCsrfTokenRepository csrfRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfRepository.setCookieCustomizer(cookie -> cookie.sameSite("Lax"));

        http
                .securityContext(sc -> sc.securityContextRepository(securityContextRepository))
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfRepository)
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
                .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class)
                .addFilterAfter(new ActiveAccountFilter(userRepository), CsrfCookieFilter.class)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable) // handled by POST /api/auth/logout
                .requestCache(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        // --- Admin ---
                        .requestMatchers(ADMIN_PAGES, ADMIN_ACTIONS).hasRole(ADMIN)
                        .requestMatchers("/api/admin/**").hasRole(ADMIN)
                        .requestMatchers(HttpMethod.PUT, "/api/orders/items/*/admin-refund/decision").hasRole(ADMIN)
                        .requestMatchers(HttpMethod.PUT, "/api/products/*/status").hasRole(ADMIN)

                        // --- Artisan ---
                        .requestMatchers(ARTISAN_PAGES).hasRole(ARTISAN)
                        .requestMatchers(HttpMethod.PUT, "/api/orders/items/*/refund/decision").hasRole(ARTISAN)
                        .requestMatchers(HttpMethod.POST, "/api/products", "/api/products/auction").hasRole(ARTISAN)
                        .requestMatchers(HttpMethod.PUT, "/api/products/*").hasRole(ARTISAN)
                        .requestMatchers(HttpMethod.DELETE, "/api/products/*").hasRole(ARTISAN)
                        .requestMatchers("/api/user/update-artisan-settings", "/api/user/update-shop-banner").hasRole(ARTISAN)

                        // --- Artisan or admin ---
                        .requestMatchers(HttpMethod.PUT, "/api/orders/*/status").hasAnyRole(ARTISAN, ADMIN)

                        // --- Customer ---
                        .requestMatchers("/api/cart/**", "/api/wishlist/**").hasRole(CUSTOMER)
                        .requestMatchers(HttpMethod.GET, "/api/orders").hasRole(CUSTOMER)
                        .requestMatchers(HttpMethod.POST, "/api/orders/items/*/refund").hasRole(CUSTOMER)
                        .requestMatchers(HttpMethod.POST, "/api/auctions/*/bid").hasRole(CUSTOMER)
                        .requestMatchers(HttpMethod.POST, "/api/reviews/**").hasRole(CUSTOMER)

                        // --- Public API ---
                        .requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/register/**",
                                "/api/auth/logout", "/api/auth/activity").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/auth/session").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/products/**", "/api/auctions/**", "/api/reviews/**")
                        .permitAll()
                        // Everything else under /api needs a login; finer checks happen in the controllers.
                        .requestMatchers("/api/**").authenticated()

                        // --- Signed-in pages (any role) ---
                        .requestMatchers("/cart", "/profile", "/orders", "/wishlist", "/notifications",
                                "/myprofile", "/myorders", "/mywishlist", "/OrderConformation")
                        .authenticated()

                        // Public pages and static assets.
                        .anyRequest().permitAll())
                .exceptionHandling(ex -> ex
                        .defaultAuthenticationEntryPointFor(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                                SecurityConfig::isApiRequest)
                        .defaultAuthenticationEntryPointFor(new LoginUrlAuthenticationEntryPoint("/login"),
                                AnyRequestMatcher.INSTANCE));

        return http.build();
    }

    private static boolean isApiRequest(HttpServletRequest request) {
        return request.getRequestURI().startsWith(request.getContextPath() + "/api/");
    }
}

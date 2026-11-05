package com.example.demo.security;

import com.example.demo.model.UserStatus;
import com.example.demo.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * Ends the session of a signed-in user whose account was suspended, rejected or deleted after they logged in.
 * Without this, a suspended user keeps full access until their session times out.
 *
 * <p>Costs one primary-key lookup per dynamic request from a signed-in user; static assets are skipped.
 * Not a {@code @Component}: Spring Boot would also register it as a plain servlet filter.
 */
@RequiredArgsConstructor
class ActiveAccountFilter extends OncePerRequestFilter {

    private final UserRepository userRepository;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        int lastSlash = path.lastIndexOf('/');
        // Static files (/cart.js, /images/Logo.png, ...) have an extension; pages and API routes do not.
        return path.indexOf('.', lastSlash) >= 0;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthenticatedUser principal) {
            Optional<String> status = userRepository.findStatusByUserId(principal.userId());
            if (status.isEmpty() || !UserStatus.isActive(status.get())) {
                SecurityContextHolder.clearContext();
                HttpSession session = request.getSession(false);
                if (session != null) {
                    session.invalidate();
                }
            }
        }
        chain.doFilter(request, response);
    }
}

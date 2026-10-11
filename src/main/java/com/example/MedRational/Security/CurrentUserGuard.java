package com.example.MedRational.Security;

import com.example.MedRational.Repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class CurrentUserGuard {

    private final UserRepository userRepository;

    // Id of the authenticated user, or empty for anonymous requests (public endpoints)
    public Optional<Long> currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return Optional.empty();
        }
        return userRepository.findByEmail(auth.getName()).map(user -> user.getId());
    }

    // Rejects the request (403) unless userId belongs to the authenticated user.
    // JwtAuthenticationFilter stores the user's email as the principal name.
    public void requireSelf(Long userId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new AccessDeniedException("You can only access your own data");
        }

        boolean isSelf = userRepository.findByEmail(auth.getName())
                .map(user -> user.getId().equals(userId))
                .orElse(false);

        if (!isSelf) {
            throw new AccessDeniedException("You can only access your own data");
        }
    }
}

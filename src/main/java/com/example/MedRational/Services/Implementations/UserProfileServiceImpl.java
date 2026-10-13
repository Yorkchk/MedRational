package com.example.MedRational.Services.Implementations;

import com.example.MedRational.DTOs.AuthResponse;
import com.example.MedRational.DTOs.ChangePasswordRequestDTO;
import com.example.MedRational.DTOs.ConfirmCodeRequestDTO;
import com.example.MedRational.DTOs.RequestEmailChangeDTO;
import com.example.MedRational.DTOs.UpdateProfileRequestDTO;
import com.example.MedRational.DTOs.UserProfileResponseDTO;
import com.example.MedRational.Entities.User;
import com.example.MedRational.Repositories.UserRepository;
import com.example.MedRational.Security.JwtUtil;
import com.example.MedRational.Services.Interfaces.EmailService;
import com.example.MedRational.Services.Interfaces.UserProfileService;
import com.example.MedRational.Services.Support.OtpCodes;
import com.example.MedRational.Services.Support.UserNames;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

// The signed-in user's own account. Names and phone change directly; the password needs a code
// sent to the current email, and a new email needs a code sent to that new address.
@Service
@RequiredArgsConstructor
public class UserProfileServiceImpl implements UserProfileService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final JwtUtil jwtUtil;
    private final Clock clock;

    @Override
    public UserProfileResponseDTO getProfile(User user) {
        return toDto(user);
    }

    @Override
    @Transactional
    public UserProfileResponseDTO updateProfile(User user, UpdateProfileRequestDTO request) {
        String firstName = request.getFirstName().trim();
        String lastName = request.getLastName().trim();
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setFullName(UserNames.fullName(firstName, lastName));

        String phone = request.getPhoneNumber();
        user.setPhoneNumber(phone == null || phone.isBlank() ? null : phone.trim());

        userRepository.save(user);
        return toDto(user);
    }

    @Override
    @Transactional
    public String requestPasswordChangeCode(User user) {
        String code = OtpCodes.issue(user, clock);
        userRepository.save(user);
        emailService.sendOtpEmail(user.getEmail(), code, "Confirm your MedRational password change");
        return "Verification code sent to " + user.getEmail();
    }

    @Override
    @Transactional
    public String changePassword(User user, ChangePasswordRequestDTO request) {
        OtpCodes.consume(user, request.getCode(), clock);
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        return "Password updated successfully.";
    }

    @Override
    @Transactional
    public String requestEmailChange(User user, RequestEmailChangeDTO request) {
        String newEmail = request.getNewEmail().trim().toLowerCase();
        if (newEmail.equals(user.getEmail())) {
            throw new IllegalArgumentException("This is already your email.");
        }
        if (userRepository.existsByEmail(newEmail)) {
            throw new IllegalArgumentException("This email is already in use.");
        }

        user.setPendingEmail(newEmail);
        String code = OtpCodes.issue(user, clock);
        userRepository.save(user);
        emailService.sendOtpEmail(newEmail, code, "Confirm your new MedRational email");
        return "Verification code sent to " + newEmail;
    }

    @Override
    @Transactional
    public AuthResponse confirmEmailChange(User user, ConfirmCodeRequestDTO request) {
        String newEmail = user.getPendingEmail();
        if (newEmail == null) {
            throw new IllegalArgumentException("No pending email change found.");
        }
        OtpCodes.consume(user, request.getCode(), clock);
        // Someone may have registered the address since the code was sent
        if (userRepository.existsByEmail(newEmail)) {
            user.setPendingEmail(null);
            userRepository.save(user);
            throw new IllegalArgumentException("This email is already in use.");
        }

        user.setEmail(newEmail);
        user.setPendingEmail(null);
        userRepository.save(user);

        // The JWT subject is the email, so the old token stops working: hand out a new one
        String token = jwtUtil.generateToken(newEmail, user.getRole().name());
        return AuthResponse.builder()
                .token(token)
                .role(user.getRole().name())
                .userId(user.getId())
                .email(newEmail)
                .fullName(user.getFullName())
                .message("Email updated successfully.")
                .build();
    }

    private UserProfileResponseDTO toDto(User user) {
        return UserProfileResponseDTO.builder()
                .id(user.getId())
                .email(user.getEmail())
                .firstName(UserNames.firstName(user))
                .lastName(UserNames.lastName(user))
                .fullName(user.getFullName())
                .phoneNumber(user.getPhoneNumber())
                .role(user.getRole().name())
                .createdAt(user.getCreatedAt())
                .build();
    }
}

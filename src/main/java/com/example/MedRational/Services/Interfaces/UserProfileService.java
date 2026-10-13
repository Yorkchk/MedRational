package com.example.MedRational.Services.Interfaces;

import com.example.MedRational.DTOs.AuthResponse;
import com.example.MedRational.DTOs.ChangePasswordRequestDTO;
import com.example.MedRational.DTOs.ConfirmCodeRequestDTO;
import com.example.MedRational.DTOs.RequestEmailChangeDTO;
import com.example.MedRational.DTOs.UpdateProfileRequestDTO;
import com.example.MedRational.DTOs.UserProfileResponseDTO;
import com.example.MedRational.Entities.User;

public interface UserProfileService {

    UserProfileResponseDTO getProfile(User user);

    UserProfileResponseDTO updateProfile(User user, UpdateProfileRequestDTO request);

    String requestPasswordChangeCode(User user);

    String changePassword(User user, ChangePasswordRequestDTO request);

    String requestEmailChange(User user, RequestEmailChangeDTO request);

    AuthResponse confirmEmailChange(User user, ConfirmCodeRequestDTO request);
}

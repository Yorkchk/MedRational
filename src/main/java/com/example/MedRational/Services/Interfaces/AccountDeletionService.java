package com.example.MedRational.Services.Interfaces;

import com.example.MedRational.Entities.User;

// Deletes an account after an emailed code, from the app (signed in) or the public web page (by email).
// Wrong or expired codes are IllegalArgumentExceptions (400), never 401/403.
public interface AccountDeletionService {

    // Emails a code to the signed-in user
    String requestCode(User user);

    // Emails a code if an account uses this email; says nothing either way, so the page can't be used to
    // find out who has an account
    void requestCode(String email);

    void deleteAccount(User user, String code);

    void deleteAccount(String email, String code);
}

package com.example.MedRational.Services.Support;

import com.example.MedRational.Entities.User;

// First/last names for accounts that only stored fullName (created before the split):
// everything before the first space is the first name, the rest the last name.
public final class UserNames {

    private UserNames() {
    }

    public static String firstName(User user) {
        if (user.getFirstName() != null) {
            return user.getFirstName();
        }
        return split(user.getFullName())[0];
    }

    public static String lastName(User user) {
        if (user.getLastName() != null) {
            return user.getLastName();
        }
        return split(user.getFullName())[1];
    }

    public static String fullName(String firstName, String lastName) {
        return (firstName.trim() + " " + lastName.trim()).trim();
    }

    // {first, last}; blank parts are empty strings, never null
    public static String[] split(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return new String[]{"", ""};
        }
        String trimmed = fullName.trim();
        int space = trimmed.indexOf(' ');
        if (space < 0) {
            return new String[]{trimmed, ""};
        }
        return new String[]{trimmed.substring(0, space), trimmed.substring(space + 1).trim()};
    }
}

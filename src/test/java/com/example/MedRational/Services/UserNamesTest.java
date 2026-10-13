package com.example.MedRational.Services;

import com.example.MedRational.Services.Support.UserNames;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class UserNamesTest {

    @Test
    void splitsAtTheFirstSpace() {
        assertArrayEquals(new String[]{"Youssef", "El Amrani"}, UserNames.split("  Youssef El Amrani "));
    }

    @Test
    void singleWordHasNoLastName() {
        assertArrayEquals(new String[]{"Youssef", ""}, UserNames.split("Youssef"));
    }

    @Test
    void missingNameIsEmpty() {
        assertArrayEquals(new String[]{"", ""}, UserNames.split(null));
        assertArrayEquals(new String[]{"", ""}, UserNames.split("  "));
    }

    @Test
    void fullNameJoinsTrimmedParts() {
        assertEquals("Sara Idrissi", UserNames.fullName(" Sara ", "Idrissi "));
    }
}

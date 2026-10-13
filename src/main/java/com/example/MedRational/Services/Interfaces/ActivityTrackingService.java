package com.example.MedRational.Services.Interfaces;

public interface ActivityTrackingService {

    // Marks the user active today. Cheap after the first call of the day and never throws
    void recordActivity(Long userId);
}

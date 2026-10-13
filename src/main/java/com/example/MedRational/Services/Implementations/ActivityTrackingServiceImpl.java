package com.example.MedRational.Services.Implementations;

import com.example.MedRational.Repositories.UserDailyActivityRepository;
import com.example.MedRational.Services.Interfaces.ActivityTrackingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class ActivityTrackingServiceImpl implements ActivityTrackingService {

    private final UserDailyActivityRepository activityRepository;
    private final Clock clock;

    // Last day stored per user, so a user costs one insert a day rather than one per request
    private final Map<Long, LocalDate> recordedDays = new ConcurrentHashMap<>();

    @Override
    public void recordActivity(Long userId) {
        if (userId == null) return;
        LocalDate today = LocalDate.now(clock);
        if (today.equals(recordedDays.get(userId))) return;
        try {
            activityRepository.insertIfAbsent(userId, today);
            recordedDays.put(userId, today);
        } catch (RuntimeException e) {
            // Statistics must never fail the user's request
            log.warn("Could not record activity of user {}", userId, e);
        }
    }
}

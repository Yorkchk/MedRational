package com.example.MedRational.Services;

import com.example.MedRational.Repositories.UserDailyActivityRepository;
import com.example.MedRational.Services.Implementations.ActivityTrackingServiceImpl;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class ActivityTrackingServiceImplTest {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 13);

    private final UserDailyActivityRepository repository = mock(UserDailyActivityRepository.class);
    private final AtomicReference<Instant> now = new AtomicReference<>(DAY.atTime(9, 0).toInstant(ZoneOffset.UTC));
    private final Clock clock = new Clock() {
        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now.get();
        }
    };
    private final ActivityTrackingServiceImpl service = new ActivityTrackingServiceImpl(repository, clock);

    @Test
    void storesEachUserOnceADay() {
        service.recordActivity(1L);
        service.recordActivity(1L);
        service.recordActivity(2L);

        verify(repository, times(1)).insertIfAbsent(1L, DAY);
        verify(repository, times(1)).insertIfAbsent(2L, DAY);
    }

    @Test
    void storesTheUserAgainTheNextDay() {
        service.recordActivity(1L);
        now.set(DAY.plusDays(1).atTime(0, 5).toInstant(ZoneOffset.UTC));
        service.recordActivity(1L);

        verify(repository).insertIfAbsent(1L, DAY);
        verify(repository).insertIfAbsent(1L, DAY.plusDays(1));
    }

    @Test
    void aFailureNeverReachesTheRequestAndIsRetried() {
        doThrow(new RuntimeException("db down")).doNothing().when(repository).insertIfAbsent(anyLong(), any());

        assertDoesNotThrow(() -> service.recordActivity(1L));
        service.recordActivity(1L);

        verify(repository, times(2)).insertIfAbsent(1L, DAY);
    }

    @Test
    void ignoresAMissingUser() {
        service.recordActivity(null);

        verify(repository, never()).insertIfAbsent(any(), any());
    }
}

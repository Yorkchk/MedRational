package com.example.MedRational.Repositories;

// Row shapes of the admin statistics queries. Numbers stay Number because native and JPQL
// aggregates come back as Integer, Long or BigInteger depending on the query
public final class StatisticsRows {

    private StatisticsRows() {
    }

    // A count for a time bucket: 0 is the period ending today, 1 the one before, and so on
    public interface BucketCount {
        Number getBucket();

        Number getTotal();
    }

    // A count per file or category id
    public interface IdCount {
        Long getId();

        Number getTotal();
    }

    // A count per hashtag name
    public interface NameCount {
        String getName();

        Number getTotal();
    }

    // Counts per id for the last 7 days (recent) and the 7 days before (earlier)
    public interface PeriodCount {
        Long getId();

        Number getRecent();

        Number getEarlier();
    }
}

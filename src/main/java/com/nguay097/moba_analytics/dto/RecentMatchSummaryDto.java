package com.nguay097.moba_analytics.dto;

import java.util.List;

public record RecentMatchSummaryDto(
        int gamesAnalyzed,
        int wins,
        int losses,
        double winRatePercent,
        double kdaRatio,
        List<Breakdown> champions,
        List<Breakdown> roles,
        String summary,
        boolean summaryGenerated
) {
    public record Breakdown(String name, int games, int wins, double winRatePercent) {}
}

package com.nguay097.moba_analytics.controller;

import com.nguay097.moba_analytics.dto.RecentMatchSummaryDto;
import com.nguay097.moba_analytics.service.RecentMatchSummaryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class RecentMatchSummaryController {

    private final RecentMatchSummaryService recentMatchSummaryService;

    /**
     * Constructs a RecentMatchSummaryController with the provided summary service.
     *
     * @param recentMatchSummaryService the service that generates recent match summaries
     */
    public RecentMatchSummaryController(RecentMatchSummaryService recentMatchSummaryService) {
        this.recentMatchSummaryService = recentMatchSummaryService;
    }

    /**
     * Generates a natural-language summary based on the account's latest matches.
     *
     * HTTP GET request to /api/summary
     *
     * @param puuid the player's unique identifier (query parameter)
     * @param platform the platform code such as "na1" or "euw1" (query parameter)
     * @return recent match statistics and a generated or fallback summary
     */
    @GetMapping("/summary")
    public RecentMatchSummaryDto getRecentMatchSummary(
            @RequestParam String puuid,
            @RequestParam String platform
    ) {
        return recentMatchSummaryService.summarize(puuid, platform);
    }
}

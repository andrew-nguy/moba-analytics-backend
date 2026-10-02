package com.nguay097.moba_analytics.service;

import com.nguay097.moba_analytics.dto.MatchDto;
import com.nguay097.moba_analytics.dto.ParticipantDto;
import com.nguay097.moba_analytics.dto.RecentMatchSummaryDto;
import org.springframework.ai.chat.client.ChatClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class RecentMatchSummaryService {

    private static final Logger logger = LoggerFactory.getLogger(RecentMatchSummaryService.class);

    private static final String SUMMARY_PROMPT = "You are writing a short, human-readable recent-form summary for a League of Legends player. "
            + "Synthesize the supplied statistics instead of repeating them. Write exactly 2 or 3 natural sentences as one paragraph. "
            + "Do not use a heading, bullets, markdown, or a list. Mention the overall record and win rate, then describe the clearest "
            + "supported pattern in the player's leading role and champion. Do not infer skill, causes, improvement, or future results. "
            + "Treat one-game results and Unknown roles as too small or incomplete to draw conclusions from. Use only the supplied statistics "
            + "and do not invent or recalculate numbers. Return only the summary paragraph.";

    private final RiotApiService riotApiService;
    private final ChatClient chatClient;

    public RecentMatchSummaryService(RiotApiService riotApiService, ChatClient.Builder chatClientBuilder) {
        this.riotApiService = riotApiService;
        this.chatClient = chatClientBuilder.build();
    }

    /**
     * Produces a recent-form summary from the player's latest available matches.
     *
     * @param puuid the player's unique identifier
     * @param platform the platform code such as "na1" or "oc1"
     * @return player statistics, breakdowns, and a generated or fallback summary
     */
    @Cacheable(value = "recentMatchSummaries", key = "#puuid + ':' + #platform")
    public RecentMatchSummaryDto summarize(String puuid, String platform) {
        String[] matchIds = riotApiService.getMatchIdsByPuuid(puuid, platform);
        List<ParticipantDto> playerGames = getPlayerGames(matchIds, puuid, platform);

        int games = playerGames.size();
        int wins = (int) playerGames.stream().filter(ParticipantDto::win).count();
        int losses = games - wins;
        int kills = playerGames.stream().mapToInt(ParticipantDto::kills).sum();
        int deaths = playerGames.stream().mapToInt(ParticipantDto::deaths).sum();
        int assists = playerGames.stream().mapToInt(ParticipantDto::assists).sum();
        double winRate = percentage(wins, games);
        double kda = roundToOneDecimal(deaths == 0 ? kills + assists : (double) (kills + assists) / deaths);
        List<RecentMatchSummaryDto.Breakdown> champions = breakdown(playerGames, ParticipantDto::championName);
        List<RecentMatchSummaryDto.Breakdown> roles = breakdown(playerGames, ParticipantDto::teamPosition);
        SummaryResult summaryResult = createSummary(games, wins, losses, winRate, kda, champions, roles);

        return new RecentMatchSummaryDto(games, wins, losses, winRate, kda, champions, roles,
                summaryResult.summary(), summaryResult.generated());
    }

    /**
     * Selects the searched player's participant record from each requested match.
     */
    private List<ParticipantDto> getPlayerGames(String[] matchIds, String puuid, String platform) {
        List<ParticipantDto> playerGames = new ArrayList<>();
        if (matchIds == null) {
            return playerGames;
        }

        for (String matchId : matchIds) {
            MatchDto match = riotApiService.getMatchById(matchId, platform);
            if (match == null || match.info() == null || match.info().participants() == null) {
                continue;
            }
            match.info().participants().stream()
                    .filter(participant -> participant != null && puuid.equals(participant.puuid()))
                    .findFirst()
                    .ifPresent(playerGames::add);
        }
        return playerGames;
    }

    /**
     * Groups player records by champion or role and calculates each group's results.
     */
    private List<RecentMatchSummaryDto.Breakdown> breakdown(
            List<ParticipantDto> games, java.util.function.Function<ParticipantDto, String> nameExtractor) {
        Map<String, BreakdownTotals> totals = new LinkedHashMap<>();
        for (ParticipantDto game : games) {
            String name = nameExtractor.apply(game);
            if (name == null || name.isBlank() || "NONE".equalsIgnoreCase(name)) {
                name = "Unknown";
            }
            totals.merge(name, BreakdownTotals.from(game), BreakdownTotals::add);
        }
        return totals.entrySet().stream()
                .map(entry -> new RecentMatchSummaryDto.Breakdown(entry.getKey(), entry.getValue().games(),
                        entry.getValue().wins(), percentage(entry.getValue().wins(), entry.getValue().games())))
                .sorted(Comparator.comparingInt(RecentMatchSummaryDto.Breakdown::games).reversed()
                        .thenComparing(RecentMatchSummaryDto.Breakdown::name))
                .toList();
    }

    /**
     * Generates a narrative from the headline statistics or returns a deterministic fallback.
     */
    private SummaryResult createSummary(int games, int wins, int losses, double winRate, double kda,
                                        List<RecentMatchSummaryDto.Breakdown> champions,
                                        List<RecentMatchSummaryDto.Breakdown> roles) {
        if (games == 0) {
            return new SummaryResult("No matching player data was found in the recent matches.", false);
        }

        try {
            String summary = chatClient.prompt()
                    .system(SUMMARY_PROMPT)
                    .user(summaryFacts(games, wins, losses, winRate, kda, champions, roles))
                    .call()
                    .content();
            if (summary != null && !summary.isBlank()) {
                return new SummaryResult(summary.trim(), true);
            }
            logger.warn("Gemini returned an empty recent match summary");
        } catch (RuntimeException ex) {
            logger.warn("Unable to generate recent match summary with Gemini", ex);
        }

        return new SummaryResult(fallbackSummary(games, wins, losses, winRate, kda, champions, roles), false);
    }

    /**
     * Formats the calculated statistics into a structured input for the AI.
     */
    private String summaryFacts(int games, int wins, int losses, double winRate, double kda,
                                List<RecentMatchSummaryDto.Breakdown> champions,
                                List<RecentMatchSummaryDto.Breakdown> roles) {
        return "Games analyzed: " + games + "\n"
                + "Overall record: " + wins + " wins, " + losses + " losses (" + format(winRate) + "% win rate)\n"
                + "Aggregate KDA ratio: " + format(kda) + "\n"
                + "Leading champion: " + formatBreakdown(mostPlayed(champions)) + "\n"
                + "Leading role: " + formatBreakdown(mostPlayed(roles));
    }

    /**
     * Builds a deterministic summary when AI generation is unavailable.
     */
    private String fallbackSummary(int games, int wins, int losses, double winRate, double kda,
                                   List<RecentMatchSummaryDto.Breakdown> champions,
                                   List<RecentMatchSummaryDto.Breakdown> roles) {
        return "Analyzed " + games + " games: " + wins + " wins and " + losses
                + " losses (" + format(winRate) + "% win rate), with an aggregate KDA ratio of "
                + format(kda) + ". Most played champion: " + topName(champions)
                + "; most played role: " + topName(roles) + ".";
    }

    /**
     * Calculates a percentage rounded to one decimal place.
     */
    private double percentage(int part, int total) {
        return total == 0 ? 0.0 : roundToOneDecimal((double) part * 100 / total);
    }

    /**
     * Rounds a value to one decimal place.
     */
    private double roundToOneDecimal(double value) {
        return Math.round(value * 10) / 10.0;
    }

    /**
     * Formats a number to one decimal place using a consistent decimal separator.
     */
    private String format(double value) {
        return String.format(java.util.Locale.ROOT, "%.1f", value);
    }

    /**
     * Returns the most-played entry, excluding Unknown results. * Breakdown entries are already sorted by games played.
     */
    private RecentMatchSummaryDto.Breakdown mostPlayed(List<RecentMatchSummaryDto.Breakdown> breakdowns) {
        return breakdowns.stream()
                .filter(item -> !"Unknown".equals(item.name()))
                .findFirst()
                .orElse(null);
    }

    /**
     * Formats a breakdown entry for inclusion in the AI prompt.
     */
    private String formatBreakdown(RecentMatchSummaryDto.Breakdown breakdown) {
        return breakdown == null ? "no reliable data" : breakdown.name() + " (" + breakdown.games()
                + " games, " + breakdown.wins() + " wins, " + format(breakdown.winRatePercent()) + "% win rate)";
    }

    /**
     * Returns the name of the most-played entry for the fallback summary.
     * */
    private String topName(List<RecentMatchSummaryDto.Breakdown> breakdowns) {
        RecentMatchSummaryDto.Breakdown breakdown = mostPlayed(breakdowns);
        return breakdown == null ? "unknown" : breakdown.name();
    }

    /**
     * Stores the accumulated game and win counts for a champion or role.
     * */
    private record BreakdownTotals(int games, int wins) {
        private static BreakdownTotals from(ParticipantDto game) {
            return new BreakdownTotals(1, game.win() ? 1 : 0);
        }

        private BreakdownTotals add(BreakdownTotals other) {
            return new BreakdownTotals(games + other.games, wins + other.wins);
        }
    }

    /**
     * Stores the summary text and whether it was successfully generated by the AI.
     */
    private record SummaryResult(String summary, boolean generated) {}
}

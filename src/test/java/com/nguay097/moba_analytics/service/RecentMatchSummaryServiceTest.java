package com.nguay097.moba_analytics.service;

import com.nguay097.moba_analytics.dto.InfoDto;
import com.nguay097.moba_analytics.dto.MatchDto;
import com.nguay097.moba_analytics.dto.ParticipantDto;
import com.nguay097.moba_analytics.dto.RecentMatchSummaryDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RecentMatchSummaryServiceTest {

    private static final String PUUID = "player-puuid";
    private static final String PLATFORM = "oc1";

    private RiotApiService riotApiService;
    private ChatClient chatClient;
    private ChatClient.Builder chatClientBuilder;
    private RecentMatchSummaryService recentMatchSummaryService;

    @BeforeEach
    void setUp() {
        riotApiService = mock(RiotApiService.class);
        chatClient = mock(ChatClient.class);
        chatClientBuilder = mock(ChatClient.Builder.class);
        when(chatClientBuilder.build()).thenReturn(chatClient);
        recentMatchSummaryService = new RecentMatchSummaryService(riotApiService, chatClientBuilder);
    }

    @Test
    void summarize_calculatesStatisticsAndOrdersBreakdownsForTwentyGames() {
        String[] matchIds = new String[20];
        for (int index = 0; index < matchIds.length; index++) {
            matchIds[index] = "match-" + index;
        }
        when(riotApiService.getMatchIdsByPuuid(PUUID, PLATFORM)).thenReturn(matchIds);

        for (int index = 0; index < 6; index++) {
            stubMatch("match-" + index, playerGame("Nami", "UTILITY", index < 4));
        }
        for (int index = 6; index < 9; index++) {
            stubMatch("match-" + index, playerGame("Ahri", "MIDDLE", index == 6));
        }
        for (int index = 9; index < 12; index++) {
            stubMatch("match-" + index, playerGame("Seraphine", "UTILITY", index == 9));
        }
        for (int index = 12; index < 14; index++) {
            stubMatch("match-" + index, playerGame("Lucian", "BOTTOM", index == 12));
        }
        for (int index = 14; index < 16; index++) {
            stubMatch("match-" + index, playerGame("Nunu", "JUNGLE", index == 14));
        }
        stubMatch("match-16", playerGame("Kayle", "TOP", true));
        stubMatch("match-17", playerGame("Lux", "UTILITY", false));
        stubMatch("match-18", playerGame("Swain", "UTILITY", false));
        stubMatch("match-19", playerGame("Thresh", "UTILITY", true));
        stubGeneratedSummary("The player has played mostly utility recently.");

        RecentMatchSummaryDto result = recentMatchSummaryService.summarize(PUUID, PLATFORM);

        assertEquals(20, result.gamesAnalyzed());
        assertEquals(10, result.wins());
        assertEquals(10, result.losses());
        assertEquals(50.0, result.winRatePercent());
        assertEquals(1.7, result.kdaRatio());
        assertEquals("Nami", result.champions().getFirst().name());
        assertEquals("UTILITY", result.roles().getFirst().name());
        assertTrue(result.summaryGenerated());
    }

    @Test
    void summarize_skipsMissingMatchesAndParticipantsForOtherPlayers() {
        when(riotApiService.getMatchIdsByPuuid(PUUID, PLATFORM)).thenReturn(new String[]{"missing", "other"});
        when(riotApiService.getMatchById("missing", PLATFORM)).thenReturn(null);
        when(riotApiService.getMatchById("other", PLATFORM)).thenReturn(matchFor(otherPlayerGame()));

        RecentMatchSummaryDto result = recentMatchSummaryService.summarize(PUUID, PLATFORM);

        assertEquals(0, result.gamesAnalyzed());
        assertFalse(result.summaryGenerated());
        verify(chatClient, never()).prompt();
    }

    @Test
    void summarize_returnsFallbackWhenGeminiFails() {
        when(riotApiService.getMatchIdsByPuuid(PUUID, PLATFORM)).thenReturn(new String[]{"match-1"});
        stubMatch("match-1", playerGame("Nami", "UTILITY", true));
        when(chatClient.prompt()).thenThrow(new RuntimeException("Gemini is unavailable"));

        RecentMatchSummaryDto result = recentMatchSummaryService.summarize(PUUID, PLATFORM);

        assertFalse(result.summaryGenerated());
        assertTrue(result.summary().startsWith("Analyzed 1 games"));
    }

    @Test
    void summarize_usesFallbackWithoutCallingGeminiWhenNoGamesAreFound() {
        when(riotApiService.getMatchIdsByPuuid(PUUID, PLATFORM)).thenReturn(new String[0]);

        RecentMatchSummaryDto result = recentMatchSummaryService.summarize(PUUID, PLATFORM);

        assertEquals("No matching player data was found in the recent matches.", result.summary());
        assertFalse(result.summaryGenerated());
        verify(chatClient, never()).prompt();
    }

    @Test
    void summarize_isConfiguredToCacheResultsByPlayerAndPlatform() throws NoSuchMethodException {
        Method method = RecentMatchSummaryService.class.getMethod("summarize", String.class, String.class);
        Cacheable cacheable = method.getAnnotation(Cacheable.class);

        assertNotNull(cacheable);
        assertEquals("recentMatchSummaries", cacheable.value()[0]);
        assertEquals("#puuid + ':' + #platform", cacheable.key());
    }

    @Test
    void summarize_reusesTheCachedResponseForTheSamePlayerAndPlatform() {
        when(riotApiService.getMatchIdsByPuuid(PUUID, PLATFORM)).thenReturn(new String[]{"match-1"});
        stubMatch("match-1", playerGame("Nami", "UTILITY", true));
        stubGeneratedSummary("The player has played Nami recently.");

        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(CacheTestConfiguration.class);
            context.getBeanFactory().registerSingleton("riotApiService", riotApiService);
            context.getBeanFactory().registerSingleton("chatClientBuilder", chatClientBuilder);
            context.refresh();

            RecentMatchSummaryService cachedService = context.getBean(RecentMatchSummaryService.class);
            cachedService.summarize(PUUID, PLATFORM);
            cachedService.summarize(PUUID, PLATFORM);
        }

        verify(riotApiService).getMatchIdsByPuuid(PUUID, PLATFORM);
        verify(riotApiService).getMatchById("match-1", PLATFORM);
        verify(chatClient).prompt();
    }

    private void stubGeneratedSummary(String summary) {
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec responseSpec = mock(ChatClient.CallResponseSpec.class);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.content()).thenReturn(summary);
    }

    private void stubMatch(String matchId, ParticipantDto participant) {
        when(riotApiService.getMatchById(matchId, PLATFORM)).thenReturn(matchFor(participant));
    }

    private MatchDto matchFor(ParticipantDto participant) {
        return new MatchDto(null, new InfoDto(0, null, 0, List.of(participant)));
    }

    private ParticipantDto playerGame(String champion, String role, boolean win) {
        return participant(PUUID, champion, role, win);
    }

    private ParticipantDto otherPlayerGame() {
        return participant("other-puuid", "Ahri", "MIDDLE", true);
    }

    private ParticipantDto participant(String puuid, String champion, String role, boolean win) {
        return new ParticipantDto(puuid, champion, 0, 4, 3, 1, win, role,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                false, false, 0, 0, 0, null, null, 0);
    }

    @Configuration
    @EnableCaching
    static class CacheTestConfiguration {

        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager();
        }

        @Bean
        RecentMatchSummaryService recentMatchSummaryService(RiotApiService riotApiService,
                                                            ChatClient.Builder chatClientBuilder) {
            return new RecentMatchSummaryService(riotApiService, chatClientBuilder);
        }
    }
}

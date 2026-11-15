package com.fitwell.service;

import com.fitwell.model.dto.OfferDtos.ParsedQueryResponse;
import com.fitwell.model.dto.OfferDtos.QueryPill;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class QueryParserServiceTest {

    private QueryParserService parserService;

    @BeforeEach
    void setUp() {
        parserService = new QueryParserService();
    }

    @Test
    @DisplayName("Faithfully parses mockup page 4 query: medical images in Rabat for about four months")
    void testMockupPage4QueryParsing() {
        String query = "I want to work on medical images in Rabat for about four months";
        ParsedQueryResponse parsed = parserService.parse(query);

        assertEquals("Rabat", parsed.city());
        assertEquals(3, parsed.minDurationMonths());
        assertEquals(5, parsed.maxDurationMonths());
        assertEquals("3 to 5 months", parsed.durationLabel());
        assertTrue(parsed.domains().contains("Medical imaging"));
        assertEquals("Any funding", parsed.compensation());

        List<String> pillLabels = parsed.pills().stream().map(QueryPill::label).toList();
        assertTrue(pillLabels.contains("Medical imaging"));
        assertTrue(pillLabels.contains("Rabat"));
        assertTrue(pillLabels.contains("3 to 5 months"));
        assertTrue(pillLabels.contains("Any funding"));
    }

    @Test
    @DisplayName("Parses French query with Fès, bioinformatique, duration and funded compensation")
    void testFrenchBioinformaticsQuery() {
        String query = "Stage en bioinformatique à Fès 4 mois rémunéré";
        ParsedQueryResponse parsed = parserService.parse(query);

        assertEquals("Fès", parsed.city());
        assertTrue(parsed.domains().contains("Bioinformatics"));
        assertEquals("Funded", parsed.compensation());

        List<String> pillLabels = parsed.pills().stream().map(QueryPill::label).toList();
        assertTrue(pillLabels.contains("Fès"));
        assertTrue(pillLabels.contains("Bioinformatics"));
        assertTrue(pillLabels.contains("Funded"));
    }

    @Test
    @DisplayName("Parses Moroccan cities like Casablanca, Tangier, Marrakech, Oujda")
    void testMoroccanCitiesDetection() {
        assertEquals("Casablanca", parserService.parse("Looking for internship in Casablanca").city());
        assertEquals("Casablanca", parserService.parse("Offre de stage à Casa").city());
        assertEquals("Tangier", parserService.parse("Data science in Tanger 6 months").city());
        assertEquals("Marrakech", parserService.parse("Machine learning in Marrakech").city());
        assertEquals("Oujda", parserService.parse("Vision models in Oujda").city());
    }

    @Test
    @DisplayName("Parses explicit duration ranges: '3 to 5 months' and '3 à 5 mois'")
    void testDurationRanges() {
        ParsedQueryResponse res1 = parserService.parse("internship for 3 to 5 months in Rabat");
        assertEquals(3, res1.minDurationMonths());
        assertEquals(5, res1.maxDurationMonths());
        assertEquals("3 to 5 months", res1.durationLabel());

        ParsedQueryResponse res2 = parserService.parse("stage de 4 à 6 mois à Fès");
        assertEquals(4, res2.minDurationMonths());
        assertEquals(6, res2.maxDurationMonths());
        assertEquals("4 to 6 months", res2.durationLabel());
    }
}


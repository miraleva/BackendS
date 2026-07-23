package com.santsg.tourvisio.chat;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class SearchCriteriaExtractorTest {

    private final SearchCriteriaExtractor extractor = new SearchCriteriaExtractor();
    private final int currentYear = LocalDate.now().getYear();

    @Test
    void testHotel_Scenario1_ExplicitLabels() {
        // 1. "giriş tarihi 22 temmuz çıkış tarihi 26 temmuz"
        SearchCriteria criteria = extractor.extract("giriş tarihi 22 temmuz çıkış tarihi 26 temmuz", "HOTEL_SEARCH", null);
        assertThat(criteria.getCheckInDate()).isEqualTo(LocalDate.of(currentYear, 7, 22));
        assertThat(criteria.getCheckOutDate()).isEqualTo(LocalDate.of(currentYear, 7, 26));
    }

    @Test
    void testHotel_Scenario2_ReversedLabels() {
        // 2. "22 temmuz çıkış 26 temmuz giriş"
        SearchCriteria criteria = extractor.extract("22 temmuz çıkış 26 temmuz giriş", "HOTEL_SEARCH", null);
        assertThat(criteria.getCheckInDate()).isEqualTo(LocalDate.of(currentYear, 7, 26));
        assertThat(criteria.getCheckOutDate()).isEqualTo(LocalDate.of(currentYear, 7, 22));
    }

    @Test
    void testHotel_Scenario3_NoLabels_Order1() {
        // 3. "22 temmuz - 26 temmuz" (no labels) -> checkInDate=22 Temmuz (earlier), checkOutDate=26 Temmuz (later)
        SearchCriteria criteria = extractor.extract("22 temmuz - 26 temmuz", "HOTEL_SEARCH", null);
        assertThat(criteria.getCheckInDate()).isEqualTo(LocalDate.of(currentYear, 7, 22));
        assertThat(criteria.getCheckOutDate()).isEqualTo(LocalDate.of(currentYear, 7, 26));
    }

    @Test
    void testHotel_Scenario3_NoLabels_Order2() {
        // 3. "26 temmuz - 22 temmuz" (no labels, reversed order) -> checkInDate=22 (earlier), checkOutDate=26 (later)
        SearchCriteria criteria = extractor.extract("26 temmuz - 22 temmuz", "HOTEL_SEARCH", null);
        assertThat(criteria.getCheckInDate()).isEqualTo(LocalDate.of(currentYear, 7, 22));
        assertThat(criteria.getCheckOutDate()).isEqualTo(LocalDate.of(currentYear, 7, 26));
    }

    @Test
    void testHotel_Scenario4_SingleDate_AwaitingCheckIn() {
        // 4. Single date "22 temmuz" with awaitingField="giriş tarihi"
        SearchCriteria criteria = extractor.extract("22 temmuz", "HOTEL_SEARCH", "giriş tarihi");
        assertThat(criteria.getCheckInDate()).isEqualTo(LocalDate.of(currentYear, 7, 22));
        assertThat(criteria.getCheckOutDate()).isNull();
    }

    @Test
    void testHotel_Scenario5_SingleDate_AwaitingCheckOut() {
        // 5. Single date "22 temmuz" with awaitingField="çıkış tarihi"
        SearchCriteria criteria = extractor.extract("22 temmuz", "HOTEL_SEARCH", "çıkış tarihi");
        assertThat(criteria.getCheckOutDate()).isEqualTo(LocalDate.of(currentYear, 7, 22));
        assertThat(criteria.getCheckInDate()).isNull();
    }

    // --- FLIGHT EQUIVALENTS ---

    @Test
    void testFlight_Scenario1_ExplicitLabels() {
        // 1. "gidiş tarihi 22 temmuz dönüş tarihi 26 temmuz"
        SearchCriteria criteria = extractor.extract("gidiş tarihi 22 temmuz dönüş tarihi 26 temmuz", "FLIGHT_SEARCH", null);
        assertThat(criteria.getDepartureDate()).isEqualTo(LocalDate.of(currentYear, 7, 22));
        assertThat(criteria.getReturnDate()).isEqualTo(LocalDate.of(currentYear, 7, 26));
    }

    @Test
    void testFlight_Scenario2_ReversedLabels() {
        // 2. "22 temmuz dönüş 26 temmuz gidiş"
        SearchCriteria criteria = extractor.extract("22 temmuz dönüş 26 temmuz gidiş", "FLIGHT_SEARCH", null);
        assertThat(criteria.getDepartureDate()).isEqualTo(LocalDate.of(currentYear, 7, 26));
        assertThat(criteria.getReturnDate()).isEqualTo(LocalDate.of(currentYear, 7, 22));
    }

    @Test
    void testFlight_Scenario3_NoLabels_Order1() {
        // 3. "22 temmuz - 26 temmuz"
        SearchCriteria criteria = extractor.extract("22 temmuz - 26 temmuz", "FLIGHT_SEARCH", null);
        assertThat(criteria.getDepartureDate()).isEqualTo(LocalDate.of(currentYear, 7, 22));
        assertThat(criteria.getReturnDate()).isEqualTo(LocalDate.of(currentYear, 7, 26));
    }

    @Test
    void testFlight_Scenario3_NoLabels_Order2() {
        // 3. "26 temmuz - 22 temmuz" (reversed order)
        SearchCriteria criteria = extractor.extract("26 temmuz - 22 temmuz", "FLIGHT_SEARCH", null);
        assertThat(criteria.getDepartureDate()).isEqualTo(LocalDate.of(currentYear, 7, 22));
        assertThat(criteria.getReturnDate()).isEqualTo(LocalDate.of(currentYear, 7, 26));
    }

    @Test
    void testFlight_Scenario4_SingleDate_AwaitingDeparture() {
        // 4. Single date "22 temmuz" with awaitingField="gidiş tarihi"
        SearchCriteria criteria = extractor.extract("22 temmuz", "FLIGHT_SEARCH", "gidiş tarihi");
        assertThat(criteria.getDepartureDate()).isEqualTo(LocalDate.of(currentYear, 7, 22));
        assertThat(criteria.getReturnDate()).isNull();
    }

    @Test
    void testFlight_Scenario5_SingleDate_AwaitingReturn() {
        // 5. Single date "22 temmuz" with awaitingField="dönüş tarihi"
        SearchCriteria criteria = extractor.extract("22 temmuz", "FLIGHT_SEARCH", "dönüş tarihi");
        assertThat(criteria.getReturnDate()).isEqualTo(LocalDate.of(currentYear, 7, 22));
        assertThat(criteria.getDepartureDate()).isNull();
    }

    @Test
    void testGeneralPoiExclusion() {
        String location = extractor.parseLocation("lunapark", false);
        assertThat(location).isNull();

        String locationWithPunctuation = extractor.parseLocation("müze!", false);
        assertThat(locationWithPunctuation).isNull();

        String validCity = extractor.parseLocation("Antalya", false);
        assertThat(validCity).isEqualTo("Antalya");
    }
}

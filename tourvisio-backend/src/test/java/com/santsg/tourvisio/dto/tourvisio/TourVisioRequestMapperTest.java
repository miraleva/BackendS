package com.santsg.tourvisio.dto.tourvisio;

import com.santsg.tourvisio.chat.SearchCriteria;
import com.santsg.tourvisio.dto.HotelSearchRequest;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TourVisioRequestMapperTest {

    @Test
    void toHotelSearchRequestFromCriteria_shouldMapChildrenAndInfantAgesWithDefaults() {
        SearchCriteria criteria = new SearchCriteria();
        criteria.setLocationOrHotelName("Antalya");
        criteria.setCheckInDate(LocalDate.of(2026, 8, 10));
        criteria.setCheckOutDate(LocalDate.of(2026, 8, 15));
        criteria.setAdultCount(1);
        criteria.setChildCount(1);
        criteria.setInfantCount(1);
        criteria.setChildAges(List.of(7));
        criteria.setInfantAges(List.of(1));

        TourVisioHotelSearchRequest result = TourVisioRequestMapper.toHotelSearchRequestFromCriteria(criteria, "12345", 2);

        assertThat(result).isNotNull();
        assertThat(result.getRoomCriteria()).hasSize(1);
        TourVisioHotelSearchRequest.RoomCriteria room = result.getRoomCriteria().get(0);
        assertThat(room.getAdult()).isEqualTo(1);
        assertThat(room.getChildAges()).containsExactly(7, 1);
    }

    @Test
    void toHotelSearchRequestFromCriteria_shouldInjectFallbackAgesWhenUnstated() {
        SearchCriteria criteria = new SearchCriteria();
        criteria.setLocationOrHotelName("Antalya");
        criteria.setCheckInDate(LocalDate.of(2026, 8, 10));
        criteria.setCheckOutDate(LocalDate.of(2026, 8, 15));
        criteria.setAdultCount(2);
        criteria.setChildCount(2);
        criteria.setInfantCount(0);
        criteria.setChildAges(List.of(5, 9));

        TourVisioHotelSearchRequest result = TourVisioRequestMapper.toHotelSearchRequestFromCriteria(criteria, "12345", 2);

        assertThat(result).isNotNull();
        TourVisioHotelSearchRequest.RoomCriteria room = result.getRoomCriteria().get(0);
        assertThat(room.getAdult()).isEqualTo(2);
        assertThat(room.getChildAges()).containsExactly(5, 9);
    }
}

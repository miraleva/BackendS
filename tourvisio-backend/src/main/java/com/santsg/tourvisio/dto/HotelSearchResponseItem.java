package com.santsg.tourvisio.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotelSearchResponseItem {

    /** TourVisio'dan gelen benzersiz otel ID'si (PriceSearch body.hotels[].id) */
    private String hotelId;

    /** Otel adı */
    private String name;

    /** Şehir / bölge */
    private String region;

    /** Yıldız sayısı */
    private Integer stars;

    /** En iyi teklifin fiyatı */
    private Double price;

    /** Fiyat para birimi (EUR, TRY, USD…) */
    private String currency;

    /** Pansiyon türü (ALL INCLUSIVE, HALF BOARD…) */
    private String pensionType;

    /** Pansiyon türü — pensionType ile aynı, daha açıklayıcı alan adı */
    private String boardType;

    /** Müsaitlik durumu */
    private Boolean available;

    /** En iyi teklifin offerId'si (rezervasyon için gerekli) */
    private String offerId;

    /** Otelin tam thumbnail URL'si */
    private String thumbnail;

    /** Oda adı (örn. DOUBLE ROOM) */
    private String roomName;

    /** TourVisio sağlayıcı kodu — GetProductInfo çağrısı için gerekli (ownerProvider) */
    private Integer provider;
}

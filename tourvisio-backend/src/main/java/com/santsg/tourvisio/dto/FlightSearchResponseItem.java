package com.santsg.tourvisio.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlightSearchResponseItem {
    // Gidis bacagi (tek yon aramada tek bilgi, gidis-donusde "gidis" tarafi)
    private String airline;
    private String departureTime;
    private String arrivalTime;
    private String transfers;
    private String baggage;

    // Fiyat: tek yonde o ucusun fiyati; gidis-donusde ikisinin TOPLAM fiyati.
    private Double price;
    private String currency;

    // Donus bacagi — sadece gidis-donus sonuclarinda dolu, tek yonde null.
    // Doluysa bu sonucun bir round-trip cifti oldugunu, null ise tek yon
    // oldugunu gosterir.
    private String returnAirline;
    private String returnDepartureTime;
    private String returnArrivalTime;
    private String returnTransfers;
    private String returnBaggage;
}

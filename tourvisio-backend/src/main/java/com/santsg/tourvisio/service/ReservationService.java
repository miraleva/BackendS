package com.santsg.tourvisio.service;

import com.santsg.tourvisio.dto.PassengerPrefillResponse;
import com.santsg.tourvisio.dto.PassengerRequest;
import com.santsg.tourvisio.dto.ReservationRequest;
import com.santsg.tourvisio.entity.Passenger;
import com.santsg.tourvisio.entity.Reservation;
import com.santsg.tourvisio.entity.User;
import com.santsg.tourvisio.exception.ResourceNotFoundException;
import com.santsg.tourvisio.repository.ReservationRepository;
import com.santsg.tourvisio.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final UserRepository userRepository;

    public ReservationService(ReservationRepository reservationRepository,
                              UserRepository userRepository) {
        this.reservationRepository = reservationRepository;
        this.userRepository = userRepository;
    }

    /**
     * Oturum açmış kullanıcının profil bilgilerini döner; bu veriler rezervasyon
     * formunu önceden doldurmak (pre-fill) için kullanılır.
     *
     * <p>Kullanıcı bulunamazsa boş bir {@link PassengerPrefillResponse} döner;
     * hata fırlatılmaz — eksik alanlar kullanıcı tarafından doldurulur.</p>
     *
     * @param userId JWT'den çözümlenen kullanıcı kimliği
     * @return profil bilgileriyle dolu (veya boş) DTO
     */
    public PassengerPrefillResponse getPrefillData(Long userId) {
        if (userId == null) {
            return PassengerPrefillResponse.builder().build();
        }

        return userRepository.findById(userId)
                .map(user -> PassengerPrefillResponse.builder()
                        .firstName(user.getFirstName())
                        .lastName(user.getLastName())
                        .email(user.getEmail())
                        .phoneNumber(user.getPhone())
                        .build())
                .orElseGet(() -> PassengerPrefillResponse.builder().build());
    }

    @Transactional
    public Reservation createReservation(ReservationRequest request) {
        // Generate a unique reservation number (e.g. RES-ABC12D)
        String reservationNum = "RES-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        Reservation reservation = Reservation.builder()
                .reservationNumber(reservationNum)
                .type(request.getType().toUpperCase())
                .itemName(request.getItemName())
                .destination(request.getDestination())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .totalPrice(request.getTotalPrice())
                .currency(request.getCurrency())
                .build();

        List<Passenger> passengers = new ArrayList<>();
        for (PassengerRequest pr : request.getPassengers()) {
            Passenger passenger = Passenger.builder()
                    .firstName(pr.getFirstName())
                    .lastName(pr.getLastName())
                    .email(pr.getEmail())
                    .phoneNumber(pr.getPhoneNumber())
                    .identityNumber(pr.getIdentityNumber())
                    .reservation(reservation)
                    .build();
            passengers.add(passenger);
        }

        reservation.setPassengers(passengers);
        return reservationRepository.save(reservation);
    }

    public List<Reservation> getAllReservations() {
        return reservationRepository.findAll();
    }

    public Reservation getReservationById(Long id) {
        return reservationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation with ID " + id + " not found"));
    }
}

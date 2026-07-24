package com.santsg.tourvisio.repository;

import com.santsg.tourvisio.entity.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.List;

@Repository
public interface ReservationRepository extends JpaRepository<Reservation, Long> {
    Optional<Reservation> findByReservationNumber(String reservationNumber);
    long countByUserId(Long userId);
    List<Reservation> findByUserId(Long userId);
    List<Reservation> findByUserIdOrderByIdDesc(Long userId);
}

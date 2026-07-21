package com.santsg.tourvisio.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Entity
@Table(name = "passengers")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = "reservation")
public class Passenger {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Column(nullable = false)
    private String email;

    @Column(name = "phone_number", nullable = false)
    private String phoneNumber;

    @Column(name = "identity_number", nullable = false)
    private String identityNumber;

    @Column(name = "birth_date")
    private java.time.LocalDate birthDate;

    @Column(name = "gender")
    private String gender;

    @Column(name = "nationality")
    private String nationality;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reservation_id", nullable = false)
    @JsonIgnore
    private Reservation reservation;
}

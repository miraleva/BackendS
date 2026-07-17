package com.santsg.tourvisio.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Rezervasyon formunu otomatik doldurmak için kullanıcının profil bilgilerini
 * taşıyan DTO. Yalnızca kullanıcı hesabında kayıtlı olan alanlar dolu gelir;
 * eksik alanlar (ör. identityNumber) null olarak döner ve kullanıcı tarafından
 * form üzerinde tamamlanır.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PassengerPrefillResponse {

    /** Kullanıcının adı (User.firstName) */
    private String firstName;

    /** Kullanıcının soyadı (User.lastName) */
    private String lastName;

    /** Kullanıcının e-posta adresi (User.email) */
    private String email;

    /** Kullanıcının telefon numarası (User.phone) */
    private String phoneNumber;

    // identityNumber (TC Kimlik No) User entity'sinde tutulmadığı için
    // bu DTO'da yer almıyor; kullanıcı formu doldururken elle girer.
}

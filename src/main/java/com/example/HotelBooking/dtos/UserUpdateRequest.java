package com.example.HotelBooking.dtos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.Email;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserUpdateRequest {

    // All fields optional — only provided fields are applied to the existing record
    @Email(message = "Email format is invalid")
    private String email;

    private String password;
    private String firstName;
    private String lastName;
    private String phoneNumber;
}

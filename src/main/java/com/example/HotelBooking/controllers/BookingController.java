package com.example.HotelBooking.controllers;

import com.example.HotelBooking.dtos.BookingDTO;
import com.example.HotelBooking.dtos.Response;
import com.example.HotelBooking.services.BookingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;

    @GetMapping("/all")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<Response> getAllBookings(){
        return ResponseEntity.ok(bookingService.getAllBookings());
    }


    @PostMapping
    @PreAuthorize("hasAuthority('ADMIN') or hasAuthority('CUSTOMER') ")
    public ResponseEntity<Response> createBooking(@RequestBody BookingDTO bookingDTO){
        return ResponseEntity.ok(bookingService.createBooking(bookingDTO));
    }


    // Publicly accessible by design: the booking reference acts as the lookup token
    @GetMapping("/{reference}")
    public ResponseEntity<Response> findBookingByReferenceNo(@PathVariable String reference){
        return ResponseEntity.ok(bookingService.findBookingByReferenceNo(reference));
    }

    @PutMapping("/update")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<Response> updateBooking(@RequestBody BookingDTO bookingDTO){
        return ResponseEntity.ok(bookingService.updateBooking(bookingDTO));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('CUSTOMER')")
    public ResponseEntity<Response> cancelBooking(@PathVariable Long id){
        return ResponseEntity.ok(bookingService.cancelBooking(id));
    }






}

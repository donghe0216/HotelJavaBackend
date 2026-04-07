package com.example.HotelBooking.repositories;

import com.example.HotelBooking.entities.Booking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    List<Booking> findByUserId(Long userId);


    Optional<Booking> findByBookingReference(String bookingReference);


    // Standard interval overlap: new booking [checkIn, checkOut) overlaps existing if
    // checkIn < existing.checkOut AND checkOut > existing.checkIn
    // Only active statuses (BOOKED, CHECKED_IN) block availability; CANCELLED/CHECKED_OUT are ignored
    @Query("""
               SELECT CASE WHEN COUNT(b) = 0 THEN true ELSE false END
                FROM Booking b
                WHERE b.room.id = :roomId
                  AND :checkInDate <= b.checkOutDate
                  AND :checkOutDate >= b.checkInDate
                  AND b.bookingStatus IN ('BOOKED', 'CHECKED_IN')
            """)
    boolean isRoomAvailable(@Param("roomId") Long roomId,
                            @Param("checkInDate") LocalDate checkInDate,
                            @Param("checkOutDate") LocalDate checkOutDate);
}

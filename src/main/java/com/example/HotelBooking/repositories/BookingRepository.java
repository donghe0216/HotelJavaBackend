package com.example.HotelBooking.repositories;

import com.example.HotelBooking.entities.Booking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    List<Booking> findByUserId(Long userId); // Fetch all bookings for a specific user

    // Only BOOKED / CHECKED_IN bookings block deletion; historical (CANCELLED, CHECKED_OUT) do not
    boolean existsByRoomIdAndBookingStatusIn(Long roomId, java.util.List<com.example.HotelBooking.enums.BookingStatus> statuses);

    // Detach historical bookings from the room before deletion so the FK constraint is satisfied
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.transaction.annotation.Transactional
    @Query("UPDATE Booking b SET b.room = null WHERE b.room.id = :roomId AND b.bookingStatus NOT IN :activeStatuses")
    void detachHistoricalBookings(@Param("roomId") Long roomId,
                                  @Param("activeStatuses") java.util.List<com.example.HotelBooking.enums.BookingStatus> activeStatuses);


    Optional<Booking> findByBookingReference(String bookingReference);


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

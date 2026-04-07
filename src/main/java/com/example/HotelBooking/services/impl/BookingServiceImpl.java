package com.example.HotelBooking.services.impl;

import com.example.HotelBooking.dtos.BookingDTO;
import com.example.HotelBooking.dtos.NotificationDTO;
import com.example.HotelBooking.dtos.Response;
import com.example.HotelBooking.entities.Booking;
import com.example.HotelBooking.entities.Room;
import com.example.HotelBooking.entities.User;
import com.example.HotelBooking.enums.BookingStatus;
import com.example.HotelBooking.enums.PaymentStatus;
import com.example.HotelBooking.exceptions.InvalidBookingStateAndDateException;
import com.example.HotelBooking.exceptions.NotFoundException;
import com.example.HotelBooking.repositories.BookingRepository;
import com.example.HotelBooking.repositories.RoomRepository;
import com.example.HotelBooking.services.BookingCodeGenerator;
import com.example.HotelBooking.services.BookingService;
import com.example.HotelBooking.services.NotificationService;
import com.example.HotelBooking.services.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.modelmapper.TypeToken;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class BookingServiceImpl implements BookingService {


    private final BookingRepository bookingRepository;
    private final RoomRepository roomRepository;
    private final NotificationService notificationService;
    private final ModelMapper modelMapper;
    private final UserService userService;
    private final BookingCodeGenerator bookingCodeGenerator;


    @Override
    public Response getAllBookings() {
        List<Booking> bookingList =bookingRepository.findAll(Sort.by(Sort.Direction.DESC, "id"));
        List<BookingDTO> bookingDTOList = modelMapper.map(bookingList, new TypeToken<List<BookingDTO>>() {}.getType());

        for(BookingDTO bookingDTO: bookingDTOList){
            // Prevent circular serialization: Booking → User → Bookings → Booking...
            // Room details are not needed in the admin list view
            bookingDTO.setUser(null);
            bookingDTO.setRoom(null);
        }

        return Response.builder()
                .status(200)
                .message("success")
                .bookings(bookingDTOList)
                .build();
    }

    @Override
    // READ_COMMITTED prevents dirty reads from concurrent transactions.
    // Combined with the pessimistic lock below, ensures availability check
    // and insert are atomic for the same room.
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Response createBooking(BookingDTO bookingDTO) {

        User currentUser = userService.getCurrentLoggedInUser();

        if (bookingDTO.getRoomId() == null) {
            throw new NotFoundException("roomId is required");
        }
        // Pessimistic write lock: serialises concurrent booking attempts for the same room
        Room room = roomRepository.findByIdWithLock(bookingDTO.getRoomId())
                .orElseThrow(()-> new NotFoundException("Room Not Found"));

        if (bookingDTO.getCheckInDate() == null || bookingDTO.getCheckOutDate() == null) {
            throw new InvalidBookingStateAndDateException("checkInDate and checkOutDate are required");
        }

        if (bookingDTO.getCheckInDate().isBefore(LocalDate.now())){
            throw new InvalidBookingStateAndDateException("check in date cannot be before today ");
        }

        if (bookingDTO.getCheckOutDate().isBefore(bookingDTO.getCheckInDate())){
            throw new InvalidBookingStateAndDateException("check out date cannot be before check in date ");
        }

        if (bookingDTO.getCheckInDate().isEqual(bookingDTO.getCheckOutDate())){
            throw new InvalidBookingStateAndDateException("check in date cannot be equal to check out date ");
        }

       boolean isAvailable = bookingRepository.isRoomAvailable(room.getId(), bookingDTO.getCheckInDate(), bookingDTO.getCheckOutDate());
        if (!isAvailable) {
            throw new InvalidBookingStateAndDateException("Room is not available for the selected date ranges");
        }

        BigDecimal totalPrice = calculateTotalPrice(room, bookingDTO);
        String bookingReference = bookingCodeGenerator.generateBookingReference();

        Booking booking = new Booking();
        booking.setUser(currentUser);
        booking.setRoom(room);
        booking.setCheckInDate(bookingDTO.getCheckInDate());
        booking.setCheckOutDate(bookingDTO.getCheckOutDate());
        booking.setTotalPrice(totalPrice);
        booking.setBookingReference(bookingReference);
        // BOOKED is the initial confirmed state; payment is collected on arrival, not upfront
        booking.setBookingStatus(BookingStatus.BOOKED);
        booking.setPaymentStatus(PaymentStatus.PENDING);
        booking.setCreatedAt(LocalDateTime.now());

        bookingRepository.save(booking);

        // send notification via email (async — failure does not affect response)
        NotificationDTO notificationDTO = NotificationDTO.builder()
                .recipient(currentUser.getEmail())
                .subject("Booking Confirmation")
                .body(String.format("Your booking has been confirmed. Booking reference: %s. Payment is due at the hotel upon check-in.", bookingReference))
                .bookingReference(bookingReference)
                .build();
        notificationService.sendEmail(notificationDTO);

        BookingDTO savedBookingDTO = modelMapper.map(booking, BookingDTO.class);

        return Response.builder()
                .status(200)
                .message("Booking is successfully")
                .booking(savedBookingDTO)
                .build();

    }

    @Override
    public Response findBookingByReferenceNo(String bookingReference) {
        Booking booking = bookingRepository.findByBookingReference(bookingReference)
                .orElseThrow(()-> new NotFoundException("Booking with reference No: " + bookingReference + "Not found"));

        BookingDTO bookingDTO = modelMapper.map(booking, BookingDTO.class);
        return  Response.builder()
                .status(200)
                .message("success")
                .booking(bookingDTO)
                .build();
    }

    @Override
    public Response updateBooking(BookingDTO bookingDTO) {
        if (bookingDTO.getId() == null) throw new NotFoundException("Booking id is required");

        Booking existingBooking = bookingRepository.findById(bookingDTO.getId())
                .orElseThrow(()-> new NotFoundException("Booking Not Found"));

        // Admin-only override: no state machine enforcement here by design.
        // Direct status mutation allows correcting corrupt state or handling edge cases.
        if (bookingDTO.getBookingStatus() != null) {
            existingBooking.setBookingStatus(bookingDTO.getBookingStatus());
        }

        if (bookingDTO.getPaymentStatus() != null) {
            existingBooking.setPaymentStatus(bookingDTO.getPaymentStatus());
        }

        bookingRepository.save(existingBooking);

        return Response.builder()
                .status(200)
                .message("Booking Updated Successfully")
                .build();
    }


    @Override
    public Response cancelBooking(Long bookingId) {
        User currentUser = userService.getCurrentLoggedInUser();

        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new NotFoundException("Booking not found"));

        // Ownership check: only the booking owner can cancel
        if (!booking.getUser().getId().equals(currentUser.getId())) {
            throw new InvalidBookingStateAndDateException("You are not authorized to cancel this booking");
        }

        // Only BOOKED status can be cancelled by the customer
        if (booking.getBookingStatus() != BookingStatus.BOOKED) {
            throw new InvalidBookingStateAndDateException("Only bookings with status BOOKED can be cancelled");
        }

        // Cannot cancel on or after check-in day
        if (!LocalDate.now().isBefore(booking.getCheckInDate())) {
            throw new InvalidBookingStateAndDateException("Booking can only be cancelled before the check-in date");
        }

        booking.setBookingStatus(BookingStatus.CANCELLED);
        bookingRepository.save(booking);

        return Response.builder()
                .status(200)
                .message("Booking cancelled successfully")
                .build();
    }

    private BigDecimal calculateTotalPrice(Room room, BookingDTO bookingDTO){
        BigDecimal pricePerNight = room.getPricePerNight();
        if (pricePerNight == null) {
            throw new InvalidBookingStateAndDateException("Room price is not set");
        }
        long days = ChronoUnit.DAYS.between(bookingDTO.getCheckInDate(), bookingDTO.getCheckOutDate());
        return pricePerNight.multiply(BigDecimal.valueOf(days));
    }





}

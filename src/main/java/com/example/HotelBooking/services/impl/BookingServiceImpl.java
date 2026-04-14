package com.example.HotelBooking.services.impl;

import com.example.HotelBooking.dtos.BookingDTO;
import com.example.HotelBooking.dtos.NotificationDTO;
import com.example.HotelBooking.dtos.Response;
import com.example.HotelBooking.entities.Booking;
import com.example.HotelBooking.entities.Room;
import com.example.HotelBooking.entities.User;
import com.example.HotelBooking.enums.BookingStatus;
import com.example.HotelBooking.exceptions.InvalidBookingStateAndDateException;
import com.example.HotelBooking.exceptions.NotFoundException;
import org.springframework.security.access.AccessDeniedException;
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
import java.util.Objects;
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

        //validation: Ensure the check-in date is not before today
        if (bookingDTO.getCheckInDate().isBefore(LocalDate.now())){
            throw new InvalidBookingStateAndDateException("check in date cannot be before today ");
        }

        //validation: Ensure the check-out date is not before check in date
        if (bookingDTO.getCheckOutDate().isBefore(bookingDTO.getCheckInDate())){
            throw new InvalidBookingStateAndDateException("check out date cannot be before check in date ");
        }

        //validation: Ensure the check-in date is not same as check out date
        if (bookingDTO.getCheckInDate().isEqual(bookingDTO.getCheckOutDate())){
            throw new InvalidBookingStateAndDateException("check in date cannot be equal to check out date ");
        }

        //validate room availability
       boolean isAvailable = bookingRepository.isRoomAvailable(room.getId(), bookingDTO.getCheckInDate(), bookingDTO.getCheckOutDate());
        if (!isAvailable) {
            throw new InvalidBookingStateAndDateException("Room is not available for the selected date ranges");
        }

        //calculate the total price needed to pay for the stay
        BigDecimal totalPrice = calculateTotalPrice(room, bookingDTO);
        String bookingReference = bookingCodeGenerator.generateBookingReference();

        //create and save the booking
        Booking booking = new Booking();
        booking.setUser(currentUser);
        booking.setRoom(room);
        booking.setCheckInDate(bookingDTO.getCheckInDate());
        booking.setCheckOutDate(bookingDTO.getCheckOutDate());
        booking.setTotalPrice(totalPrice);
        booking.setBookingReference(bookingReference);
        booking.setBookingStatus(BookingStatus.BOOKED);
        booking.setCreatedAt(LocalDateTime.now());

        bookingRepository.save(booking); //save to database

        //send notification via email (async — failure does not affect response)
        NotificationDTO notificationDTO = NotificationDTO.builder()
                .recipient(currentUser.getEmail())
                .subject("Booking Confirmation")
                .body(String.format("Your booking has been confirmed. Booking reference: %s. Payment is due at the hotel upon check-in.", bookingReference))
                .bookingReference(bookingReference)
                .build();
        notificationService.sendEmail(notificationDTO);

        // return the saved booking data (including reference and total price)
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

        if (bookingDTO.getBookingStatus() != null) {
            validateStatusTransition(existingBooking.getBookingStatus(), bookingDTO.getBookingStatus());
            existingBooking.setBookingStatus(bookingDTO.getBookingStatus());
        }

        bookingRepository.save(existingBooking);

        return Response.builder()
                .status(200)
                .message("Booking Updated Successfully")
                .build();
    }


    @Override
    @Transactional
    public Response cancelBooking(Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new NotFoundException("Booking not found: " + bookingId));

        User currentUser = userService.getCurrentLoggedInUser();

        // Only the booking owner or an ADMIN may cancel.
        boolean isOwner = Objects.equals(booking.getUser().getId(), currentUser.getId());
        boolean isAdmin = currentUser.getRole().name().equals("ADMIN");
        if (!isOwner && !isAdmin) {
            throw new AccessDeniedException("You are not authorised to cancel this booking");
        }

        // Only BOOKED status can be cancelled; all other states are terminal or in-progress.
        if (booking.getBookingStatus() != BookingStatus.BOOKED) {
            throw new InvalidBookingStateAndDateException(
                    "Cannot cancel a booking with status: " + booking.getBookingStatus());
        }

        // Cancellation must be made more than 24 hours before check-in.
        // checkInDate is a calendar date (no time component), so "more than 24 hours before"
        // means today's date must be before checkInDate - 1 day, i.e. checkInDate >= day after tomorrow.
        if (!LocalDate.now().plusDays(1).isBefore(booking.getCheckInDate())) {
            throw new InvalidBookingStateAndDateException(
                    "Cancellation must be made more than 24 hours before check-in");
        }

        booking.setBookingStatus(BookingStatus.CANCELLED);
        bookingRepository.save(booking);

        return Response.builder()
                .status(200)
                .message("Booking cancelled successfully")
                .build();
    }

    /**
     * Business rule: only certain status transitions are valid for an offline-payment hotel.
     * BOOKED is the only non-terminal state that allows branching (check-in / cancel / no-show).
     * CHECKED_IN can only progress to CHECKED_OUT — reverting or cancelling after arrival is not supported.
     * CHECKED_OUT, CANCELLED, and NO_SHOW are terminal: the booking lifecycle has ended.
     */
    private void validateStatusTransition(BookingStatus current, BookingStatus next) {
        // Terminal states: booking lifecycle has ended, no further changes allowed
        if (current == BookingStatus.CHECKED_OUT) {
            throw new InvalidBookingStateAndDateException(
                    "This booking has already been checked out and cannot be modified.");
        }
        if (current == BookingStatus.CANCELLED) {
            throw new InvalidBookingStateAndDateException(
                    "This booking has been cancelled and cannot be modified.");
        }
        if (current == BookingStatus.NO_SHOW) {
            throw new InvalidBookingStateAndDateException(
                    "This booking has been marked as No Show and cannot be modified.");
        }

        // BOOKED: valid targets are CHECKED_IN, CANCELLED, NO_SHOW
        if (current == BookingStatus.BOOKED) {
            if (next == BookingStatus.CHECKED_OUT) {
                throw new InvalidBookingStateAndDateException(
                        "Cannot check out a booking that has not checked in yet. Please set status to CHECKED IN first.");
            }
            if (next == BookingStatus.BOOKED) {
                throw new InvalidBookingStateAndDateException(
                        "Booking is already in BOOKED status.");
            }
        }

        // CHECKED_IN: only CHECKED_OUT is allowed
        if (current == BookingStatus.CHECKED_IN) {
            if (next == BookingStatus.BOOKED) {
                throw new InvalidBookingStateAndDateException(
                        "Cannot revert a booking back to BOOKED after the guest has checked in.");
            }
            if (next == BookingStatus.CANCELLED) {
                throw new InvalidBookingStateAndDateException(
                        "Cannot cancel a booking after the guest has already checked in.");
            }
            if (next == BookingStatus.NO_SHOW) {
                throw new InvalidBookingStateAndDateException(
                        "Cannot mark as No Show after the guest has checked in.");
            }
            if (next == BookingStatus.CHECKED_IN) {
                throw new InvalidBookingStateAndDateException(
                        "Booking is already in CHECKED IN status.");
            }
        }
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

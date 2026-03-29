package com.example.HotelBooking.controllers;

import com.example.HotelBooking.dtos.NotificationDTO;
import com.example.HotelBooking.dtos.Response;
import com.example.HotelBooking.entities.Notification;
import com.example.HotelBooking.repositories.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.modelmapper.TypeToken;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationRepository notificationRepository;
    private final ModelMapper modelMapper;

    @GetMapping("/all")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<Response> getAllNotifications() {
        List<Notification> notifications = notificationRepository.findAll(Sort.by(Sort.Direction.DESC, "id"));
        List<NotificationDTO> notificationDTOs = modelMapper.map(notifications, new TypeToken<List<NotificationDTO>>() {}.getType());
        Response response = Response.builder()
                .status(200)
                .message("success")
                .notifications(notificationDTOs)
                .build();
        return ResponseEntity.ok(response);
    }
}

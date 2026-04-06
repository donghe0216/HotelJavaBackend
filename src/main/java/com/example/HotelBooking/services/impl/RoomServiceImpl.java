package com.example.HotelBooking.services.impl;

import com.example.HotelBooking.dtos.Response;
import com.example.HotelBooking.dtos.RoomDTO;
import com.example.HotelBooking.entities.Room;
import com.example.HotelBooking.enums.RoomType;
import com.example.HotelBooking.exceptions.InvalidBookingStateAndDateException;
import com.example.HotelBooking.exceptions.NameValueRequiredException;
import com.example.HotelBooking.exceptions.NotFoundException;
import com.example.HotelBooking.repositories.RoomRepository;
import com.example.HotelBooking.services.RoomService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.modelmapper.TypeToken;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class RoomServiceImpl implements RoomService {


    private final RoomRepository roomRepository;
    private final ModelMapper modelMapper;

    @Value("${app.image.upload-dir}")
    private String IMAGE_DIRECTORY_FRONTEND;



    @Override
    public Response addRoom(RoomDTO roomDTO, MultipartFile imageFile) {

        if (roomDTO.getRoomNumber() == null) throw new NameValueRequiredException("roomNumber is required");
        validateRoomNumber(roomDTO.getRoomNumber());

        if (roomDTO.getType() == null) throw new NameValueRequiredException("type is required");

        if (roomDTO.getPricePerNight() == null) throw new NameValueRequiredException("pricePerNight is required");
        validatePrice(roomDTO.getPricePerNight());

        if (roomDTO.getCapacity() == null) throw new NameValueRequiredException("capacity is required");
        validateCapacity(roomDTO.getCapacity());

        Room roomToSave = modelMapper.map(roomDTO, Room.class);

        if (imageFile != null){
            String imagePath = saveImageToFrontend(imageFile);
            roomToSave.setImageUrl(imagePath);
        }

        Room savedRoom = roomRepository.save(roomToSave);
        RoomDTO savedRoomDTO = modelMapper.map(savedRoom, RoomDTO.class);

        return Response.builder()
                .status(200)
                .message("Room successfully added")
                .room(savedRoomDTO)
                .build();
    }

    @Override
    public Response updateRoom(RoomDTO roomDTO, MultipartFile imageFile) {
        Room existingRoom = roomRepository.findById(roomDTO.getId())
                .orElseThrow(()-> new NotFoundException("Room not found"));

        if (imageFile != null && !imageFile.isEmpty()){
            String imagePath = saveImageToFrontend(imageFile);
            existingRoom.setImageUrl(imagePath);
        }

        if (roomDTO.getRoomNumber() != null) {
            validateRoomNumber(roomDTO.getRoomNumber());
            existingRoom.setRoomNumber(roomDTO.getRoomNumber());
        }

        if (roomDTO.getPricePerNight() != null) {
            validatePrice(roomDTO.getPricePerNight());
            existingRoom.setPricePerNight(roomDTO.getPricePerNight());
        }

        if (roomDTO.getCapacity() != null) {
            validateCapacity(roomDTO.getCapacity());
            existingRoom.setCapacity(roomDTO.getCapacity());
        }
        if (roomDTO.getType() != null) existingRoom.setType(roomDTO.getType());

        if(roomDTO.getDescription() != null) existingRoom.setDescription(roomDTO.getDescription());

        roomRepository.save(existingRoom);

        return Response.builder()
                .status(200)
                .message("Room updated successfully")
                .build();




    }

    @Override
    public Response getAllRooms() {

       List<Room> roomList = roomRepository.findAll(Sort.by(Sort.Direction.DESC, "id"));

       List<RoomDTO> roomDTOList = modelMapper.map(roomList,new TypeToken<List<RoomDTO>>() {}.getType());

       return Response.builder()
               .status(200)
               .message("success")
               .rooms(roomDTOList)
               .build();
    }

    @Override
    public Response getRoomById(Long id) {

        Room room = roomRepository.findById(id)
                .orElseThrow(()-> new NotFoundException("Room not found"));

        RoomDTO roomDTO = modelMapper.map(room, RoomDTO.class);

        return Response.builder()
                .status(200)
                .message("success")
                .room(roomDTO)
                .build();
    }

    @Override
    public Response deleteRoom(Long id) {
        if (!roomRepository.existsById(id)){
            throw new NotFoundException("Room not found");
        }
        roomRepository.deleteById(id);

        return Response.builder()
                .status(200)
                .message("Room Deleted Successfully")
                .build();
    }

    @Override
    public Response getAvailableRooms(LocalDate checkInDate, LocalDate checkOutDate, RoomType roomType) {

        //validation: Ensure the check-in date is not before today
        if (checkInDate.isBefore(LocalDate.now())){
            throw new InvalidBookingStateAndDateException("check in date cannot be before today ");
        }

        //validation: Ensure the check-out date is not before check in date
        if (checkOutDate.isBefore(checkInDate)){
            throw new InvalidBookingStateAndDateException("check out date cannot be before check in date ");
        }

        //validation: Ensure the check-in date is not same as check out date
        if (checkInDate.isEqual(checkOutDate)){
            throw new InvalidBookingStateAndDateException("check in date cannot be equal to check out date ");
        }

        List<Room> roomList = roomRepository.findAvailableRooms(checkInDate, checkOutDate, roomType);

        List<RoomDTO> roomDTOList = modelMapper.map(roomList,new TypeToken<List<RoomDTO>>() {}.getType());

        return Response.builder()
                .status(200)
                .message("success")
                .rooms(roomDTOList)
                .build();
    }

    @Override
    public List<RoomType> getAllRoomTypes() {

        return Arrays.asList(RoomType.values());
    }

    @Override
    public Response searchRoom(String input) {

        if (input == null || input.isBlank()) {
            throw new NameValueRequiredException("keyword cannot be empty");
        }

        List<Room> roomList = roomRepository.searchRooms(input);

        List<RoomDTO> roomDTOList = modelMapper.map(roomList,new TypeToken<List<RoomDTO>>() {}.getType());

        return Response.builder()
                .status(200)
                .message("success")
                .rooms(roomDTOList)
                .build();
    }





    private void validateRoomNumber(Integer roomNumber) {
        if (roomNumber <= 0)
            throw new NameValueRequiredException("roomNumber must be greater than 0");
    }

    private void validatePrice(BigDecimal price) {
        if (price.compareTo(BigDecimal.ZERO) <= 0)
            throw new NameValueRequiredException("pricePerNight must be greater than 0");
    }

    private void validateCapacity(Integer capacity) {
        if (capacity <= 0)
            throw new NameValueRequiredException("capacity must be at least 1");
    }

    //save image to backend folder

//    private String saveImage(MultipartFile imageFile){
//        if (!imageFile.getContentType().startsWith("image/")){
//            throw new IllegalArgumentException("Only Image files are allowed");
//        }
//
//        //Create directory to store image if it doesn exist
//        File directory = new File(IMAGE_DIRECTORY);
//
//        if (!directory.exists()){
//            directory.mkdir();
//        }
//        //Generate uniwue file name for the image
//        String uniqueFileName = UUID.randomUUID() + "_" + imageFile.getOriginalFilename();
//        //get the absolute path of the image
//        String  imagePath = IMAGE_DIRECTORY + uniqueFileName;
//
//        try {
//            File destinationFile = new File(imagePath);
//            imageFile.transferTo(destinationFile);
//        }catch (Exception ex){
//            throw  new IllegalArgumentException(ex.getMessage());
//        }
//
//        return imagePath;
//
//    }


    //save image to frontend folder
    private String saveImageToFrontend(MultipartFile imageFile){
        if (!imageFile.getContentType().startsWith("image/")){
            throw new IllegalArgumentException("Only Image files are allowed");
        }

        //Create directory to store image if it doesn exist
        File directory = new File(IMAGE_DIRECTORY_FRONTEND);

        if (!directory.exists()){
            directory.mkdir();
        }
        //Generate uniwue file name for the image
        String uniqueFileName = UUID.randomUUID() + "_" + imageFile.getOriginalFilename();
        //get the absolute path of the image
        String  imagePath = IMAGE_DIRECTORY_FRONTEND + uniqueFileName;

        try {
            File destinationFile = new File(imagePath);
            imageFile.transferTo(destinationFile);
        }catch (Exception ex){
            throw  new IllegalArgumentException(ex.getMessage());
        }

        return "/rooms/" + uniqueFileName;

    }
}

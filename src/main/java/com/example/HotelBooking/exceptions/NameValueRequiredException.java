package com.example.HotelBooking.exceptions;

public class NameValueRequiredException extends IllegalArgumentException {
    public NameValueRequiredException(String message) {
        super(message);
    }
}

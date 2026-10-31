package com.shelfline.exception;

/** JSON body returned for every error. */
public record ErrorResponse(int status, String error, String message) {
}

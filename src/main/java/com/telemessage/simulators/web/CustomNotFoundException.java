package com.telemessage.simulators.web;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CustomNotFoundException extends WebApplicationException {


    /**
     * Create a HTTP 404 (Not Found) exception.
     */
    public CustomNotFoundException() {
        super(Response.Status.NOT_FOUND);
    }

    /**
     * Create a HTTP 404 (Not Found) exception.
     *
     * @param message the String that is the entity of the 404 response.
     */
    public CustomNotFoundException(String message) {
        super(Response.status(Response.Status.NOT_FOUND).
                entity(message).type("application/json").build());
    }
}

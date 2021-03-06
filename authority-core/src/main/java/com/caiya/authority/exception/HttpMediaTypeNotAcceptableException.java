package com.caiya.authority.exception;

import com.caiya.authority.core.MediaType;

import java.util.List;


/**
 * Exception thrown when the request handler cannot generate a response that is acceptable by the client.
 *
 * @author Arjen Poutsma
 * @since 3.0
 */
@SuppressWarnings("serial")
public class HttpMediaTypeNotAcceptableException extends HttpMediaTypeException {

    /**
     * Create a new HttpMediaTypeNotAcceptableException.
     *
     * @param message the exception message
     */
    public HttpMediaTypeNotAcceptableException(String message) {
        super(message);
    }

    /**
     * Create a new HttpMediaTypeNotSupportedException.
     *
     * @param supportedMediaTypes the list of supported media types
     */
    public HttpMediaTypeNotAcceptableException(List<MediaType> supportedMediaTypes) {
        super("Could not find acceptable representation", supportedMediaTypes);
    }

}

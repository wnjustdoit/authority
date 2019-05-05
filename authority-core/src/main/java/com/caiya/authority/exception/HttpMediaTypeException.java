package com.caiya.authority.exception;

import com.caiya.authority.core.MediaType;

import java.util.Collections;
import java.util.List;
import javax.servlet.ServletException;

/**
 * Abstract base for exceptions related to media types. Adds a list of supported {@link MediaType MediaTypes}.
 *
 * @author Arjen Poutsma
 * @since 3.0
 */
@SuppressWarnings("serial")
public abstract class HttpMediaTypeException extends ServletException {

    private final List<MediaType> supportedMediaTypes;


    /**
     * Create a new HttpMediaTypeException.
     *
     * @param message the exception message
     */
    protected HttpMediaTypeException(String message) {
        super(message);
        this.supportedMediaTypes = Collections.emptyList();
    }

    /**
     * Create a new HttpMediaTypeException with a list of supported media types.
     *
     * @param supportedMediaTypes the list of supported media types
     */
    protected HttpMediaTypeException(String message, List<MediaType> supportedMediaTypes) {
        super(message);
        this.supportedMediaTypes = Collections.unmodifiableList(supportedMediaTypes);
    }


    /**
     * Return the list of supported media types.
     */
    public List<MediaType> getSupportedMediaTypes() {
        return this.supportedMediaTypes;
    }

}

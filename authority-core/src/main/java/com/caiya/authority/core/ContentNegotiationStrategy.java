package com.caiya.authority.core;

import com.caiya.authority.exception.HttpMediaTypeNotAcceptableException;

import javax.servlet.http.HttpServletRequest;
import java.util.List;


/**
 * A strategy for resolving the requested media types for a request.
 *
 * @author Rossen Stoyanchev
 * @since 3.2
 */
public interface ContentNegotiationStrategy {

    /**
     * Resolve the given request to a list of media types. The returned list is
     * ordered by specificity first and by quality parameter second.
     * @param webRequest the current request
     * @return the requested media types or an empty list (never {@code null})
     * @throws HttpMediaTypeNotAcceptableException if the requested media
     * types cannot be parsed
     */
    List<MediaType> resolveMediaTypes(HttpServletRequest webRequest)
            throws HttpMediaTypeNotAcceptableException;

}

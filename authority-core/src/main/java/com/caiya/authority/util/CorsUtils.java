package com.caiya.authority.util;

import com.caiya.authority.core.HttpMethod;

import javax.servlet.http.HttpServletRequest;

/**
 * Utility class for CORS request handling based on the
 * <a href="http://www.w3.org/TR/cors/">CORS W3C recommandation</a>.
 *
 * @author Sebastien Deleuze
 * @since 4.2
 */
public abstract class CorsUtils {

    /**
     * Returns {@code true} if the request is a valid CORS one.
     */
    public static boolean isCorsRequest(HttpServletRequest request) {
        return (request.getHeader("Origin") != null);
    }

    /**
     * Returns {@code true} if the request is a valid CORS pre-flight one.
     */
    public static boolean isPreFlightRequest(HttpServletRequest request) {
        return (isCorsRequest(request) && HttpMethod.OPTIONS.matches(request.getMethod()) &&
                request.getHeader("Access-Control-Request-Method") != null);
    }

}

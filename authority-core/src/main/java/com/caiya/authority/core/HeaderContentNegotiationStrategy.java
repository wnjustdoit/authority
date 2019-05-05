package com.caiya.authority.core;

import com.caiya.authority.exception.HttpMediaTypeNotAcceptableException;
import com.mamaqunaer.common.util.ObjectUtils;
import com.mamaqunaer.common.util.StringUtils;

import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;


/**
 * A {@code ContentNegotiationStrategy} that checks the 'Accept' request header.
 *
 * @author Rossen Stoyanchev
 * @author Juergen Hoeller
 * @since 3.2
 */
public class HeaderContentNegotiationStrategy implements ContentNegotiationStrategy {

    /**
     * {@inheritDoc}
     * @throws HttpMediaTypeNotAcceptableException if the 'Accept' header cannot be parsed
     */
    @Override
    public List<MediaType> resolveMediaTypes(HttpServletRequest request)
            throws HttpMediaTypeNotAcceptableException {

        String[] headerValueArray = getHeaderValues(request, "Accept");
        if (headerValueArray == null) {
            return Collections.<MediaType>emptyList();
        }

        List<String> headerValues = Arrays.asList(headerValueArray);
        try {
            List<MediaType> mediaTypes = MediaType.parseMediaTypes(headerValues);
            MediaType.sortBySpecificityAndQuality(mediaTypes);
            return mediaTypes;
        }
        catch (InvalidMediaTypeException ex) {
            throw new HttpMediaTypeNotAcceptableException(
                    "Could not parse 'Accept' header " + headerValues + ": " + ex.getMessage());
        }
    }

    private String[] getHeaderValues(HttpServletRequest request, String headerName) {
        String[] headerValues = StringUtils.toStringArray(request.getHeaders(headerName));
        return (!ObjectUtils.isEmpty(headerValues) ? headerValues : null);
    }

}

package com.caiya.authority.core;

import com.caiya.authority.exception.HttpMediaTypeNotAcceptableException;
import com.mamaqunaer.common.util.StringUtils;

import javax.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.List;
import java.util.Map;


/**
 * Base class for {@code ContentNegotiationStrategy} implementations with the
 * steps to resolve a request to media types.
 *
 * <p>First a key (e.g. "json", "pdf") must be extracted from the request (e.g.
 * file extension, query param). The key must then be resolved to media type(s)
 * through the base class {@link MappingMediaTypeFileExtensionResolver} which
 * stores such mappings.
 *
 * <p>The method {@link #handleNoMatch} allow sub-classes to plug in additional
 * ways of looking up media types (e.g. through the Java Activation framework,
 * or {@link javax.servlet.ServletContext#getMimeType}. Media types resolved
 * via base classes are then added to the base class
 * {@link MappingMediaTypeFileExtensionResolver}, i.e. cached for new lookups.
 *
 * @author Rossen Stoyanchev
 * @since 3.2
 */
public abstract class AbstractMappingContentNegotiationStrategy extends MappingMediaTypeFileExtensionResolver
        implements ContentNegotiationStrategy {

    /**
     * Create an instance with the given map of file extensions and media types.
     */
    public AbstractMappingContentNegotiationStrategy(Map<String, MediaType> mediaTypes) {
        super(mediaTypes);
    }


    @Override
    public List<MediaType> resolveMediaTypes(HttpServletRequest webRequest)
            throws HttpMediaTypeNotAcceptableException {

        return resolveMediaTypeKey(webRequest, getMediaTypeKey(webRequest));
    }

    /**
     * An alternative to {@link //#resolveMediaTypes(NativeWebRequest)} that accepts
     * an already extracted key.
     * @since 3.2.16
     */
    public List<MediaType> resolveMediaTypeKey(HttpServletRequest webRequest, String key)
            throws HttpMediaTypeNotAcceptableException {

        if (StringUtils.hasText(key)) {
            MediaType mediaType = lookupMediaType(key);
            if (mediaType != null) {
                handleMatch(key, mediaType);
                return Collections.singletonList(mediaType);
            }
            mediaType = handleNoMatch(webRequest, key);
            if (mediaType != null) {
                addMapping(key, mediaType);
                return Collections.singletonList(mediaType);
            }
        }
        return Collections.emptyList();
    }


    /**
     * Extract a key from the request to use to look up media types.
     * @return the lookup key or {@code null}.
     */
    protected abstract String getMediaTypeKey(HttpServletRequest request);

    /**
     * Override to provide handling when a key is successfully resolved via
     * {@link #lookupMediaType}.
     */
    protected void handleMatch(String key, MediaType mediaType) {
    }

    /**
     * Override to provide handling when a key is not resolved via.
     * {@link #lookupMediaType}. Sub-classes can take further steps to
     * determine the media type(s). If a MediaType is returned from
     * this method it will be added to the cache in the base class.
     */
    protected MediaType handleNoMatch(HttpServletRequest request, String key)
            throws HttpMediaTypeNotAcceptableException {

        return null;
    }

}
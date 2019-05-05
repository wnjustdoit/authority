package com.caiya.authority.core;

import com.caiya.authority.util.AntPathMatcher;
import com.caiya.authority.util.PathMatcher;
import com.caiya.authority.util.UrlPathHelper;
import com.mamaqunaer.common.util.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;

/**
 * Abstract base class for {@link HandlerMapping}
 * implementations. Supports ordering, a default handler, handler interceptors,
 * including handler interceptors mapped by path patterns.
 * <p>
 * <p>Note: This base class does <i>not</i> support exposure of the
 * {@link #PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE}. Support for this attribute
 * is up to concrete subclasses, typically based on request URL mappings.
 *
 * @author Juergen Hoeller
 * @author Rossen Stoyanchev
 * @see #getHandlerInternal
 * @see #setDefaultHandler
 * @see #setAlwaysUseFullPath
 * @see #setUrlDecode
 * @see AntPathMatcher
 * @since 07.04.2003
 */
public abstract class AbstractHandlerMapping implements HandlerMapping {

    protected final Logger logger = LoggerFactory.getLogger(this.getClass());

    private int order = Integer.MAX_VALUE;  // default: same as non-Ordered

    private Object defaultHandler;

    private UrlPathHelper urlPathHelper = new UrlPathHelper();

    private PathMatcher pathMatcher = new AntPathMatcher();

    /**
     * Set the default handler for this handler mapping.
     * This handler will be returned if no specific mapping was found.
     * <p>Default is {@code null}, indicating no default handler.
     */
    public void setDefaultHandler(Object defaultHandler) {
        this.defaultHandler = defaultHandler;
    }

    /**
     * Return the default handler for this handler mapping,
     * or {@code null} if none.
     */
    public Object getDefaultHandler() {
        return this.defaultHandler;
    }

    /**
     * Set if URL lookup should always use the full path within the current servlet
     * context. Else, the path within the current servlet mapping is used if applicable
     * (that is, in the case of a ".../*" servlet mapping in web.xml).
     * <p>Default is "false".
     *
     * @see UrlPathHelper#setAlwaysUseFullPath
     */
    public void setAlwaysUseFullPath(boolean alwaysUseFullPath) {
        this.urlPathHelper.setAlwaysUseFullPath(alwaysUseFullPath);
    }

    /**
     * Set if context path and request URI should be URL-decoded. Both are returned
     * <i>undecoded</i> by the Servlet API, in contrast to the servlet path.
     * <p>Uses either the request encoding or the default encoding according
     * to the Servlet spec (ISO-8859-1).
     *
     * @see UrlPathHelper#setUrlDecode
     */
    public void setUrlDecode(boolean urlDecode) {
        this.urlPathHelper.setUrlDecode(urlDecode);
    }

    /**
     * Set if ";" (semicolon) content should be stripped from the request URI.
     * <p>The default value is {@code true}.
     *
     * @see UrlPathHelper#setRemoveSemicolonContent(boolean)
     */
    public void setRemoveSemicolonContent(boolean removeSemicolonContent) {
        this.urlPathHelper.setRemoveSemicolonContent(removeSemicolonContent);
    }

    /**
     * Set the UrlPathHelper to use for resolution of lookup paths.
     * <p>Use this to override the default UrlPathHelper with a custom subclass,
     * or to share common UrlPathHelper settings across multiple HandlerMappings
     * and MethodNameResolvers.
     */
    public void setUrlPathHelper(UrlPathHelper urlPathHelper) {
        Assert.notNull(urlPathHelper, "UrlPathHelper must not be null");
        this.urlPathHelper = urlPathHelper;
    }

    /**
     * Return the UrlPathHelper implementation to use for resolution of lookup paths.
     */
    public UrlPathHelper getUrlPathHelper() {
        return urlPathHelper;
    }

    /**
     * Set the PathMatcher implementation to use for matching URL paths
     * against registered URL patterns. Default is AntPathMatcher.
     *
     * @see AntPathMatcher
     */
    public void setPathMatcher(PathMatcher pathMatcher) {
        Assert.notNull(pathMatcher, "PathMatcher must not be null");
        this.pathMatcher = pathMatcher;
    }

    /**
     * Return the PathMatcher implementation to use for matching URL paths
     * against registered URL patterns.
     */
    public PathMatcher getPathMatcher() {
        return this.pathMatcher;
    }


    /**
     * Look up a handler for the given request, returning {@code null} if no
     * specific one is found. This method is called by {@link //#getHandler};
     * a {@code null} return value will lead to the default handler, if one is set.
     * <p>On CORS pre-flight requests this method should return a match not for
     * the pre-flight request but for the expected actual request based on the URL
     * path, the HTTP methods from the "Access-Control-Request-Method" header, and
     * the headers from the "Access-Control-Request-Headers" header thus allowing
     * the CORS configuration to be obtained via {@link //#getCorsConfigurations},
     * <p>Note: This method may also return a pre-built {@link //HandlerExecutionChain},
     * combining a handler object with dynamically determined interceptors.
     * Statically specified interceptors will get merged into such an existing chain.
     *
     * @param request current HTTP request
     * @return the corresponding handler instance, or {@code null} if none found
     * @throws Exception if there is an internal error
     */
    protected abstract Object getHandlerInternal(HttpServletRequest request) throws Exception;


}

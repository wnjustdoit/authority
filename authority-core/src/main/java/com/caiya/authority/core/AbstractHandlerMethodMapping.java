package com.caiya.authority.core;

import com.caiya.authority.util.CorsUtils;
import com.mamaqunaer.common.util.Assert;
import com.mamaqunaer.common.util.ClassUtils;
import com.mamaqunaer.common.util.LinkedMultiValueMap;
import com.mamaqunaer.common.util.MultiValueMap;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;


/**
 * Abstract base class for {@link HandlerMapping} implementations that define
 * a mapping between a request and a {@link HandlerMethod}.
 *
 * <p>For each registered handler method, a unique mapping is maintained with
 * subclasses defining the details of the mapping type {@code <T>}.
 *
 * @author Arjen Poutsma
 * @author Rossen Stoyanchev
 * @author Juergen Hoeller
 * @since 3.1
 * @param <T> The mapping for a {@link HandlerMethod} containing the conditions
 * needed to match the handler method to incoming request.
 */
public abstract class AbstractHandlerMethodMapping<T> extends AbstractHandlerMapping {

    /**
     * Bean name prefix for target beans behind scoped proxies. Used to exclude those
     * targets from handler method detection, in favor of the corresponding proxies.
     * <p>We're not checking the autowire-candidate status here, which is how the
     * proxy target filtering problem is being handled at the autowiring level,
     * since autowire-candidate may have been turned to {@code false} for other
     * reasons, while still expecting the bean to be eligible for handler methods.
     * <p>Originally defined in {@link //org.springframework.aop.scope.ScopedProxyUtils}
     * but duplicated here to avoid a hard dependency on the spring-aop module.
     */
    private static final String SCOPED_TARGET_NAME_PREFIX = "scopedTarget.";

    private static final HandlerMethod PREFLIGHT_AMBIGUOUS_MATCH =
            new HandlerMethod(new EmptyHandler(), ClassUtils.getMethod(EmptyHandler.class, "handle"));

    private static final CorsConfiguration ALLOW_CORS_CONFIG = new CorsConfiguration();

    static {
        ALLOW_CORS_CONFIG.addAllowedOrigin("*");
        ALLOW_CORS_CONFIG.addAllowedMethod("*");
        ALLOW_CORS_CONFIG.addAllowedHeader("*");
        ALLOW_CORS_CONFIG.setAllowCredentials(true);
    }


    private boolean detectHandlerMethodsInAncestorContexts = false;

    private HandlerMethodMappingNamingStrategy<T> namingStrategy;

    private final MappingRegistry mappingRegistry = new MappingRegistry();


    /**
     * Whether to detect handler methods in beans in ancestor ApplicationContexts.
     * <p>Default is "false": Only beans in the current ApplicationContext are
     * considered, i.e. only in the context that this HandlerMapping itself
     * is defined in (typically the current DispatcherServlet's context).
     * <p>Switch this flag on to detect handler beans in ancestor contexts
     * (typically the Spring root WebApplicationContext) as well.
     */
    public void setDetectHandlerMethodsInAncestorContexts(boolean detectHandlerMethodsInAncestorContexts) {
        this.detectHandlerMethodsInAncestorContexts = detectHandlerMethodsInAncestorContexts;
    }

    /**
     * Configure the naming strategy to use for assigning a default name to every
     * mapped handler method.
     * <p>The default naming strategy is based on the capital letters of the
     * class name followed by "#" and then the method name, e.g. "TC#getFoo"
     * for a class named TestController with method getFoo.
     */
    public void setHandlerMethodMappingNamingStrategy(HandlerMethodMappingNamingStrategy<T> namingStrategy) {
        this.namingStrategy = namingStrategy;
    }

    /**
     * Return the configured naming strategy or {@code null}.
     */
    public HandlerMethodMappingNamingStrategy<T> getNamingStrategy() {
        return this.namingStrategy;
    }

    /**
     * Return a (read-only) map with all mappings and HandlerMethod's.
     */
    public Map<T, HandlerMethod> getHandlerMethods() {
        this.mappingRegistry.acquireReadLock();
        try {
            return Collections.unmodifiableMap(this.mappingRegistry.getMappings());
        }
        finally {
            this.mappingRegistry.releaseReadLock();
        }
    }

    /**
     * Return the handler methods for the given mapping name.
     * @param mappingName the mapping name
     * @return a list of matching HandlerMethod's or {@code null}; the returned
     * list will never be modified and is safe to iterate.
     * @see #setHandlerMethodMappingNamingStrategy
     */
    public List<HandlerMethod> getHandlerMethodsForMappingName(String mappingName) {
        return this.mappingRegistry.getHandlerMethodsByMappingName(mappingName);
    }

    /**
     * Return the internal mapping registry. Provided for testing purposes.
     */
    MappingRegistry getMappingRegistry() {
        return this.mappingRegistry;
    }

    /**
     * Register the given mapping.
     * <p>This method may be invoked at runtime after initialization has completed.
     * @param mapping the mapping for the handler method
     * @param handler the handler
     * @param method the method
     */
    public void registerMapping(T mapping, Object handler, Method method) {
        this.mappingRegistry.register(mapping, handler, method);
    }

    /**
     * Un-register the given mapping.
     * <p>This method may be invoked at runtime after initialization has completed.
     * @param mapping the mapping to unregister
     */
    public void unregisterMapping(T mapping) {
        this.mappingRegistry.unregister(mapping);
    }

    /**
     * Register a handler method and its unique mapping. Invoked at startup for
     * each detected handler method.
     * @param handler the bean name of the handler or the handler instance
     * @param method the method to register
     * @param mapping the mapping conditions associated with the handler method
     * @throws IllegalStateException if another method was already registered
     * under the same mapping
     */
    protected void registerHandlerMethod(Object handler, Method method, T mapping) {
        this.mappingRegistry.register(mapping, handler, method);
    }

    /**
     * Create the HandlerMethod instance.
     * @param handler either a bean name or an actual handler instance
     * @param method the target method
     * @return the created HandlerMethod
     */
    protected HandlerMethod createHandlerMethod(Object handler, Method method) {
        return new HandlerMethod(handler, method);
    }

    /**
     * Extract and return the CORS configuration for the mapping.
     */
    protected CorsConfiguration initCorsConfiguration(Object handler, Method method, T mapping) {
        return null;
    }


    // Handler method lookup

    /**
     * Look up a handler method for the given request.
     */
    @Override
    protected HandlerMethod getHandlerInternal(HttpServletRequest request) throws Exception {
        String lookupPath = getUrlPathHelper().getLookupPathForRequest(request);
        if (logger.isDebugEnabled()) {
            logger.debug("Looking up handler method for path " + lookupPath);
        }
        this.mappingRegistry.acquireReadLock();
        try {
            HandlerMethod handlerMethod = lookupHandlerMethod(lookupPath, request);
            if (logger.isDebugEnabled()) {
                if (handlerMethod != null) {
                    logger.debug("Returning handler method [" + handlerMethod + "]");
                }
                else {
                    logger.debug("Did not find handler method for [" + lookupPath + "]");
                }
            }
            return (handlerMethod != null ? handlerMethod.createWithResolvedBean() : null);
        }
        finally {
            this.mappingRegistry.releaseReadLock();
        }
    }

    /**
     * user define
     *
     * @param request
     * @return
     * @throws Exception
     */
    public Match getBestMatch(HttpServletRequest request) throws Exception {
        String lookupPath = getUrlPathHelper().getLookupPathForRequest(request);
        if (logger.isDebugEnabled()) {
            logger.debug("Looking up handler method for path " + lookupPath);
        }
        this.mappingRegistry.acquireReadLock();
        try {
            Match bestMatch = lookupBestMatch(lookupPath, request);
            if (logger.isDebugEnabled()) {
                if (bestMatch != null) {
                    logger.debug("Returning bestMatch method [" + bestMatch + "]");
                } else {
                    logger.debug("Did not find bestMatch method for [" + lookupPath + "]");
                }
            }
            return bestMatch;
        } finally {
            this.mappingRegistry.releaseReadLock();
        }
    }

    public HandlerMethod getHandler(HttpServletRequest request) throws Exception {
        return getHandlerInternal(request);
    }

    /**
     * Look up the best-matching handler method for the current request.
     * If multiple matches are found, the best match is selected.
     * @param lookupPath mapping lookup path within the current servlet mapping
     * @param request the current request
     * @return the best-matching handler method, or {@code null} if no match
     * @see #handleMatch(Object, String, HttpServletRequest)
     * @see #handleNoMatch(Set, String, HttpServletRequest)
     */
    protected HandlerMethod lookupHandlerMethod(String lookupPath, HttpServletRequest request) throws Exception {
        List<Match> matches = new ArrayList<>();
        List<T> directPathMatches = this.mappingRegistry.getMappingsByUrl(lookupPath);
        if (directPathMatches != null) {
            addMatchingMappings(directPathMatches, matches, request);
        }
        if (matches.isEmpty()) {
            // No choice but to go through all mappings...
            addMatchingMappings(this.mappingRegistry.getMappings().keySet(), matches, request);
        }

        if (!matches.isEmpty()) {
            Comparator<Match> comparator = new MatchComparator(getMappingComparator(request));
            Collections.sort(matches, comparator);
            if (logger.isTraceEnabled()) {
                logger.trace("Found " + matches.size() + " matching mapping(s) for [" +
                        lookupPath + "] : " + matches);
            }
            Match bestMatch = matches.get(0);
            if (matches.size() > 1) {
                if (CorsUtils.isPreFlightRequest(request)) {
                    return PREFLIGHT_AMBIGUOUS_MATCH;
                }
                Match secondBestMatch = matches.get(1);
                if (comparator.compare(bestMatch, secondBestMatch) == 0) {
                    Method m1 = bestMatch.handlerMethod.getMethod();
                    Method m2 = secondBestMatch.handlerMethod.getMethod();
                    throw new IllegalStateException("Ambiguous handler methods mapped for HTTP path '" +
                            request.getRequestURL() + "': {" + m1 + ", " + m2 + "}");
                }
            }
            handleMatch(bestMatch.mapping, lookupPath, request);
            return bestMatch.handlerMethod;
        }
        else {
            return handleNoMatch(this.mappingRegistry.getMappings().keySet(), lookupPath, request);
        }
    }

    /**
     * user define
     *
     * @param lookupPath
     * @param request
     * @return
     * @throws Exception
     */
    protected Match lookupBestMatch(String lookupPath, HttpServletRequest request) throws Exception {
        List<Match> matches = new ArrayList<Match>();
        List<T> directPathMatches = this.mappingRegistry.getMappingsByUrl(lookupPath);
        if (directPathMatches != null) {
            addMatchingMappings(directPathMatches, matches, request);
        }
        if (matches.isEmpty()) {
            // No choice but to go through all mappings...
            addMatchingMappings(this.mappingRegistry.getMappings().keySet(), matches, request);
        }

        if (!matches.isEmpty()) {
            Comparator<Match> comparator = new MatchComparator(getMappingComparator(request));
            Collections.sort(matches, comparator);
            if (logger.isTraceEnabled()) {
                logger.trace("Found " + matches.size() + " matching mapping(s) for [" +
                        lookupPath + "] : " + matches);
            }
            Match bestMatch = matches.get(0);
            if (matches.size() > 1) {
                if (CorsUtils.isPreFlightRequest(request)) {
                    return null;
                }
                Match secondBestMatch = matches.get(1);
                if (comparator.compare(bestMatch, secondBestMatch) == 0) {
                    Method m1 = bestMatch.handlerMethod.getMethod();
                    Method m2 = secondBestMatch.handlerMethod.getMethod();
                    throw new IllegalStateException("Ambiguous handler methods mapped for HTTP path '" +
                            request.getRequestURL() + "': {" + m1 + ", " + m2 + "}");
                }
            }
            handleMatch(bestMatch.mapping, lookupPath, request);
            return bestMatch;
        } else {
            return null;
        }
    }

    private void addMatchingMappings(Collection<T> mappings, List<Match> matches, HttpServletRequest request) {
        for (T mapping : mappings) {
            T match = getMatchingMapping(mapping, request);
            if (match != null) {
                matches.add(new Match(match, this.mappingRegistry.getMappings().get(mapping)));
            }
        }
    }

    /**
     * Invoked when a matching mapping is found.
     * @param mapping the matching mapping
     * @param lookupPath mapping lookup path within the current servlet mapping
     * @param request the current request
     */
    protected void handleMatch(T mapping, String lookupPath, HttpServletRequest request) {
        request.setAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE, lookupPath);
    }

    /**
     * Invoked when no matching mapping is not found.
     * @param mappings all registered mappings
     * @param lookupPath mapping lookup path within the current servlet mapping
     * @param request the current request
     * @throws ServletException in case of errors
     */
    protected HandlerMethod handleNoMatch(Set<T> mappings, String lookupPath, HttpServletRequest request)
            throws Exception {

        return null;
    }


    // Abstract template methods

    /**
     * Whether the given type is a handler with handler methods.
     * @param beanType the type of the bean being checked
     * @return "true" if this a handler type, "false" otherwise.
     */
    protected abstract boolean isHandler(Class<?> beanType);

    /**
     * Provide the mapping for a handler method. A method for which no
     * mapping can be provided is not a handler method.
     * @param method the method to provide a mapping for
     * @param handlerType the handler type, possibly a sub-type of the method's
     * declaring class
     * @return the mapping, or {@code null} if the method is not mapped
     */
    protected abstract T getMappingForMethod(Method method, Class<?> handlerType);

    /**
     * Extract and return the URL paths contained in a mapping.
     */
    protected abstract Set<String> getMappingPathPatterns(T mapping);

    /**
     * Check if a mapping matches the current request and return a (potentially
     * new) mapping with conditions relevant to the current request.
     * @param mapping the mapping to get a match for
     * @param request the current HTTP servlet request
     * @return the match, or {@code null} if the mapping doesn't match
     */
    protected abstract T getMatchingMapping(T mapping, HttpServletRequest request);

    /**
     * Return a comparator for sorting matching mappings.
     * The returned comparator should sort 'better' matches higher.
     * @param request the current request
     * @return the comparator (never {@code null})
     */
    protected abstract Comparator<T> getMappingComparator(HttpServletRequest request);


    /**
     * A registry that maintains all mappings to handler methods, exposing methods
     * to perform lookups and providing concurrent access.
     *
     * <p>Package-private for testing purposes.
     */
    class MappingRegistry {

        private final Map<T, MappingRegistration<T>> registry = new HashMap<T, MappingRegistration<T>>();

        private final Map<T, HandlerMethod> mappingLookup = new LinkedHashMap<>();

        private final MultiValueMap<String, T> urlLookup = new LinkedMultiValueMap<>();

        private final Map<String, List<HandlerMethod>> nameLookup =
                new ConcurrentHashMap<String, List<HandlerMethod>>();

        private final Map<HandlerMethod, CorsConfiguration> corsLookup =
                new ConcurrentHashMap<HandlerMethod, CorsConfiguration>();

        private final ReentrantReadWriteLock readWriteLock = new ReentrantReadWriteLock();

        /**
         * Return all mappings and handler methods. Not thread-safe.
         * @see #acquireReadLock()
         */
        public Map<T, HandlerMethod> getMappings() {
            return this.mappingLookup;
        }

        /**
         * Return matches for the given URL path. Not thread-safe.
         * @see #acquireReadLock()
         */
        public List<T> getMappingsByUrl(String urlPath) {
            return this.urlLookup.get(urlPath);
        }

        /**
         * Return handler methods by mapping name. Thread-safe for concurrent use.
         */
        public List<HandlerMethod> getHandlerMethodsByMappingName(String mappingName) {
            return this.nameLookup.get(mappingName);
        }

        /**
         * Return CORS configuration. Thread-safe for concurrent use.
         */
        public CorsConfiguration getCorsConfiguration(HandlerMethod handlerMethod) {
            HandlerMethod original = handlerMethod.getResolvedFromHandlerMethod();
            return this.corsLookup.get(original != null ? original : handlerMethod);
        }

        /**
         * Acquire the read lock when using getMappings and getMappingsByUrl.
         */
        public void acquireReadLock() {
            this.readWriteLock.readLock().lock();
        }

        /**
         * Release the read lock after using getMappings and getMappingsByUrl.
         */
        public void releaseReadLock() {
            this.readWriteLock.readLock().unlock();
        }

        public void register(T mapping, Object handler, Method method) {
            this.readWriteLock.writeLock().lock();
            try {
                HandlerMethod handlerMethod = createHandlerMethod(handler, method);
                assertUniqueMethodMapping(handlerMethod, mapping);

                if (logger.isInfoEnabled()) {
                    logger.info("Mapped \"" + mapping + "\" onto " + handlerMethod);
                }
                this.mappingLookup.put(mapping, handlerMethod);

                List<String> directUrls = getDirectUrls(mapping);
                for (String url : directUrls) {
                    this.urlLookup.add(url, mapping);
                }

                String name = null;
                if (getNamingStrategy() != null) {
                    name = getNamingStrategy().getName(handlerMethod, mapping);
                    addMappingName(name, handlerMethod);
                }

                CorsConfiguration corsConfig = initCorsConfiguration(handler, method, mapping);
                if (corsConfig != null) {
                    this.corsLookup.put(handlerMethod, corsConfig);
                }

                this.registry.put(mapping, new MappingRegistration<T>(mapping, handlerMethod, directUrls, name));
            }
            finally {
                this.readWriteLock.writeLock().unlock();
            }
        }

        private void assertUniqueMethodMapping(HandlerMethod newHandlerMethod, T mapping) {
            HandlerMethod handlerMethod = this.mappingLookup.get(mapping);
            if (handlerMethod != null && !handlerMethod.equals(newHandlerMethod)) {
                throw new IllegalStateException(
                        "Ambiguous mapping. Cannot map '" +	newHandlerMethod.getBean() + "' method \n" +
                                newHandlerMethod + "\nto " + mapping + ": There is already '" +
                                handlerMethod.getBean() + "' bean method\n" + handlerMethod + " mapped.");
            }
        }

        private List<String> getDirectUrls(T mapping) {
            List<String> urls = new ArrayList<String>(1);
            for (String path : getMappingPathPatterns(mapping)) {
                if (!getPathMatcher().isPattern(path)) {
                    urls.add(path);
                }
            }
            return urls;
        }

        private void addMappingName(String name, HandlerMethod handlerMethod) {
            List<HandlerMethod> oldList = this.nameLookup.get(name);
            if (oldList == null) {
                oldList = Collections.<HandlerMethod>emptyList();
            }

            for (HandlerMethod current : oldList) {
                if (handlerMethod.equals(current)) {
                    return;
                }
            }

            if (logger.isTraceEnabled()) {
                logger.trace("Mapping name '" + name + "'");
            }

            List<HandlerMethod> newList = new ArrayList<HandlerMethod>(oldList.size() + 1);
            newList.addAll(oldList);
            newList.add(handlerMethod);
            this.nameLookup.put(name, newList);

            if (newList.size() > 1) {
                if (logger.isTraceEnabled()) {
                    logger.trace("Mapping name clash for handlerMethods " + newList +
                            ". Consider assigning explicit names.");
                }
            }
        }

        public void unregister(T mapping) {
            this.readWriteLock.writeLock().lock();
            try {
                MappingRegistration<T> definition = this.registry.remove(mapping);
                if (definition == null) {
                    return;
                }

                this.mappingLookup.remove(definition.getMapping());

                for (String url : definition.getDirectUrls()) {
                    List<T> list = this.urlLookup.get(url);
                    if (list != null) {
                        list.remove(definition.getMapping());
                        if (list.isEmpty()) {
                            this.urlLookup.remove(url);
                        }
                    }
                }

                removeMappingName(definition);

                this.corsLookup.remove(definition.getHandlerMethod());
            }
            finally {
                this.readWriteLock.writeLock().unlock();
            }
        }

        private void removeMappingName(MappingRegistration<T> definition) {
            String name = definition.getMappingName();
            if (name == null) {
                return;
            }
            HandlerMethod handlerMethod = definition.getHandlerMethod();
            List<HandlerMethod> oldList = this.nameLookup.get(name);
            if (oldList == null) {
                return;
            }
            if (oldList.size() <= 1) {
                this.nameLookup.remove(name);
                return;
            }
            List<HandlerMethod> newList = new ArrayList<HandlerMethod>(oldList.size() - 1);
            for (HandlerMethod current : oldList) {
                if (!current.equals(handlerMethod)) {
                    newList.add(current);
                }
            }
            this.nameLookup.put(name, newList);
        }
    }


    private static class MappingRegistration<T> {

        private final T mapping;

        private final HandlerMethod handlerMethod;

        private final List<String> directUrls;

        private final String mappingName;

        public MappingRegistration(T mapping, HandlerMethod handlerMethod, List<String> directUrls, String mappingName) {
            Assert.notNull(mapping, "Mapping must not be null");
            Assert.notNull(handlerMethod, "HandlerMethod must not be null");
            this.mapping = mapping;
            this.handlerMethod = handlerMethod;
            this.directUrls = (directUrls != null ? directUrls : Collections.<String>emptyList());
            this.mappingName = mappingName;
        }

        public T getMapping() {
            return this.mapping;
        }

        public HandlerMethod getHandlerMethod() {
            return this.handlerMethod;
        }

        public List<String> getDirectUrls() {
            return this.directUrls;
        }

        public String getMappingName() {
            return this.mappingName;
        }
    }


    /**
     * A thin wrapper around a matched HandlerMethod and its mapping, for the purpose of
     * comparing the best match with a comparator in the context of the current request.
     */
    public class Match {

        private final T mapping;

        private final HandlerMethod handlerMethod;

        public Match(T mapping, HandlerMethod handlerMethod) {
            this.mapping = mapping;
            this.handlerMethod = handlerMethod;
        }

        @Override
        public String toString() {
            return this.mapping.toString();
        }
    }


    private class MatchComparator implements Comparator<Match> {

        private final Comparator<T> comparator;

        public MatchComparator(Comparator<T> comparator) {
            this.comparator = comparator;
        }

        @Override
        public int compare(Match match1, Match match2) {
            return this.comparator.compare(match1.mapping, match2.mapping);
        }
    }


    private static class EmptyHandler {

        public void handle() {
            throw new UnsupportedOperationException("Not implemented");
        }
    }

}
package com.caiya.authority.core;

import com.caiya.authority.exception.HttpMediaTypeNotAcceptableException;
import com.caiya.authority.exception.HttpMediaTypeNotSupportedException;
import com.caiya.authority.exception.HttpRequestMethodNotSupportedException;
import com.caiya.authority.exception.UnsatisfiedServletRequestParameterException;
import com.caiya.authority.util.WebUtils;
import com.mamaqunaer.authority.util.*;
import com.mamaqunaer.common.util.CollectionUtils;
import com.mamaqunaer.common.util.MultiValueMap;
import com.mamaqunaer.common.util.StringUtils;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;


/**
 * Abstract base class for classes for which {@link RequestMappingInfo} defines
 * the mapping between a request and a handler method.
 *
 * @author Arjen Poutsma
 * @author Rossen Stoyanchev
 * @since 3.1
 */
public class RequestMappingInfoHandlerMapping extends AbstractHandlerMethodMapping<RequestMappingInfo> {

    private static final Method HTTP_OPTIONS_HANDLE_METHOD = null;


    public RequestMappingInfoHandlerMapping() {
        setHandlerMethodMappingNamingStrategy(new RequestMappingInfoHandlerMethodMappingNamingStrategy());
    }


    /**
     * Get the URL path patterns associated with this {@link RequestMappingInfo}.
     */
    @Override
    protected Set<String> getMappingPathPatterns(RequestMappingInfo info) {
        return info.getPatternsCondition().getPatterns();
    }

    /**
     * Check if the given RequestMappingInfo matches the current request and
     * return a (potentially new) instance with conditions that match the
     * current request -- for example with a subset of URL patterns.
     *
     * @return an info in case of a match; or {@code null} otherwise.
     */
    @Override
    protected RequestMappingInfo getMatchingMapping(RequestMappingInfo info, HttpServletRequest request) {
        return info.getMatchingCondition(request);
    }

    /**
     * Provide a Comparator to sort RequestMappingInfos matched to a request.
     */
    @Override
    protected Comparator<RequestMappingInfo> getMappingComparator(final HttpServletRequest request) {
        return (info1, info2) -> info1.compareTo(info2, request);
    }

    /**
     * Expose URI template variables, matrix variables, and producible media types in the request.
     *
     * @see HandlerMapping#URI_TEMPLATE_VARIABLES_ATTRIBUTE
     * @see HandlerMapping#MATRIX_VARIABLES_ATTRIBUTE
     * @see HandlerMapping#PRODUCIBLE_MEDIA_TYPES_ATTRIBUTE
     */
    @Override
    protected void handleMatch(RequestMappingInfo info, String lookupPath, HttpServletRequest request) {
        super.handleMatch(info, lookupPath, request);

        String bestPattern;
        Map<String, String> uriVariables;
        Map<String, String> decodedUriVariables;

        Set<String> patterns = info.getPatternsCondition().getPatterns();
        if (patterns.isEmpty()) {
            bestPattern = lookupPath;
            uriVariables = Collections.emptyMap();
            decodedUriVariables = Collections.emptyMap();
        } else {
            bestPattern = patterns.iterator().next();
            uriVariables = getPathMatcher().extractUriTemplateVariables(bestPattern, lookupPath);
            decodedUriVariables = getUrlPathHelper().decodePathVariables(request, uriVariables);
        }

        request.setAttribute(BEST_MATCHING_PATTERN_ATTRIBUTE, bestPattern);
        request.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, decodedUriVariables);

        if (isMatrixVariableContentAvailable()) {
            Map<String, MultiValueMap<String, String>> matrixVars = extractMatrixVariables(request, uriVariables);
            request.setAttribute(HandlerMapping.MATRIX_VARIABLES_ATTRIBUTE, matrixVars);
        }

        if (!info.getProducesCondition().getProducibleMediaTypes().isEmpty()) {
            Set<MediaType> mediaTypes = info.getProducesCondition().getProducibleMediaTypes();
            request.setAttribute(PRODUCIBLE_MEDIA_TYPES_ATTRIBUTE, mediaTypes);
        }
    }

    private boolean isMatrixVariableContentAvailable() {
        return !getUrlPathHelper().shouldRemoveSemicolonContent();
    }

    private Map<String, MultiValueMap<String, String>> extractMatrixVariables(
            HttpServletRequest request, Map<String, String> uriVariables) {

        Map<String, MultiValueMap<String, String>> result = new LinkedHashMap<String, MultiValueMap<String, String>>();
        for (Entry<String, String> uriVar : uriVariables.entrySet()) {
            String uriVarValue = uriVar.getValue();

            int equalsIndex = uriVarValue.indexOf('=');
            if (equalsIndex == -1) {
                continue;
            }

            String matrixVariables;

            int semicolonIndex = uriVarValue.indexOf(';');
            if ((semicolonIndex == -1) || (semicolonIndex == 0) || (equalsIndex < semicolonIndex)) {
                matrixVariables = uriVarValue;
            } else {
                matrixVariables = uriVarValue.substring(semicolonIndex + 1);
                uriVariables.put(uriVar.getKey(), uriVarValue.substring(0, semicolonIndex));
            }

            MultiValueMap<String, String> vars = WebUtils.parseMatrixVariables(matrixVariables);
            result.put(uriVar.getKey(), getUrlPathHelper().decodeMatrixVariables(request, vars));
        }
        return result;
    }

    /**
     * Iterate all RequestMappingInfo's once again, look if any match by URL at
     * least and raise exceptions according to what doesn't match.
     *
     * @throws HttpRequestMethodNotSupportedException if there are matches by URL
     *                                                but not by HTTP method
     * @throws HttpMediaTypeNotAcceptableException    if there are matches by URL
     *                                                but not by consumable/producible media types
     */
    @Override
    protected HandlerMethod handleNoMatch(Set<RequestMappingInfo> infos, String lookupPath,
                                          HttpServletRequest request) throws ServletException {

        PartialMatchHelper helper = new PartialMatchHelper(infos, request);

        if (helper.isEmpty()) {
            return null;
        }

        if (helper.hasMethodsMismatch()) {
            Set<String> methods = helper.getAllowedMethods();
            if (HttpMethod.OPTIONS.matches(request.getMethod())) {
                return new HandlerMethod(null, HTTP_OPTIONS_HANDLE_METHOD);
            }
            throw new HttpRequestMethodNotSupportedException(request.getMethod(), methods);
        }

        if (helper.hasConsumesMismatch()) {
            Set<MediaType> mediaTypes = helper.getConsumableMediaTypes();
            MediaType contentType = null;
            if (StringUtils.hasLength(request.getContentType())) {
                try {
                    contentType = MediaType.parseMediaType(request.getContentType());
                } catch (InvalidMediaTypeException ex) {
                    throw new HttpMediaTypeNotSupportedException(ex.getMessage());
                }
            }
            throw new HttpMediaTypeNotSupportedException(contentType, new ArrayList<MediaType>(mediaTypes));
        }

        if (helper.hasProducesMismatch()) {
            Set<MediaType> mediaTypes = helper.getProducibleMediaTypes();
            throw new HttpMediaTypeNotAcceptableException(new ArrayList<MediaType>(mediaTypes));
        }

        if (helper.hasParamsMismatch()) {
            List<String[]> conditions = helper.getParamConditions();
            throw new UnsatisfiedServletRequestParameterException(conditions, request.getParameterMap());
        }

        return null;
    }

    @Override
    protected boolean isHandler(Class<?> beanType) {
        return false;
    }

    @Override
    protected RequestMappingInfo getMappingForMethod(Method method, Class<?> handlerType) {
        return null;
    }


    /**
     * Aggregate all partial matches and expose methods checking across them.
     */
    private static class PartialMatchHelper {

        private final List<PartialMatch> partialMatches = new ArrayList<PartialMatch>();

        public PartialMatchHelper(Set<RequestMappingInfo> infos, HttpServletRequest request) {
            for (RequestMappingInfo info : infos) {
                if (info.getPatternsCondition().getMatchingCondition(request) != null) {
                    this.partialMatches.add(new PartialMatch(info, request));
                }
            }
        }

        /**
         * Whether there any partial matches.
         */
        public boolean isEmpty() {
            return this.partialMatches.isEmpty();
        }

        /**
         * Any partial matches for "methods"?
         */
        public boolean hasMethodsMismatch() {
            for (PartialMatch match : this.partialMatches) {
                if (match.hasMethodsMatch()) {
                    return false;
                }
            }
            return true;
        }

        /**
         * Any partial matches for "methods" and "consumes"?
         */
        public boolean hasConsumesMismatch() {
            for (PartialMatch match : this.partialMatches) {
                if (match.hasConsumesMatch()) {
                    return false;
                }
            }
            return true;
        }

        /**
         * Any partial matches for "methods", "consumes", and "produces"?
         */
        public boolean hasProducesMismatch() {
            for (PartialMatch match : this.partialMatches) {
                if (match.hasProducesMatch()) {
                    return false;
                }
            }
            return true;
        }

        /**
         * Any partial matches for "methods", "consumes", "produces", and "params"?
         */
        public boolean hasParamsMismatch() {
            for (PartialMatch match : this.partialMatches) {
                if (match.hasParamsMatch()) {
                    return false;
                }
            }
            return true;
        }

        /**
         * Return declared HTTP methods.
         */
        public Set<String> getAllowedMethods() {
            Set<String> result = new LinkedHashSet<String>();
            for (PartialMatch match : this.partialMatches) {
                for (RequestMethod method : match.getInfo().getMethodsCondition().getMethods()) {
                    result.add(method.name());
                }
            }
            return result;
        }

        /**
         * Return declared "consumable" types but only among those that also
         * match the "methods" condition.
         */
        public Set<MediaType> getConsumableMediaTypes() {
            Set<MediaType> result = new LinkedHashSet<MediaType>();
            for (PartialMatch match : this.partialMatches) {
                if (match.hasMethodsMatch()) {
                    result.addAll(match.getInfo().getConsumesCondition().getConsumableMediaTypes());
                }
            }
            return result;
        }

        /**
         * Return declared "producible" types but only among those that also
         * match the "methods" and "consumes" conditions.
         */
        public Set<MediaType> getProducibleMediaTypes() {
            Set<MediaType> result = new LinkedHashSet<MediaType>();
            for (PartialMatch match : this.partialMatches) {
                if (match.hasConsumesMatch()) {
                    result.addAll(match.getInfo().getProducesCondition().getProducibleMediaTypes());
                }
            }
            return result;
        }

        /**
         * Return declared "params" conditions but only among those that also
         * match the "methods", "consumes", and "params" conditions.
         */
        public List<String[]> getParamConditions() {
            List<String[]> result = new ArrayList<String[]>();
            for (PartialMatch match : this.partialMatches) {
                if (match.hasProducesMatch()) {
                    Set<NameValueExpression<String>> set = match.getInfo().getParamsCondition().getExpressions();
                    if (!CollectionUtils.isEmpty(set)) {
                        int i = 0;
                        String[] array = new String[set.size()];
                        for (NameValueExpression<String> expression : set) {
                            array[i++] = expression.toString();
                        }
                        result.add(array);
                    }
                }
            }
            return result;
        }


        /**
         * Container for a RequestMappingInfo that matches the URL path at least.
         */
        private static class PartialMatch {

            private final RequestMappingInfo info;

            private final boolean methodsMatch;

            private final boolean consumesMatch;

            private final boolean producesMatch;

            private final boolean paramsMatch;

            /**
             * @param info    RequestMappingInfo that matches the URL path.
             * @param request the current request
             */
            public PartialMatch(RequestMappingInfo info, HttpServletRequest request) {
                this.info = info;
                this.methodsMatch = (info.getMethodsCondition().getMatchingCondition(request) != null);
                this.consumesMatch = (info.getConsumesCondition().getMatchingCondition(request) != null);
                this.producesMatch = (info.getProducesCondition().getMatchingCondition(request) != null);
                this.paramsMatch = (info.getParamsCondition().getMatchingCondition(request) != null);
            }

            public RequestMappingInfo getInfo() {
                return this.info;
            }

            public boolean hasMethodsMatch() {
                return this.methodsMatch;
            }

            public boolean hasConsumesMatch() {
                return (hasMethodsMatch() && this.consumesMatch);
            }

            public boolean hasProducesMatch() {
                return (hasConsumesMatch() && this.producesMatch);
            }

            public boolean hasParamsMatch() {
                return (hasProducesMatch() && this.paramsMatch);
            }

            @Override
            public String toString() {
                return this.info.toString();
            }
        }
    }


}

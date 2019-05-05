package com.caiya.authority.test;

import com.caiya.authority.core.*;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import javax.servlet.http.HttpServletRequest;

/**
 * AuthorityTest.
 *
 * @author wangnan
 * @since 1.0
 */
public class AuthorityTest {

    AbstractHandlerMethodMapping<RequestMappingInfo> mapping;

    @Before
    public void before() {
        mapping = new RequestMappingInfoHandlerMapping();

        PatternsRequestCondition patternsCondition = new PatternsRequestCondition("/user/info");
        RequestMethodsRequestCondition methodsCondition = new RequestMethodsRequestCondition(RequestMethod.valueOf("GET"));
        // reserve other conditions..
        RequestMappingInfo requestMappingInfo = new RequestMappingInfo(null, patternsCondition, methodsCondition, null, null, null, null, null);

        mapping.registerMapping(requestMappingInfo, this.getClass(), this.getClass().getMethods()[0]);
    }

    @Test
    public void test() throws Exception {
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        Mockito.when(request.getRequestURI()).thenReturn("/user/info");
        Mockito.when(request.getMethod()).thenReturn("GET");
        Mockito.when(request.getContextPath()).thenReturn("");
        Mockito.when(request.getServletPath()).thenReturn("/user/info");
        AbstractHandlerMethodMapping.Match bestMatch = mapping.getBestMatch(request);
        Assert.assertNotNull(bestMatch);
    }
}

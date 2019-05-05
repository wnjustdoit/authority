package com.caiya.authority.core;

/**
 * A contract for {@code "name!=value"} style expression used to specify request
 * parameters and request header conditions in {@code @RequestMapping}.
 *
 * @author Rossen Stoyanchev
 * @see //RequestMapping#params()
 * @see //RequestMapping#headers()
 * @since 3.1
 */
public interface NameValueExpression<T> {

    String getName();

    T getValue();

    boolean isNegated();

}

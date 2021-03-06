package com.caiya.authority.core;

/**
 * A contract for media type expressions (e.g. "text/plain", "!text/plain") as
 * defined in the {@code @RequestMapping} annotation for "consumes" and
 * "produces" conditions.
 *
 * @author Rossen Stoyanchev
 * @since 3.1
 *
 * @see //RequestMapping#consumes()
 * @see //RequestMapping#produces()
 */
public interface MediaTypeExpression {

    MediaType getMediaType();

    boolean isNegated();

}

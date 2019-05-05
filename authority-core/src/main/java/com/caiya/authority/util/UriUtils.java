package com.caiya.authority.util;

import com.mamaqunaer.common.util.Assert;

import java.io.ByteArrayOutputStream;
import java.io.UnsupportedEncodingException;

/**
 * Utility class for URI encoding and decoding based on RFC 3986.
 * Offers encoding methods for the various URI components.
 * <p>
 * <p>All {@code encode*(String, String)} methods in this class operate in a similar way:
 * <ul>
 * <li>Valid characters for the specific URI component as defined in RFC 3986 stay the same.</li>
 * <li>All other characters are converted into one or more bytes in the given encoding scheme.
 * Each of the resulting bytes is written as a hexadecimal string in the "<code>%<i>xy</i></code>"
 * format.</li>
 * </ul>
 *
 * @author Arjen Poutsma
 * @author Juergen Hoeller
 * @see <a href="http://www.ietf.org/rfc/rfc3986.txt">RFC 3986</a>
 * @since 3.0
 */
public abstract class UriUtils {
    /**
     * Decode the given encoded URI component.
     * <ul>
     * <li>Alphanumeric characters {@code "a"} through {@code "z"}, {@code "A"} through {@code "Z"}, and
     * {@code "0"} through {@code "9"} stay the same.</li>
     * <li>Special characters {@code "-"}, {@code "_"}, {@code "."}, and {@code "*"} stay the same.</li>
     * <li>A sequence "{@code %<i>xy</i>}" is interpreted as a hexadecimal representation of the character.</li>
     * </ul>
     *
     * @param source   the encoded String
     * @param encoding the encoding
     * @return the decoded value
     * @throws IllegalArgumentException     when the given source contains invalid encoded sequences
     * @throws UnsupportedEncodingException when the given encoding parameter is not supported
     * @see java.net.URLDecoder#decode(String, String)
     */
    public static String decode(String source, String encoding) throws UnsupportedEncodingException {
        if (source == null) {
            return null;
        }
        Assert.hasLength(encoding, "Encoding must not be empty");
        int length = source.length();
        ByteArrayOutputStream bos = new ByteArrayOutputStream(length);
        boolean changed = false;
        for (int i = 0; i < length; i++) {
            int ch = source.charAt(i);
            if (ch == '%') {
                if ((i + 2) < length) {
                    char hex1 = source.charAt(i + 1);
                    char hex2 = source.charAt(i + 2);
                    int u = Character.digit(hex1, 16);
                    int l = Character.digit(hex2, 16);
                    if (u == -1 || l == -1) {
                        throw new IllegalArgumentException("Invalid encoded sequence \"" + source.substring(i) + "\"");
                    }
                    bos.write((char) ((u << 4) + l));
                    i += 2;
                    changed = true;
                } else {
                    throw new IllegalArgumentException("Invalid encoded sequence \"" + source.substring(i) + "\"");
                }
            } else {
                bos.write(ch);
            }
        }
        return (changed ? new String(bos.toByteArray(), encoding) : source);
    }

}

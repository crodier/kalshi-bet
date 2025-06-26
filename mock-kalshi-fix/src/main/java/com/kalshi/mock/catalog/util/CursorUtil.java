package com.kalshi.mock.catalog.util;

import java.util.Base64;

/**
 * Utility class for cursor-based pagination
 */
public class CursorUtil {
    
    /**
     * Encodes an offset into a cursor string
     */
    public static String encodeCursor(int offset) {
        return Base64.getEncoder().encodeToString(String.valueOf(offset).getBytes());
    }
    
    /**
     * Decodes a cursor string back to an offset
     */
    public static int decodeCursor(String cursor) {
        try {
            String decoded = new String(Base64.getDecoder().decode(cursor));
            return Integer.parseInt(decoded);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid cursor: " + cursor);
        }
    }
}
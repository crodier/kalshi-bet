package com.kalshi.mock.catalog.dto;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Utility class for handling cursor-based pagination
 */
public class CursorUtil {
    
    /**
     * Encodes an offset value into a cursor string
     * 
     * @param offset The offset value
     * @return Base64 encoded cursor string
     */
    public static String encodeCursor(int offset) {
        String cursorData = "offset:" + offset;
        return Base64.getEncoder().encodeToString(cursorData.getBytes(StandardCharsets.UTF_8));
    }
    
    /**
     * Decodes a cursor string to get the offset value
     * 
     * @param cursor The cursor string
     * @return The offset value
     */
    public static int decodeCursor(String cursor) {
        if (cursor == null || cursor.isEmpty()) {
            return 0;
        }
        
        try {
            byte[] decodedBytes = Base64.getDecoder().decode(cursor);
            String decodedString = new String(decodedBytes, StandardCharsets.UTF_8);
            
            if (decodedString.startsWith("offset:")) {
                String offsetStr = decodedString.substring("offset:".length());
                return Integer.parseInt(offsetStr);
            }
        } catch (Exception e) {
            // Invalid cursor format, return 0
        }
        
        return 0;
    }
    
    /**
     * Creates a cursor for the next page
     * 
     * @param currentOffset The current offset
     * @param limit The page size limit
     * @param totalRecords The total number of records available
     * @return The cursor for the next page, or null if no more pages
     */
    public static String getNextCursor(int currentOffset, int limit, int totalRecords) {
        int nextOffset = currentOffset + limit;
        
        if (nextOffset >= totalRecords) {
            return null; // No more pages
        }
        
        return encodeCursor(nextOffset);
    }
}
package com.kalshi.orderbook.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BulkOrderBookRequest {
    private List<String> marketTickers;
    private int depth = 1; // Default to top of book only
    private boolean includeAllLevels = false;
}
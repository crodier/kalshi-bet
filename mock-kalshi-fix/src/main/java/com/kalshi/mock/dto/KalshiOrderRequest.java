package com.kalshi.mock.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

/**
 * Kalshi order request matching the actual API structure
 */
@Schema(description = "Order request for placing a new order")
public class KalshiOrderRequest {
    
    @NotNull
    @Schema(description = "Market ticker", example = "TRUMPWIN-24NOV05", required = true)
    @JsonProperty("market_ticker")
    private String marketTicker;
    
    @NotNull
    @Pattern(regexp = "yes|no", message = "Side must be 'yes' or 'no'")
    @Schema(description = "Contract side", allowableValues = {"yes", "no"}, required = true)
    private String side;
    
    @NotNull
    @Pattern(regexp = "buy|sell", message = "Action must be 'buy' or 'sell'")
    @Schema(description = "Order action", allowableValues = {"buy", "sell"}, required = true)
    private String action;
    
    @NotNull
    @Pattern(regexp = "limit|market", message = "Type must be 'limit' or 'market'")
    @Schema(description = "Order type", allowableValues = {"limit", "market"}, required = true)
    private String type;
    
    @NotNull
    @Min(1)
    @Schema(description = "Number of contracts", example = "100", required = true)
    private Integer count;
    
    @Min(1)
    @Max(99)
    @Schema(description = "Price in cents (required for limit orders)", example = "65")
    private Integer price;
    
    @Pattern(regexp = "GTC|IOC|FOK", message = "Time in force must be 'GTC', 'IOC', or 'FOK'")
    @Schema(description = "Time in force", allowableValues = {"GTC", "IOC", "FOK"}, defaultValue = "GTC")
    @JsonProperty("time_in_force")
    private String timeInForce;
    
    @Schema(description = "Client order ID for tracking", example = "MY-ORDER-123")
    @JsonProperty("client_order_id")
    private String clientOrderId;
    
    // Getters and setters
    public String getMarketTicker() { return marketTicker; }
    public void setMarketTicker(String marketTicker) { this.marketTicker = marketTicker; }
    
    public String getSide() { return side; }
    public void setSide(String side) { this.side = side; }
    
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    
    public Integer getCount() { return count; }
    public void setCount(Integer count) { this.count = count; }
    
    public Integer getPrice() { return price; }
    public void setPrice(Integer price) { this.price = price; }
    
    public String getTimeInForce() { return timeInForce; }
    public void setTimeInForce(String timeInForce) { this.timeInForce = timeInForce; }
    
    public String getClientOrderId() { return clientOrderId; }
    public void setClientOrderId(String clientOrderId) { this.clientOrderId = clientOrderId; }
}
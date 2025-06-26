package com.kalshi.mock.catalog.dto;

import com.kalshi.mock.catalog.model.Series;
import com.kalshi.mock.catalog.model.Event;
import com.kalshi.mock.catalog.model.Market;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Mapper class for converting between catalog entities and DTOs
 */
@Component
public class CatalogMapper {
    
    /**
     * Converts a Series entity to SeriesResponse DTO
     */
    public SeriesResponse toSeriesResponse(Series series) {
        if (series == null) {
            return null;
        }
        
        SeriesResponse response = new SeriesResponse();
        response.setTicker(series.getTicker());
        response.setFrequency(series.getFrequency());
        response.setTitle(series.getTitle());
        response.setCategory(series.getCategory());
        response.setTags(series.getTags());
        response.setSettlementSources(series.getSettlementSources());
        response.setContractUrl(series.getContractUrl());
        
        // Convert fee multiplier to basis points
        if (series.getFeeMultiplier() != null) {
            response.setFeeRateBps((int)(series.getFeeMultiplier() * 10000));
        }
        
        return response;
    }
    
    /**
     * Converts an Event entity to EventResponse DTO
     */
    public EventResponse toEventResponse(Event event, boolean includeMarkets) {
        if (event == null) {
            return null;
        }
        
        EventResponse response = new EventResponse();
        response.setEventTicker(event.getEventTicker());
        response.setSeriesTicker(event.getSeriesTicker());
        response.setTitle(event.getTitle());
        response.setSubTitle(event.getSubTitle());
        response.setCategory(event.getCategory());
        response.setStatus(event.getStatus().toString());
        response.setMutuallyExclusive(event.getMutuallyExclusive());
        response.setYesSubTitle(event.getYesSubTitle());
        response.setNoSubTitle(event.getNoSubTitle());
        response.setExpectedExpirationTime(event.getExpectedExpirationTime());
        response.setResponsePriceUnits(event.getResponsePriceUnits());
        
        if (includeMarkets && event.getMarkets() != null) {
            List<MarketResponse> marketResponses = event.getMarkets().stream()
                .map(this::toMarketResponse)
                .collect(Collectors.toList());
            response.setMarkets(marketResponses);
        }
        
        return response;
    }
    
    /**
     * Converts a Market entity to MarketResponse DTO
     */
    public MarketResponse toMarketResponse(Market market) {
        if (market == null) {
            return null;
        }
        
        MarketResponse response = new MarketResponse();
        response.setTicker(market.getTicker());
        response.setEventTicker(market.getEventTicker());
        response.setMarketType(market.getMarketType().toString());
        response.setTitle(market.getTitle());
        response.setSubtitle(market.getSubtitle());
        response.setYesSubTitle(market.getYesSubtitle());
        response.setNoSubTitle(market.getNoSubtitle());
        response.setOpenTime(market.getOpenTime());
        response.setCloseTime(market.getCloseTime());
        response.setExpectedExpirationTime(market.getExpectedExpirationTime());
        response.setExpirationTime(market.getExpirationTime());
        response.setStatus(market.getStatus().toString());
        
        // Convert price information (BigDecimal to cents Integer)
        if (market.getYesBid() != null) {
            response.setYesBid(market.getYesBid().multiply(new java.math.BigDecimal(100)).intValue());
        }
        if (market.getYesAsk() != null) {
            response.setYesAsk(market.getYesAsk().multiply(new java.math.BigDecimal(100)).intValue());
        }
        if (market.getNoBid() != null) {
            response.setNoBid(market.getNoBid().multiply(new java.math.BigDecimal(100)).intValue());
        }
        if (market.getNoAsk() != null) {
            response.setNoAsk(market.getNoAsk().multiply(new java.math.BigDecimal(100)).intValue());
        }
        if (market.getLastPrice() != null) {
            response.setLastPrice(market.getLastPrice().multiply(new java.math.BigDecimal(100)).intValue());
        }
        if (market.getPreviousYesBid() != null) {
            response.setPreviousYesBid(market.getPreviousYesBid().multiply(new java.math.BigDecimal(100)).intValue());
        }
        if (market.getPreviousYesAsk() != null) {
            response.setPreviousYesAsk(market.getPreviousYesAsk().multiply(new java.math.BigDecimal(100)).intValue());
        }
        if (market.getPreviousPrice() != null) {
            response.setPreviousPrice(market.getPreviousPrice().multiply(new java.math.BigDecimal(100)).intValue());
        }
        
        response.setVolume(market.getVolume());
        response.setVolume24h(market.getVolume24h());
        response.setLiquidity(market.getLiquidity());
        response.setOpenInterest(market.getOpenInterest());
        response.setNotionalValue(market.getNotionalValue());
        response.setRiskLimitCents(market.getRiskLimitCents());
        response.setStrikeType(market.getStrikeType());
        response.setFloorStrike(market.getFloorStrike());
        response.setCapStrike(market.getCapStrike());
        response.setResult(market.getResult());
        response.setCanCloseEarly(market.getCanCloseEarly());
        response.setExpirationValue(market.getExpirationValue());
        response.setCategory(market.getCategory());
        response.setRulesPrimary(market.getRulesPrimary());
        response.setRulesSecondary(market.getRulesSecondary());
        response.setResponsePriceUnits(market.getResponsePriceUnits());
        response.setSettlementTimerSeconds(market.getSettlementTimerSeconds());
        response.setSettlementSource(market.getSettlementSource());
        response.setCustomStrike(market.getCustomStrike());
        response.setIsDeactivated(market.getIsDeactivated());
        
        return response;
    }
    
    /**
     * Converts a CreateSeriesRequest DTO to Series entity
     */
    public Series toSeries(CreateSeriesRequest request) {
        if (request == null) {
            return null;
        }
        
        Series series = new Series();
        series.setTicker(request.getTicker());
        series.setFrequency(request.getFrequency());
        series.setTitle(request.getTitle());
        series.setCategory(request.getCategory());
        series.setTags(request.getTags());
        series.setSettlementSources(request.getSettlementSources());
        series.setContractUrl(request.getContractUrl());
        series.setFeeType(request.getFeeType());
        series.setFeeMultiplier(request.getFeeMultiplier());
        
        return series;
    }
    
    /**
     * Converts a CreateEventRequest DTO to Event entity
     */
    public Event toEvent(CreateEventRequest request) {
        if (request == null) {
            return null;
        }
        
        Event event = new Event();
        event.setEventTicker(request.getEventTicker());
        event.setSeriesTicker(request.getSeriesTicker());
        event.setTitle(request.getTitle());
        event.setSubTitle(request.getSubTitle());
        event.setCategory(request.getCategory());
        event.setMutuallyExclusive(request.getMutuallyExclusive());
        event.setYesSubTitle(request.getYesSubTitle());
        event.setNoSubTitle(request.getNoSubTitle());
        event.setExpectedExpirationTime(request.getExpectedExpirationTime());
        event.setResponsePriceUnits(request.getResponsePriceUnits());
        
        return event;
    }
    
    /**
     * Converts a CreateMarketRequest DTO to Market entity
     */
    public Market toMarket(CreateMarketRequest request) {
        if (request == null) {
            return null;
        }
        
        Market market = new Market();
        market.setTicker(request.getTicker());
        market.setEventTicker(request.getEventTicker());
        if (request.getMarketType() != null) {
            market.setMarketType(Market.MarketType.valueOf(request.getMarketType()));
        }
        market.setTitle(request.getTitle());
        market.setSubtitle(request.getSubtitle());
        market.setYesSubtitle(request.getYesSubtitle());
        market.setNoSubtitle(request.getNoSubtitle());
        market.setOpenTime(request.getOpenTime());
        market.setCloseTime(request.getCloseTime());
        market.setExpectedExpirationTime(request.getExpectedExpirationTime());
        if (request.getRiskLimitCents() != null) {
            market.setRiskLimitCents(request.getRiskLimitCents().longValue());
        }
        market.setStrikeType(request.getStrikeType());
        if (request.getFloorStrike() != null) {
            market.setFloorStrike(new java.math.BigDecimal(request.getFloorStrike()));
        }
        if (request.getCapStrike() != null) {
            market.setCapStrike(new java.math.BigDecimal(request.getCapStrike()));
        }
        market.setCanCloseEarly(request.getCanCloseEarly());
        market.setCategory(request.getCategory());
        market.setRulesPrimary(request.getRulesPrimary());
        market.setRulesSecondary(request.getRulesSecondary());
        market.setResponsePriceUnits(request.getResponsePriceUnits());
        market.setSettlementSource(request.getSettlementSource());
        market.setCustomStrike(request.getCustomStrike());
        
        return market;
    }
}
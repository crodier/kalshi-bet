package com.betfanatics.exchange.order.service

import com.betfanatics.exchange.order.health.FixHealthIndicator
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import quickfix.Session
import quickfix.SessionID
import quickfix.SessionSettings
import java.io.FileInputStream
import java.time.*
import java.time.format.DateTimeFormatter
import javax.annotation.PostConstruct
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty

@Service
@ConditionalOnProperty(name = ["quickfixj.client.enabled"], havingValue = "true", matchIfMissing = true)
class FixConnectionMonitorService(
    private val fixHealthIndicator: FixHealthIndicator,
    private val fixErrorService: FixErrorService,
    @Value("\${quickfixj.client.config:classpath:quickfixj-prod.cfg}") private val configPath: String,
    @Value("\${quickfixj.mock.config.file:classpath:quickfixj-mock.cfg}") private val mockConfigPath: String,
    @Value("\${spring.profiles.active:}") private val activeProfiles: String
) {
    
    private val log = LoggerFactory.getLogger(FixConnectionMonitorService::class.java)
    
    private var sessionSettings: SessionSettings? = null
    private var lastErrorPublishTime: Instant? = null
    private val errorRepublishIntervalSeconds = 30L
    
    // Session schedule cache
    private data class SessionSchedule(
        val startDay: DayOfWeek?,
        val endDay: DayOfWeek?,
        val startTime: LocalTime?,
        val endTime: LocalTime?,
        val timezone: ZoneId,
        val use24HourSession: Boolean = false
    )
    
    private var sessionSchedule: SessionSchedule? = null
    
    @PostConstruct
    fun init() {
        try {
            // Load the appropriate config based on profile
            val configFile = if (activeProfiles.contains("mock")) mockConfigPath else configPath
            
            sessionSettings = if (configFile.startsWith("classpath:")) {
                val resourceStream = this::class.java.classLoader.getResourceAsStream(
                    configFile.removePrefix("classpath:")
                )
                resourceStream?.use { SessionSettings(it) }
            } else {
                SessionSettings(FileInputStream(configFile))
            }
            
            // Parse session schedule from settings
            sessionSettings?.let { settings ->
                val sessionIds = settings.sectionIterator()
                if (sessionIds.hasNext()) {
                    val sessionId = sessionIds.next() as SessionID
                    sessionSchedule = parseSessionSchedule(settings, sessionId)
                    log.info("Loaded FIX session schedule: {}", sessionSchedule)
                }
            }
        } catch (e: Exception) {
            log.error("Failed to load FIX session configuration: {}", e.message, e)
        }
    }
    
    @Scheduled(fixedDelay = 5000) // Check every 5 seconds
    fun monitorConnection() {
        try {
            val health = fixHealthIndicator.health()
            val isConnected = health.status.code == "UP"
            
            if (!isConnected && shouldReportError()) {
                // Check if we're outside scheduled downtime
                if (!isWithinScheduledDowntime()) {
                    republishConnectionError()
                } else {
                    log.debug("FIX disconnected but within scheduled downtime, not reporting error")
                }
            }
        } catch (e: Exception) {
            log.error("Error in connection monitor: {}", e.message, e)
        }
    }
    
    private fun shouldReportError(): Boolean {
        lastErrorPublishTime?.let { lastTime ->
            val secondsSinceLastPublish = Duration.between(lastTime, Instant.now()).toSeconds()
            return secondsSinceLastPublish >= errorRepublishIntervalSeconds
        }
        return true // First time
    }
    
    private fun isWithinScheduledDowntime(): Boolean {
        sessionSchedule?.let { schedule ->
            // If 24-hour session, never in scheduled downtime
            if (schedule.use24HourSession) {
                return false
            }
            
            val now = ZonedDateTime.now(schedule.timezone)
            val currentTime = now.toLocalTime()
            
            // For Kalshi, the market is generally open:
            // Sunday 6PM ET to Friday 6PM ET (with a daily reset at 2AM ET)
            // But the FIX session may have different hours
            
            // If we only have time-based schedule (no day info)
            if (schedule.startTime != null && schedule.endTime != null) {
                // Check if we're in a daily maintenance window
                if (schedule.startTime > schedule.endTime) {
                    // Crosses midnight (e.g., 22:00:00 to 02:00:00)
                    return currentTime >= schedule.endTime && currentTime < schedule.startTime
                } else {
                    // Within same day
                    return currentTime < schedule.startTime || currentTime >= schedule.endTime
                }
            }
            
            // Full week schedule with days
            if (schedule.startDay != null && schedule.endDay != null && 
                schedule.startTime != null && schedule.endTime != null) {
                
                val currentDay = now.dayOfWeek
                
                // Check if we're within the week range
                val isWithinWeek = when {
                    schedule.startDay == schedule.endDay -> currentDay == schedule.startDay
                    schedule.startDay < schedule.endDay -> 
                        currentDay >= schedule.startDay && currentDay <= schedule.endDay
                    else -> // Wraps around week (e.g., Sunday to Friday)
                        currentDay >= schedule.startDay || currentDay <= schedule.endDay
                }
                
                if (!isWithinWeek) {
                    return true // Outside trading days
                }
                
                // Within trading days, check time
                when (currentDay) {
                    schedule.startDay -> {
                        // On start day, downtime before market open
                        return currentTime < schedule.startTime
                    }
                    schedule.endDay -> {
                        // On end day, downtime after market close
                        return currentTime >= schedule.endTime
                    }
                    else -> {
                        // Mid-week, check daily maintenance window
                        // Kalshi typically has maintenance at 2AM ET
                        val maintenanceStart = LocalTime.of(2, 0)
                        val maintenanceEnd = LocalTime.of(2, 5)
                        return currentTime >= maintenanceStart && currentTime < maintenanceEnd
                    }
                }
            }
            
            return false
        }
        
        // If no schedule is configured, assume 24/7 operation
        return false
    }
    
    private fun republishConnectionError() {
        // Check if we have a connection error to republish
        val lastError = fixErrorService.getLastConnectionError()
        if (lastError == null || lastError.errorType != "CONNECTION_ERROR") {
            // No previous connection error, create a new one
            val health = fixHealthIndicator.health()
            val details = mutableMapOf<String, Any>()
            
            // Add health check details
            health.details.forEach { (key, value) ->
                details[key] = value.toString()
            }
            
            // Add schedule information
            sessionSchedule?.let { schedule ->
                details["scheduledStartDay"] = schedule.startDay?.toString() ?: "Not configured"
                details["scheduledEndDay"] = schedule.endDay?.toString() ?: "Not configured"
                details["scheduledStartTime"] = schedule.startTime?.toString() ?: "Not configured"
                details["scheduledEndTime"] = schedule.endTime?.toString() ?: "Not configured"
                details["timezone"] = schedule.timezone.toString()
                details["use24HourSession"] = schedule.use24HourSession
            }
            
            details["outsideScheduledDowntime"] = true
            
            val sessionId = health.details["sessionId"] as? String
            val errorMessage = health.details["lastError"] as? String ?: "FIX connection not established"
            
            fixErrorService.reportConnectionError(
                sessionId,
                "FIX connection unavailable outside scheduled downtime: $errorMessage",
                details
            )
        } else {
            // Republish the existing error with updated count
            fixErrorService.republishConnectionError()
        }
        
        lastErrorPublishTime = Instant.now()
        log.warn("FIX connection error published - disconnected outside scheduled downtime")
    }
    
    private fun parseSessionSchedule(settings: SessionSettings, sessionId: SessionID): SessionSchedule {
        return try {
            val timezone = try {
                val tz = settings.getString(sessionId, "TimeZone")
                ZoneId.of(tz)
            } catch (e: Exception) {
                ZoneId.of("America/New_York") // Default to ET
            }
            
            val startDay = try {
                DayOfWeek.valueOf(settings.getString(sessionId, "StartDay").uppercase())
            } catch (e: Exception) {
                null
            }
            
            val endDay = try {
                DayOfWeek.valueOf(settings.getString(sessionId, "EndDay").uppercase())
            } catch (e: Exception) {
                null
            }
            
            val startTime = try {
                val timeStr = settings.getString(sessionId, "StartTime")
                LocalTime.parse(timeStr, DateTimeFormatter.ofPattern("HH:mm:ss"))
            } catch (e: Exception) {
                null
            }
            
            val endTime = try {
                val timeStr = settings.getString(sessionId, "EndTime")
                LocalTime.parse(timeStr, DateTimeFormatter.ofPattern("HH:mm:ss"))
            } catch (e: Exception) {
                null
            }
            
            // Check if this is a 24-hour session
            val use24Hour = startTime == LocalTime.of(0, 0, 0) && 
                           endTime == LocalTime.of(23, 59, 59)
            
            SessionSchedule(startDay, endDay, startTime, endTime, timezone, use24Hour)
        } catch (e: Exception) {
            log.warn("Could not parse session schedule from settings: {}", e.message)
            SessionSchedule(null, null, null, null, ZoneId.of("America/New_York"))
        }
    }
}
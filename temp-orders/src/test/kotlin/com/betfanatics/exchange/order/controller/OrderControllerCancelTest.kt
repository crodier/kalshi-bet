package com.betfanatics.exchange.controller

import com.betfanatics.exchange.order.actor.FixGatewayActor
import com.betfanatics.exchange.order.service.ClOrdIdMappingService
import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.javadsl.AskPattern
import org.apache.pekko.cluster.sharding.typed.javadsl.ClusterSharding
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityTypeKey
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.time.Duration
import java.util.concurrent.CompletableFuture
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@ExtendWith(MockitoExtension::class)
class OrderControllerCancelTest {

    private lateinit var mockMvc: MockMvc
    private lateinit var orderController: OrderController
    private lateinit var objectMapper: ObjectMapper

    @Mock
    private lateinit var fixGatewayActor: ActorRef<FixGatewayActor.Command>

    @Mock
    private lateinit var clusterSharding: ClusterSharding

    @Mock
    private lateinit var actorSystem: ActorSystem<Void>

    @Mock
    private lateinit var orderProcessManagerTypeKey: EntityTypeKey<com.betfanatics.exchange.order.actor.OrderProcessManager.Command>

    @Mock
    private lateinit var clOrdIdMappingService: ClOrdIdMappingService

    @BeforeEach
    fun setup() {
        objectMapper = ObjectMapper()
        orderController = OrderController(
            fixGatewayActor,
            clusterSharding,
            actorSystem,
            orderProcessManagerTypeKey,
            clOrdIdMappingService
        )
        mockMvc = MockMvcBuilders.standaloneSetup(orderController).build()
    }

    @Test
    fun `test cancel order successfully`() {
        // Given
        val betOrderId = "test-order-123"
        val cancelClOrdId = "FBG_test_order_123_C_1234567890"
        val origClOrdId = "FBG_test_order_123"
        val cancelRequest = CancelOrderRequest(betOrderId = betOrderId)
        
        // Mock Redis service
        `when`(clOrdIdMappingService.hasClOrdId(betOrderId)).thenReturn(true)
        `when`(clOrdIdMappingService.generateAndStoreCancelClOrdId(betOrderId))
            .thenReturn(Pair(cancelClOrdId, origClOrdId))
        
        // Mock actor response
        val futureResponse = CompletableFuture.completedFuture(
            FixGatewayActor.OrderCancelled(betOrderId, "Cancel request sent")
        )
        `when`(actorSystem.scheduler()).thenReturn(mock(org.apache.pekko.actor.Scheduler::class.java))
        
        // Capture the ask pattern call
        val commandCaptor = argumentCaptor<java.util.function.Function<ActorRef<FixGatewayActor.Response>, FixGatewayActor.Command>>()
        `when`(AskPattern.ask(
            eq(fixGatewayActor),
            commandCaptor.capture(),
            any<Duration>(),
            any()
        )).thenReturn(futureResponse)
        
        // When
        mockMvc.perform(
            MockMvcRequestBuilders.post("/v1/order/cancel")
                .header("X-Dev-User", "test-user")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(cancelRequest))
        )
            // Then
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.content().string("Cancel request sent for betOrderId: $betOrderId"))
        
        // Verify the command sent to actor
        val replyTo = mock(ActorRef::class.java) as ActorRef<FixGatewayActor.Response>
        val command = commandCaptor.firstValue.apply(replyTo) as FixGatewayActor.CancelOrder
        
        assertEquals(betOrderId, command.betOrderId)
        assertEquals(cancelClOrdId, command.cancelClOrdId)
        assertEquals(origClOrdId, command.origClOrdId)
        assertEquals("test-user", command.userId)
    }

    @Test
    fun `test cancel order with modify ClOrdID in tag 41`() {
        // Given
        val betOrderId = "test-order-456"
        val cancelClOrdId = "FBG_test_order_456_C_9876543210"
        val modifyClOrdId = "FBG_test_order_456_M_1234567890" // This should be in tag 41
        val cancelRequest = CancelOrderRequest(betOrderId = betOrderId)
        
        // Mock Redis service - returns modify ClOrdID as origClOrdId
        `when`(clOrdIdMappingService.hasClOrdId(betOrderId)).thenReturn(true)
        `when`(clOrdIdMappingService.generateAndStoreCancelClOrdId(betOrderId))
            .thenReturn(Pair(cancelClOrdId, modifyClOrdId))
        
        // Mock actor response
        val futureResponse = CompletableFuture.completedFuture(
            FixGatewayActor.OrderCancelled(betOrderId, "Cancel request sent")
        )
        `when`(actorSystem.scheduler()).thenReturn(mock(org.apache.pekko.actor.Scheduler::class.java))
        
        // Capture the ask pattern call
        val commandCaptor = argumentCaptor<java.util.function.Function<ActorRef<FixGatewayActor.Response>, FixGatewayActor.Command>>()
        `when`(AskPattern.ask(
            eq(fixGatewayActor),
            commandCaptor.capture(),
            any<Duration>(),
            any()
        )).thenReturn(futureResponse)
        
        // When
        mockMvc.perform(
            MockMvcRequestBuilders.post("/v1/order/cancel")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(cancelRequest))
        )
            .andExpect(MockMvcResultMatchers.status().isOk)
        
        // Verify the command has modify ClOrdID in origClOrdId (tag 41)
        val replyTo = mock(ActorRef::class.java) as ActorRef<FixGatewayActor.Response>
        val command = commandCaptor.firstValue.apply(replyTo) as FixGatewayActor.CancelOrder
        
        assertEquals(modifyClOrdId, command.origClOrdId, "Tag 41 should contain the modify ClOrdID")
        assertTrue(command.origClOrdId.contains("_M_"), "Tag 41 should be a modify ClOrdID")
    }

    @Test
    fun `test cancel order not found`() {
        // Given
        val betOrderId = "non-existent-order"
        val cancelRequest = CancelOrderRequest(betOrderId = betOrderId)
        
        // Mock Redis service
        `when`(clOrdIdMappingService.hasClOrdId(betOrderId)).thenReturn(false)
        
        // When/Then
        mockMvc.perform(
            MockMvcRequestBuilders.post("/v1/order/cancel")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(cancelRequest))
        )
            .andExpect(MockMvcResultMatchers.status().isNotFound)
            .andExpect(MockMvcResultMatchers.content().string("Order not found: $betOrderId"))
        
        // Verify no cancel ClOrdID was generated
        verify(clOrdIdMappingService, never()).generateAndStoreCancelClOrdId(any())
    }

    @Test
    fun `test cancel order rejected by FIX`() {
        // Given
        val betOrderId = "test-order-789"
        val cancelClOrdId = "FBG_test_order_789_C_1234567890"
        val origClOrdId = "FBG_test_order_789"
        val cancelRequest = CancelOrderRequest(betOrderId = betOrderId)
        
        // Mock Redis service
        `when`(clOrdIdMappingService.hasClOrdId(betOrderId)).thenReturn(true)
        `when`(clOrdIdMappingService.generateAndStoreCancelClOrdId(betOrderId))
            .thenReturn(Pair(cancelClOrdId, origClOrdId))
        
        // Mock actor response - rejected
        val futureResponse = CompletableFuture.completedFuture(
            FixGatewayActor.OrderRejected(betOrderId, "Order not in cancelable state")
        )
        `when`(actorSystem.scheduler()).thenReturn(mock(org.apache.pekko.actor.Scheduler::class.java))
        `when`(AskPattern.ask(
            eq(fixGatewayActor),
            any<java.util.function.Function<ActorRef<FixGatewayActor.Response>, FixGatewayActor.Command>>(),
            any<Duration>(),
            any()
        )).thenReturn(futureResponse)
        
        // When/Then
        mockMvc.perform(
            MockMvcRequestBuilders.post("/v1/order/cancel")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(cancelRequest))
        )
            .andExpect(MockMvcResultMatchers.status().isBadRequest)
            .andExpect(MockMvcResultMatchers.content().string("Cancel rejected: Order not in cancelable state"))
    }
}
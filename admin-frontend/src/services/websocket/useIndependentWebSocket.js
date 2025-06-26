import { useState, useEffect, useRef, useCallback } from 'react';

export const useIndependentWebSocket = (url, options = {}) => {
  const {
    onMessage,
    onConnect,
    onDisconnect,
    onError,
    reconnectInterval = 5000,
    maxReconnectAttempts = 10
  } = options;

  const [connectionState, setConnectionState] = useState('disconnected'); // 'disconnected', 'connecting', 'connected', 'error', 'never-connected'
  const [lastMessage, setLastMessage] = useState(null);
  const [lastMessageTime, setLastMessageTime] = useState(null);
  const [connectTime, setConnectTime] = useState(null);
  const [uptime, setUptime] = useState(0);
  const [messageCount, setMessageCount] = useState(0);
  const [throughput, setThroughput] = useState(0);
  
  const wsRef = useRef(null);
  const reconnectTimeoutRef = useRef(null);
  const reconnectAttemptsRef = useRef(0);
  const messageCounterRef = useRef(0);
  const lastThroughputTime = useRef(Date.now());
  const isFirstConnection = useRef(true);

  // Calculate throughput every second
  useEffect(() => {
    const interval = setInterval(() => {
      const now = Date.now();
      const timeDiff = (now - lastThroughputTime.current) / 1000;
      const msgDiff = messageCounterRef.current;
      
      if (timeDiff >= 1) {
        setThroughput(Math.round(msgDiff / timeDiff));
        messageCounterRef.current = 0;
        lastThroughputTime.current = now;
      }
      
      // Update uptime
      if (connectTime) {
        setUptime(Math.floor((now - connectTime) / 1000));
      }
    }, 1000);

    return () => clearInterval(interval);
  }, [connectTime]);

  const connect = useCallback(() => {
    if (!url || wsRef.current?.readyState === WebSocket.CONNECTING) return;

    if (wsRef.current && wsRef.current.readyState === WebSocket.OPEN) {
      wsRef.current.close();
    }

    setConnectionState('connecting');
    
    try {
      wsRef.current = new WebSocket(url);

      wsRef.current.onopen = () => {
        setConnectionState('connected');
        setConnectTime(Date.now());
        reconnectAttemptsRef.current = 0;
        isFirstConnection.current = false;
        
        if (onConnect) onConnect();
      };

      wsRef.current.onmessage = (event) => {
        const now = Date.now();
        let message;
        
        try {
          message = JSON.parse(event.data);
        } catch {
          message = event.data;
        }
        
        setLastMessage(message);
        setLastMessageTime(now);
        setMessageCount(prev => prev + 1);
        messageCounterRef.current++;
        
        if (onMessage) onMessage(message);
      };

      wsRef.current.onclose = () => {
        setConnectionState('disconnected');
        setConnectTime(null);
        setUptime(0);
        
        if (onDisconnect) onDisconnect();
        
        // Auto-reconnect if enabled and within retry limits
        if (reconnectInterval > 0 && reconnectAttemptsRef.current < maxReconnectAttempts) {
          reconnectAttemptsRef.current++;
          reconnectTimeoutRef.current = setTimeout(() => {
            connect();
          }, reconnectInterval * Math.pow(1.5, reconnectAttemptsRef.current - 1)); // Exponential backoff
        }
      };

      wsRef.current.onerror = (error) => {
        setConnectionState('error');
        if (onError) onError(error);
      };

    } catch (error) {
      setConnectionState('error');
      if (onError) onError(error);
    }
  }, [url, onMessage, onConnect, onDisconnect, onError, reconnectInterval, maxReconnectAttempts]);

  const disconnect = useCallback(() => {
    if (reconnectTimeoutRef.current) {
      clearTimeout(reconnectTimeoutRef.current);
      reconnectTimeoutRef.current = null;
    }
    
    if (wsRef.current) {
      wsRef.current.close();
      wsRef.current = null;
    }
    
    setConnectionState('disconnected');
    setConnectTime(null);
    setUptime(0);
  }, []);

  const sendMessage = useCallback((message) => {
    if (wsRef.current && wsRef.current.readyState === WebSocket.OPEN) {
      const msgStr = typeof message === 'string' ? message : JSON.stringify(message);
      wsRef.current.send(msgStr);
      return true;
    }
    return false;
  }, []);

  // Connect on mount and URL change
  useEffect(() => {
    if (url) {
      connect();
    }
    
    return () => {
      disconnect();
    };
  }, [url, connect, disconnect]);

  return {
    connectionState: isFirstConnection.current && connectionState === 'disconnected' ? 'never-connected' : connectionState,
    lastMessage,
    lastMessageTime,
    connectTime,
    uptime,
    messageCount,
    throughput,
    connect,
    disconnect,
    sendMessage,
    isConnected: connectionState === 'connected'
  };
};
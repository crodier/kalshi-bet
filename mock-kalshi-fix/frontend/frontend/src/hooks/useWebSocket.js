import { useEffect, useRef } from 'react';
import websocketService from '../services/websocket';

export const useWebSocket = () => {
  const isConnected = useRef(false);

  useEffect(() => {
    const connectWebSocket = async () => {
      if (!isConnected.current) {
        try {
          await websocketService.connect();
          isConnected.current = true;
          console.log('WebSocket connected from hook');
        } catch (error) {
          console.error('Failed to connect WebSocket:', error);
        }
      }
    };

    connectWebSocket();

    return () => {
      // Don't disconnect on component unmount to maintain connection
      // Only disconnect when the entire app unmounts
    };
  }, []);

  return websocketService;
};
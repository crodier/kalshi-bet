import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import OrderBook from './OrderBook';
import * as api from '../services/api';
import websocketService from '../services/websocket';

// Mock the API and WebSocket services
vi.mock('../services/api', () => ({
  marketAPI: {
    getOrderbook: vi.fn()
  }
}));

vi.mock('../services/websocket', () => ({
  default: {
    subscribe: vi.fn(),
    unsubscribe: vi.fn()
  }
}));

describe('OrderBook', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('shows empty state when no market is selected', () => {
    render(<OrderBook marketTicker={null} />);
    expect(screen.getByText('Select a market to view orderbook')).toBeInTheDocument();
  });

  it('shows loading state when fetching orderbook', () => {
    render(<OrderBook marketTicker="DUMMY_TEST" />);
    expect(screen.getByText('Loading orderbook...')).toBeInTheDocument();
  });

  it('displays orderbook data after loading', async () => {
    const mockOrderbook = {
      data: {
        orderbook: {
          yes: [
            [65, 100],
            [60, 200],
            [40, 300],
            [35, 400]
          ]
        }
      }
    };

    api.marketAPI.getOrderbook.mockResolvedValue(mockOrderbook);
    websocketService.subscribe.mockReturnValue(1);

    render(<OrderBook marketTicker="DUMMY_TEST" />);

    await waitFor(() => {
      expect(screen.getByText('Order Book - DUMMY_TEST')).toBeInTheDocument();
      expect(screen.getByText('Bids (Buy)')).toBeInTheDocument();
      expect(screen.getByText('Asks (Sell)')).toBeInTheDocument();
    });
  });

  it('subscribes to WebSocket updates', async () => {
    const mockOrderbook = {
      data: {
        orderbook: {
          yes: [[65, 100]]
        }
      }
    };

    api.marketAPI.getOrderbook.mockResolvedValue(mockOrderbook);
    websocketService.subscribe.mockReturnValue(1);

    render(<OrderBook marketTicker="DUMMY_TEST" />);

    await waitFor(() => {
      expect(websocketService.subscribe).toHaveBeenCalledWith(
        ['orderbook_snapshot', 'orderbook_delta'],
        ['DUMMY_TEST'],
        expect.any(Function)
      );
    });
  });

  it('unsubscribes from WebSocket on unmount', async () => {
    const mockOrderbook = {
      data: {
        orderbook: {
          yes: [[65, 100]]
        }
      }
    };

    api.marketAPI.getOrderbook.mockResolvedValue(mockOrderbook);
    websocketService.subscribe.mockReturnValue(1);

    const { unmount } = render(<OrderBook marketTicker="DUMMY_TEST" />);

    await waitFor(() => {
      expect(websocketService.subscribe).toHaveBeenCalled();
    });

    unmount();

    expect(websocketService.unsubscribe).toHaveBeenCalledWith(1);
  });
});
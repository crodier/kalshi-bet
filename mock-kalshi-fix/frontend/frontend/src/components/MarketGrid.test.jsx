import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import MarketGrid from './MarketGrid';

describe('MarketGrid', () => {
  it('renders loading state initially', () => {
    render(<MarketGrid />);
    expect(screen.getByText('Loading markets...')).toBeInTheDocument();
  });

  it('renders markets after loading', async () => {
    render(<MarketGrid />);
    
    await waitFor(() => {
      expect(screen.getByText('Markets')).toBeInTheDocument();
      expect(screen.getByText('DUMMY_TEST')).toBeInTheDocument();
      expect(screen.getByText('TRUMPWIN-24NOV05')).toBeInTheDocument();
    });
  });

  it('displays market details correctly', async () => {
    render(<MarketGrid />);
    
    await waitFor(() => {
      expect(screen.getByText('Dummy Test Market')).toBeInTheDocument();
      expect(screen.getByText('65¢')).toBeInTheDocument();
      expect(screen.getByText('1,000')).toBeInTheDocument();
    });
  });

  it('calls onMarketSelect when a market is clicked', async () => {
    const mockOnMarketSelect = vi.fn();
    render(<MarketGrid onMarketSelect={mockOnMarketSelect} />);
    
    await waitFor(() => {
      const marketRow = screen.getByText('DUMMY_TEST').closest('.grid-row');
      fireEvent.click(marketRow);
    });

    expect(mockOnMarketSelect).toHaveBeenCalledWith({
      ticker: 'DUMMY_TEST',
      name: 'Dummy Test Market',
      lastPrice: 65,
      volume: 1000
    });
  });

  it('highlights selected market', async () => {
    render(<MarketGrid />);
    
    await waitFor(() => {
      const marketRow = screen.getByText('DUMMY_TEST').closest('.grid-row');
      fireEvent.click(marketRow);
      expect(marketRow).toHaveClass('selected');
    });
  });
});
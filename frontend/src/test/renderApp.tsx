import { render, type RenderResult } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactElement } from 'react'
import { App } from '../App'

/** A fresh client per test, with retries off so a failure surfaces immediately. */
export function createTestQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: 0 } },
  })
}

export function renderWithClient(ui: ReactElement): RenderResult {
  const queryClient = createTestQueryClient()
  return render(<QueryClientProvider client={queryClient}>{ui}</QueryClientProvider>)
}

export function renderApp(): RenderResult {
  return renderWithClient(<App />)
}

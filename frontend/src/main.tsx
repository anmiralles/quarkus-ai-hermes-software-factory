import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { App } from './App'
import './index.css'

// Server state lives in TanStack Query (D-1); the app has no client-state store (D-2).
const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      // The API is a small catalogue service: a retry storm on a 500 helps nobody.
      retry: false,
    },
  },
})

const rootElement = document.getElementById('root')
if (rootElement === null) {
  throw new Error('Root element #root is missing from index.html')
}

createRoot(rootElement).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <App />
    </QueryClientProvider>
  </StrictMode>,
)

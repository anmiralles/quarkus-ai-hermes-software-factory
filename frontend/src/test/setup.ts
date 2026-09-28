import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterAll, afterEach, beforeAll } from 'vitest'
import { resetCoffeeStore } from './handlers'
import { server } from './server'

// `onUnhandledRequest: 'error'` — an unexpected call fails loudly instead of silently 404ing.
beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))

afterEach(() => {
  cleanup()
  server.resetHandlers()
  resetCoffeeStore()
})

afterAll(() => server.close())

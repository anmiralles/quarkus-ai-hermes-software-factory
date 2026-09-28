import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { makeCoffee } from '../test/handlers'
import { renderWithClient } from '../test/renderApp'
import { CoffeeForm } from './CoffeeForm'

/** The form's own guards and its accessible surface (§5.6, D-4, D-7). */
describe('CoffeeForm', () => {
  it('blocks a submit with the minimal client guards and reports both required fields', async () => {
    const user = userEvent.setup()
    const onSubmit = vi.fn()

    renderWithClient(
      <CoffeeForm mode="create" submitting={false} onSubmit={onSubmit} onCancel={() => {}} />,
    )

    expect(screen.getByRole('heading', { level: 2, name: 'New coffee' })).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Save coffee' }))

    expect(onSubmit).not.toHaveBeenCalled()
    expect(screen.getByText('Name is required')).toBeInTheDocument()
    expect(screen.getByText('Origin is required')).toBeInTheDocument()
    expect(screen.getByRole('textbox', { name: 'Name' })).toHaveAttribute('aria-invalid', 'true')
  })

  it('offers exactly the three roast levels and no free text', () => {
    renderWithClient(
      <CoffeeForm mode="create" submitting={false} onSubmit={() => {}} onCancel={() => {}} />,
    )

    const select = screen.getByRole('combobox', { name: 'Roast level' })
    expect(within(select).getAllByRole('option').map((option) => option.textContent)).toEqual([
      'LIGHT',
      'MEDIUM',
      'DARK',
    ])
  })

  it('prefills every field from the row in edit mode and submits the untrimmed-safe values', async () => {
    const user = userEvent.setup()
    const onSubmit = vi.fn()
    const coffee = makeCoffee({ name: 'Kenya Nyeri', roastLevel: 'DARK', origin: 'Kenya', price: 12.3, stock: 7 })

    renderWithClient(
      <CoffeeForm
        mode="edit"
        coffee={coffee}
        submitting={false}
        onSubmit={onSubmit}
        onCancel={() => {}}
      />,
    )

    expect(screen.getByRole('heading', { level: 2 })).toHaveTextContent('Edit coffee: Kenya Nyeri')
    expect(screen.getByRole('textbox', { name: 'Name' })).toHaveValue('Kenya Nyeri')
    expect(screen.getByRole('combobox', { name: 'Roast level' })).toHaveValue('DARK')
    expect(screen.getByRole('textbox', { name: 'Origin' })).toHaveValue('Kenya')
    expect(screen.getByRole('spinbutton', { name: 'Price' })).toHaveValue(12.3)
    expect(screen.getByRole('spinbutton', { name: 'Stock' })).toHaveValue(7)

    await user.click(screen.getByRole('button', { name: 'Save changes' }))

    expect(onSubmit).toHaveBeenCalledWith({
      name: 'Kenya Nyeri',
      roastLevel: 'DARK',
      origin: 'Kenya',
      price: 12.3,
      stock: 7,
    })
  })

  it('disables the submit button while the mutation is in flight', () => {
    renderWithClient(
      <CoffeeForm mode="create" submitting onSubmit={() => {}} onCancel={() => {}} />,
    )

    expect(screen.getByRole('button', { name: 'Save coffee' })).toBeDisabled()
  })
})

import { useState, type FormEvent } from 'react'
import { ROAST_LEVELS, type Coffee, type CoffeeRequest, type RoastLevel } from '../api/types'

type FieldName = 'name' | 'roastLevel' | 'origin' | 'price' | 'stock'

interface CoffeeFormProps {
  mode: 'create' | 'edit'
  /** Present in edit mode: the row being edited, used to prefill the fields (§5.4). */
  coffee?: Coffee
  /** Server field errors keyed by JSON property name (D-4), rendered next to the field. */
  fieldErrors?: Partial<Record<FieldName, string>>
  submitting: boolean
  onSubmit: (request: CoffeeRequest) => void
  onCancel: () => void
}

/**
 * Create + edit panel (§5.2, §5.4).
 *
 * The body it submits is built field by field, never by spreading a `Coffee` — the
 * backend runs `fail-on-unknown-properties=true`, so an accidental `id`/`createdAt`/
 * `updatedAt` would be a 400, not a no-op (D-5).
 *
 * The only client-side guards are "is it there" and "does it parse" (D-4); everything
 * else — lengths, ranges, decimal scale, the enum — is the server's answer, shown verbatim.
 *
 * The component is intentionally stateless about *results*: mutation state and server
 * errors are owned by the page, so the form stays presentational and testable.
 */
export function CoffeeForm({
  mode,
  coffee,
  fieldErrors,
  submitting,
  onSubmit,
  onCancel,
}: CoffeeFormProps) {
  const [name, setName] = useState(coffee?.name ?? '')
  const [roastLevel, setRoastLevel] = useState<RoastLevel>(coffee?.roastLevel ?? 'LIGHT')
  const [origin, setOrigin] = useState(coffee?.origin ?? '')
  const [price, setPrice] = useState(coffee ? String(coffee.price) : '')
  const [stock, setStock] = useState(coffee ? String(coffee.stock) : '')
  const [localErrors, setLocalErrors] = useState<Partial<Record<FieldName, string>>>({})

  const errors: Partial<Record<FieldName, string>> = { ...fieldErrors, ...localErrors }
  const heading = mode === 'edit' && coffee ? `Edit coffee: ${coffee.name}` : 'New coffee'

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()

    const guards: Partial<Record<FieldName, string>> = {}
    if (name.trim() === '') guards.name = 'Name is required'
    if (origin.trim() === '') guards.origin = 'Origin is required'

    const parsedPrice = Number(price)
    if (price.trim() === '' || !Number.isFinite(parsedPrice)) {
      guards.price = 'Price must be a number'
    }

    const parsedStock = Number(stock)
    if (stock.trim() === '' || !Number.isInteger(parsedStock)) {
      guards.stock = 'Stock must be a whole number'
    }

    setLocalErrors(guards)
    if (Object.keys(guards).length > 0) {
      return
    }

    onSubmit({
      name: name.trim(),
      roastLevel,
      origin: origin.trim(),
      price: parsedPrice,
      stock: parsedStock,
    })
  }

  return (
    <form onSubmit={handleSubmit} noValidate>
      <h2>{heading}</h2>

      <p>
        <label htmlFor="coffee-name">Name</label>
        <input
          id="coffee-name"
          type="text"
          value={name}
          onChange={(event) => setName(event.target.value)}
          aria-invalid={errors.name !== undefined}
          aria-describedby={errors.name !== undefined ? 'coffee-name-error' : undefined}
        />
        {errors.name !== undefined ? (
          <span id="coffee-name-error" role="alert">
            {errors.name}
          </span>
        ) : null}
      </p>

      <p>
        <label htmlFor="coffee-roast-level">Roast level</label>
        <select
          id="coffee-roast-level"
          value={roastLevel}
          onChange={(event) => setRoastLevel(event.target.value as RoastLevel)}
          aria-invalid={errors.roastLevel !== undefined}
          aria-describedby={errors.roastLevel !== undefined ? 'coffee-roast-level-error' : undefined}
        >
          {ROAST_LEVELS.map((level) => (
            <option key={level} value={level}>
              {level}
            </option>
          ))}
        </select>
        {errors.roastLevel !== undefined ? (
          <span id="coffee-roast-level-error" role="alert">
            {errors.roastLevel}
          </span>
        ) : null}
      </p>

      <p>
        <label htmlFor="coffee-origin">Origin</label>
        <input
          id="coffee-origin"
          type="text"
          value={origin}
          onChange={(event) => setOrigin(event.target.value)}
          aria-invalid={errors.origin !== undefined}
          aria-describedby={errors.origin !== undefined ? 'coffee-origin-error' : undefined}
        />
        {errors.origin !== undefined ? (
          <span id="coffee-origin-error" role="alert">
            {errors.origin}
          </span>
        ) : null}
      </p>

      <p>
        <label htmlFor="coffee-price">Price</label>
        <input
          id="coffee-price"
          type="number"
          step="any"
          value={price}
          onChange={(event) => setPrice(event.target.value)}
          aria-invalid={errors.price !== undefined}
          aria-describedby={errors.price !== undefined ? 'coffee-price-error' : undefined}
        />
        {errors.price !== undefined ? (
          <span id="coffee-price-error" role="alert">
            {errors.price}
          </span>
        ) : null}
      </p>

      <p>
        <label htmlFor="coffee-stock">Stock</label>
        <input
          id="coffee-stock"
          type="number"
          step="1"
          value={stock}
          onChange={(event) => setStock(event.target.value)}
          aria-invalid={errors.stock !== undefined}
          aria-describedby={errors.stock !== undefined ? 'coffee-stock-error' : undefined}
        />
        {errors.stock !== undefined ? (
          <span id="coffee-stock-error" role="alert">
            {errors.stock}
          </span>
        ) : null}
      </p>

      <p>
        <button type="submit" disabled={submitting}>
          {mode === 'edit' ? 'Save changes' : 'Save coffee'}
        </button>{' '}
        <button type="button" onClick={onCancel}>
          Cancel
        </button>
      </p>
    </form>
  )
}

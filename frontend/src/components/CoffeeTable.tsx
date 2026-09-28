import type { Coffee } from '../api/types'

interface CoffeeTableProps {
  coffees: Coffee[]
  confirmingId: string | null
  onEdit: (coffee: Coffee) => void
  onRequestDelete: (coffee: Coffee) => void
  onConfirmDelete: (coffee: Coffee) => void
  onCancelDelete: () => void
}

/**
 * The catalogue table (§5.6). Rows are keyed by the server's `id` — never by index,
 * which would break on reorder and mask state bugs. Prices render at two decimals
 * because the server serialises `BigDecimal` at scale 2 (D-6).
 */
export function CoffeeTable({
  coffees,
  confirmingId,
  onEdit,
  onRequestDelete,
  onConfirmDelete,
  onCancelDelete,
}: CoffeeTableProps) {
  return (
    <table>
      <thead>
        <tr>
          <th scope="col">Name</th>
          <th scope="col">Roast</th>
          <th scope="col">Origin</th>
          <th scope="col">Price</th>
          <th scope="col">Stock</th>
          <th scope="col">Actions</th>
        </tr>
      </thead>
      <tbody>
        {coffees.map((coffee) => (
          <tr key={coffee.id}>
            <td>{coffee.name}</td>
            <td>{coffee.roastLevel}</td>
            <td>{coffee.origin}</td>
            <td>{coffee.price.toFixed(2)}</td>
            <td>{coffee.stock}</td>
            <td>
              {confirmingId === coffee.id ? (
                <div>
                  <p>{`Delete ${coffee.name}? This cannot be undone.`}</p>
                  <button type="button" onClick={() => onConfirmDelete(coffee)}>
                    Confirm delete
                  </button>{' '}
                  <button type="button" onClick={onCancelDelete}>
                    Cancel delete
                  </button>
                </div>
              ) : (
                <div>
                  <button type="button" onClick={() => onEdit(coffee)}>
                    {`Edit ${coffee.name}`}
                  </button>{' '}
                  <button type="button" onClick={() => onRequestDelete(coffee)}>
                    {`Delete ${coffee.name}`}
                  </button>
                </div>
              )}
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  )
}

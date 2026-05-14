import { useCurrency } from './CurrencyContext';

/**
 * Navbar dropdown that lets the customer override the auto-detected viewing
 * currency. The selection is persisted in localStorage by the context.
 */
export function CurrencyPicker() {
  const { currencies, selected, setSelectedCode, loading } = useCurrency();

  if (loading || currencies.length === 0 || !selected) {
    return null;
  }

  return (
    <div className="dropdown">
      <button
        type="button"
        className="btn btn-sm btn-outline-secondary dropdown-toggle"
        data-bs-toggle="dropdown"
        aria-expanded="false"
        title="Viewing currency (display only - orders settle in the base currency)"
      >
        <i className="bi bi-currency-exchange me-1"></i>
        {selected.code}
      </button>
      <ul className="dropdown-menu dropdown-menu-end">
        {currencies.map((c) => (
          <li key={c.code}>
            <button
              type="button"
              className={`dropdown-item ${c.code === selected.code ? 'active' : ''}`}
              onClick={() => setSelectedCode(c.code)}
            >
              <span className="me-2 fw-semibold">{c.code}</span>
              <span className="text-muted small">
                {c.symbol} {c.name}
              </span>
            </button>
          </li>
        ))}
      </ul>
    </div>
  );
}

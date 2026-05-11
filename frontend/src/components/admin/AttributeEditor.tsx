import { useState } from 'react';

interface AttributeEditorProps {
  value: Record<string, unknown>;
  onChange: (next: Record<string, unknown>) => void;
}

interface DraftRow {
  id: string;
  key: string;
  value: string;
}

let rowSeq = 0;
const newId = () => `row-${++rowSeq}`;

const toRows = (value: Record<string, unknown>): DraftRow[] =>
  Object.entries(value).map(([key, val]) => ({
    id: newId(),
    key,
    value: typeof val === 'string' ? val : JSON.stringify(val),
  }));

const rowsToValue = (rows: DraftRow[]): Record<string, unknown> => {
  const result: Record<string, unknown> = {};
  for (const row of rows) {
    if (!row.key.trim()) continue;
    const trimmed = row.value.trim();
    let parsed: unknown = trimmed;
    if (trimmed === 'true') parsed = true;
    else if (trimmed === 'false') parsed = false;
    else if (trimmed !== '' && !Number.isNaN(Number(trimmed)) && /^-?\d+(\.\d+)?$/.test(trimmed)) {
      parsed = Number(trimmed);
    } else if (trimmed.startsWith('[') || trimmed.startsWith('{')) {
      try {
        parsed = JSON.parse(trimmed);
      } catch {
        parsed = trimmed;
      }
    }
    result[row.key.trim()] = parsed;
  }
  return result;
};

/**
 * Editor for free-form product attributes stored as JSONB on the backend.
 * Lets admins add/remove arbitrary key-value pairs at runtime - no schema
 * migration required to introduce a new attribute.
 *
 * Value-type inference: "true"/"false" -> boolean, numeric strings -> number,
 * strings starting with [ or { are parsed as JSON, everything else stays string.
 */
export function AttributeEditor({ value, onChange }: AttributeEditorProps) {
  const [rows, setRows] = useState<DraftRow[]>(() => toRows(value));

  const emit = (next: DraftRow[]) => {
    setRows(next);
    onChange(rowsToValue(next));
  };

  const updateRow = (id: string, patch: Partial<DraftRow>) =>
    emit(rows.map((r) => (r.id === id ? { ...r, ...patch } : r)));

  const addRow = () => emit([...rows, { id: newId(), key: '', value: '' }]);

  const removeRow = (id: string) => emit(rows.filter((r) => r.id !== id));

  return (
    <div className="border rounded p-3 bg-light">
      <div className="d-flex justify-content-between align-items-center mb-2">
        <label className="form-label fw-semibold mb-0">Attributes (JSONB)</label>
        <button type="button" className="btn btn-sm btn-outline-primary" onClick={addRow}>
          + Add attribute
        </button>
      </div>

      {rows.length === 0 && (
        <p className="text-muted small mb-0">
          No attributes yet. Click "Add attribute" to define a key/value pair.
        </p>
      )}

      {rows.map((row) => (
        <div className="row g-2 mb-2 align-items-center" key={row.id}>
          <div className="col-12 col-md-4">
            <input
              type="text"
              className="form-control form-control-sm"
              placeholder="key (e.g. color)"
              value={row.key}
              onChange={(e) => updateRow(row.id, { key: e.target.value })}
            />
          </div>
          <div className="col-10 col-md-7">
            <input
              type="text"
              className="form-control form-control-sm"
              placeholder='value (string, number, true/false, or JSON like ["a","b"])'
              value={row.value}
              onChange={(e) => updateRow(row.id, { value: e.target.value })}
            />
          </div>
          <div className="col-2 col-md-1 text-end">
            <button
              type="button"
              className="btn btn-sm btn-outline-danger"
              onClick={() => removeRow(row.id)}
              aria-label={`Remove ${row.key}`}
            >
              &times;
            </button>
          </div>
        </div>
      ))}
    </div>
  );
}

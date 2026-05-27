import { useCurrency } from './CurrencyContext';
import type { Region } from '../types';

const COUNTRY_FLAG: Record<string, string> = {
  BD: '🇧🇩',
  JP: '🇯🇵',
  TH: '🇹🇭',
  US: '🇺🇸',
  GB: '🇬🇧',
  EU: '🇪🇺',
  SG: '🇸🇬',
  AU: '🇦🇺',
  CA: '🇨🇦',
  MY: '🇲🇾',
  PH: '🇵🇭',
  ID: '🇮🇩',
  VN: '🇻🇳',
  KR: '🇰🇷',
  CN: '🇨🇳',
  IN: '🇮🇳',
};

function flag(countryCode: string): string {
  return COUNTRY_FLAG[countryCode.toUpperCase()] ?? '🌐';
}

export function RegionSelector() {
  const { region, regions, setRegion } = useCurrency();

  if (regions.length === 0) return null;

  const label = region ? `${flag(region.countryCode)} ${region.name}` : '🌐 Region';

  return (
    <div className="dropdown">
      <button
        className="btn btn-outline-secondary btn-sm dropdown-toggle d-flex align-items-center gap-1"
        type="button"
        data-bs-toggle="dropdown"
        aria-expanded="false"
        aria-label="Select region"
        style={{ fontSize: '0.82rem', whiteSpace: 'nowrap' }}
      >
        {label}
      </button>
      <ul
        className="dropdown-menu dropdown-menu-end shadow-sm"
        style={{ minWidth: '180px', maxHeight: '320px', overflowY: 'auto' }}
      >
        {regions.map((r: Region) => (
          <li key={r.id}>
            <button
              type="button"
              className={`dropdown-item d-flex align-items-center gap-2 ${region?.id === r.id ? 'active' : ''}`}
              onClick={() => setRegion(r)}
            >
              <span>{flag(r.countryCode)}</span>
              <span>{r.name}</span>
              <span className="ms-auto text-muted small">{r.currencyCode}</span>
            </button>
          </li>
        ))}
      </ul>
    </div>
  );
}

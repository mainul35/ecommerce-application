import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react';
import { currencyService, regionService } from '../services/currencyService';
import type { Currency, Region } from '../types';

interface CurrencyContextValue {
  /** All currencies available to the customer (admin-active set). */
  currencies: Currency[];
  /** The currency the customer is currently viewing prices in. */
  selected: Currency | null;
  /** The base currency stored prices are denominated in. */
  base: Currency | null;
  /** The detected (or admin-configured) region for the visitor; drives product visibility. */
  region: Region | null;
  /** Customer-driven currency override. Persisted in localStorage. */
  setSelectedCode: (code: string) => void;
  /** Convert a price from the base currency to the selected currency. */
  convert: (basePrice: number) => number;
  /** Format a base-currency price in the customer's currency. */
  format: (basePrice: number) => string;
  loading: boolean;
}

const CurrencyContext = createContext<CurrencyContextValue | null>(null);

const STORAGE_KEY = 'preferredCurrencyCode';
/** Region id propagated to storefront product calls so the catalog filters by it. */
const REGION_STORAGE_KEY = 'currentRegionId';

/**
 * Read the customer's resolved region id (set by CurrencyProvider after IP
 * detection or admin-configured fallback). Used by productService to attach
 * a region filter to catalog requests, so customers don't see products
 * restricted to other regions.
 */
export function getCurrentRegionId(): string | null {
  if (typeof localStorage === 'undefined') return null;
  return localStorage.getItem(REGION_STORAGE_KEY);
}

/**
 * Calls a free IP-geolocation service (ipapi.co) to discover the visitor's
 * country code. Returns null on failure so callers can fall back to the
 * base currency without breaking. The lookup is fire-and-forget; we never
 * block the storefront on it.
 */
async function detectCountryCode(): Promise<string | null> {
  try {
    const res = await fetch('https://ipapi.co/country_code/', {
      method: 'GET',
      signal: AbortSignal.timeout(3000),
    });
    if (!res.ok) return null;
    const txt = (await res.text()).trim();
    return /^[A-Z]{2}$/.test(txt) ? txt : null;
  } catch {
    return null;
  }
}

interface ProviderProps {
  children: ReactNode;
}

/**
 * Boot order:
 *   1. Fetch the active currency catalog and the base currency in parallel.
 *   2. If localStorage has a previously chosen code, use it as the selected currency.
 *   3. Otherwise, ask ipapi.co for the country code, look up the matching Region,
 *      and use the region's currency.
 *   4. Fall back to the base currency if anything in step 3 fails.
 *
 * Pricing convention: all prices on the wire are in the base currency.
 * The {@code convert} / {@code format} helpers do the multiplication for
 * display - they never mutate the underlying numbers stored on a product.
 */
export function CurrencyProvider({ children }: Readonly<ProviderProps>) {
  const [currencies, setCurrencies] = useState<Currency[]>([]);
  const [base, setBase] = useState<Currency | null>(null);
  const [region, setRegion] = useState<Region | null>(null);
  const [selectedCode, setSelectedCodeState] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const [list, baseCurrency] = await Promise.all([
          currencyService.listActive(),
          currencyService.getBase().catch(() => null),
        ]);
        if (cancelled) return;
        setCurrencies(list);
        setBase(baseCurrency);

        const stored = localStorage.getItem(STORAGE_KEY);
        if (stored && list.some((c) => c.code === stored)) {
          setSelectedCodeState(stored);
        } else {
          // No stored preference -> try IP detection.
          const country = await detectCountryCode();
          if (cancelled) return;
          if (country) {
            const detectedRegion = await regionService.findByCountry(country);
            if (cancelled) return;
            if (detectedRegion) {
              setRegion(detectedRegion);
              localStorage.setItem(REGION_STORAGE_KEY, detectedRegion.id);
              if (list.some((c) => c.code === detectedRegion.currencyCode)) {
                setSelectedCodeState(detectedRegion.currencyCode);
                return;
              }
            } else {
              localStorage.removeItem(REGION_STORAGE_KEY);
            }
          }
          if (baseCurrency) setSelectedCodeState(baseCurrency.code);
        }
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  const setSelectedCode = useCallback((code: string) => {
    localStorage.setItem(STORAGE_KEY, code);
    setSelectedCodeState(code);
  }, []);

  const selected = useMemo(
    () => currencies.find((c) => c.code === selectedCode) ?? base,
    [currencies, selectedCode, base],
  );

  const convert = useCallback(
    (basePrice: number) => {
      const rate = Number(selected?.exchangeRate ?? 1);
      return basePrice * rate;
    },
    [selected],
  );

  const format = useCallback(
    (basePrice: number) => {
      const sym = selected?.symbol ?? '$';
      const code = selected?.code ?? 'USD';
      const converted = convert(basePrice);
      // JPY/IDR-style "no decimals" currencies have integer rate >= 1; keep the
      // simple 2dp default for everything (matches how the catalog used to render).
      const decimals = code === 'JPY' || code === 'BDT' ? 0 : 2;
      return `${sym}${converted.toLocaleString(undefined, {
        minimumFractionDigits: decimals,
        maximumFractionDigits: decimals,
      })}`;
    },
    [selected, convert],
  );

  const value = useMemo<CurrencyContextValue>(
    () => ({
      currencies,
      selected,
      base,
      region,
      setSelectedCode,
      convert,
      format,
      loading,
    }),
    [currencies, selected, base, region, setSelectedCode, convert, format, loading],
  );

  return <CurrencyContext.Provider value={value}>{children}</CurrencyContext.Provider>;
}

export function useCurrency(): CurrencyContextValue {
  const ctx = useContext(CurrencyContext);
  if (!ctx) {
    throw new Error('useCurrency must be used inside <CurrencyProvider>');
  }
  return ctx;
}

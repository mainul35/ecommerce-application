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
  currencies: Currency[];
  selected: Currency | null;
  base: Currency | null;
  region: Region | null;
  regions: Region[];
  setSelectedCode: (code: string) => void;
  setRegion: (region: Region) => void;
  convert: (basePrice: number) => number;
  format: (basePrice: number) => string;
  loading: boolean;
}

const CurrencyContext = createContext<CurrencyContextValue | null>(null);

const STORAGE_KEY = 'preferredCurrencyCode';
const REGION_STORAGE_KEY = 'currentRegionId';

export function getCurrentRegionId(): string | null {
  if (typeof localStorage === 'undefined') return null;
  return localStorage.getItem(REGION_STORAGE_KEY);
}

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

function tryStoredRegion(
  list: Currency[],
  regions: Region[],
  onRegion: (r: Region) => void,
): string | null {
  const storedRegionId = localStorage.getItem(REGION_STORAGE_KEY);
  if (!storedRegionId) return null;
  const storedRegion = regions.find((r) => r.id === storedRegionId);
  if (!storedRegion) return null;
  onRegion(storedRegion);
  const storedCurrency = localStorage.getItem(STORAGE_KEY);
  if (storedCurrency && list.some((c) => c.code === storedCurrency)) return storedCurrency;
  if (list.some((c) => c.code === storedRegion.currencyCode)) return storedRegion.currencyCode;
  return null;
}

async function tryIpDetection(
  list: Currency[],
  onRegion: (r: Region) => void,
): Promise<string | null> {
  const country = await detectCountryCode();
  if (!country) return null;
  const detectedRegion = await regionService.findByCountry(country).catch(() => null);
  if (!detectedRegion) {
    localStorage.removeItem(REGION_STORAGE_KEY);
    return null;
  }
  onRegion(detectedRegion);
  localStorage.setItem(REGION_STORAGE_KEY, detectedRegion.id);
  return list.some((c) => c.code === detectedRegion.currencyCode) ? detectedRegion.currencyCode : null;
}

async function resolveCurrencyCode(
  list: Currency[],
  baseCurrency: Currency | null,
  regions: Region[],
  onRegion: (r: Region) => void,
): Promise<string | null> {
  const fromRegion = tryStoredRegion(list, regions, onRegion);
  if (fromRegion) return fromRegion;

  const storedCurrency = localStorage.getItem(STORAGE_KEY);
  if (storedCurrency && list.some((c) => c.code === storedCurrency)) return storedCurrency;

  const fromIp = await tryIpDetection(list, onRegion);
  if (fromIp) return fromIp;

  return baseCurrency?.code ?? null;
}

interface ProviderProps {
  children: ReactNode;
}

export function CurrencyProvider({ children }: Readonly<ProviderProps>) {
  const [currencies, setCurrencies] = useState<Currency[]>([]);
  const [base, setBase] = useState<Currency | null>(null);
  const [currentRegion, setCurrentRegion] = useState<Region | null>(null);
  const [allRegions, setAllRegions] = useState<Region[]>([]);
  const [currencyCode, setCurrencyCode] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const [list, baseCurrency, regions] = await Promise.all([
          currencyService.listActive(),
          currencyService.getBase().catch(() => null),
          regionService.listActive().catch(() => [] as Region[]),
        ]);
        if (cancelled) return;
        setCurrencies(list);
        setBase(baseCurrency);
        setAllRegions(regions);

        const code = await resolveCurrencyCode(list, baseCurrency, regions, (r) => {
          if (!cancelled) setCurrentRegion(r);
        });
        if (!cancelled && code) setCurrencyCode(code);
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => { cancelled = true; };
  }, []);

  const setSelectedCode = useCallback((code: string) => {
    localStorage.setItem(STORAGE_KEY, code);
    setCurrencyCode(code);
  }, []);

  const setRegion = useCallback(
    (region: Region) => {
      setCurrentRegion(region);
      localStorage.setItem(REGION_STORAGE_KEY, region.id);
      if (currencies.some((c) => c.code === region.currencyCode)) {
        localStorage.setItem(STORAGE_KEY, region.currencyCode);
        setCurrencyCode(region.currencyCode);
      }
    },
    [currencies],
  );

  const selected = useMemo(
    () => currencies.find((c) => c.code === currencyCode) ?? base,
    [currencies, currencyCode, base],
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
      region: currentRegion,
      regions: allRegions,
      setSelectedCode,
      setRegion,
      convert,
      format,
      loading,
    }),
    [currencies, selected, base, currentRegion, allRegions, setSelectedCode, setRegion, convert, format, loading],
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

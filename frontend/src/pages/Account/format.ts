/** Formats a numeric amount as localized currency, e.g. 12.5 -> "$12.50". */
export const formatMoney = (n: number, currency = 'USD'): string =>
  Number(n).toLocaleString(undefined, {
    style: 'currency',
    currency,
  });

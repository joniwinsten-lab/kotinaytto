import { addDays, format } from "date-fns";
import { toZonedTime } from "date-fns-tz";

const TZ = "Europe/Helsinki";

/** Helsinki-kalenterin tänään + seuraavat 7 päivää (yhteensä 8 päivää). */
export function helDateRange8(): string[] {
  const z = toZonedTime(new Date(), TZ);
  return Array.from({ length: 8 }, (_, i) => format(addDays(z, i), "yyyy-MM-dd"));
}

const SHORT_WD = ["Su", "Ma", "Ti", "Ke", "To", "Pe", "La"];

export function weekdayShortFi(isoDate: string): string {
  const [y, mo, d] = isoDate.split("-").map(Number);
  const dt = new Date(Date.UTC(y, mo - 1, d, 12, 0, 0));
  return SHORT_WD[dt.getUTCDay()] ?? "";
}

export function formatIsoShortFi(isoDate: string): string {
  const [y, mo, d] = isoDate.split("-").map(Number);
  if (!y || !mo || !d) return isoDate;
  return `${d}.${mo}.`;
}

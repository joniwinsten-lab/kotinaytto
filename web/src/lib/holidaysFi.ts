import Holidays from "date-holidays";

const hd = new Holidays("FI");

/** Päivämäärä "YYYY-MM-DD". Suomen sunnuntai tai virallinen pyhäpäivä. Lauantai ei ole automaattinen vapaa. */
export function isFinnishSundayOrHoliday(isoDate: string): boolean {
  const [y, mo, d] = isoDate.split("-").map(Number);
  if (!y || !mo || !d) return false;
  const dt = new Date(Date.UTC(y, mo - 1, d, 12, 0, 0));
  const dow = dt.getUTCDay();
  if (dow === 0) return true;
  const h = hd.isHoliday(dt);
  return Boolean(h);
}

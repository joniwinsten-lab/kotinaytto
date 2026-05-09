import Holidays from "date-holidays";

const hd = new Holidays("FI");
const HOLIDAY_NAME_FI_MAP: Record<string, string> = {
  "Mother's Day": "Äitienpäivä",
  "New Year's Day": "Uudenvuodenpäivä",
  "Epiphany": "Loppiainen",
  "Good Friday": "Pitkäperjantai",
  "Easter Sunday": "Pääsiäispäivä",
  "Easter Monday": "2. pääsiäispäivä",
  "May Day": "Vappu",
  Ascension: "Helatorstai",
  Pentecost: "Helluntaipäivä",
  "Midsummer Day": "Juhannuspäivä",
  "All Saints' Day": "Pyhäinpäivä",
  "Independence Day": "Itsenäisyyspäivä",
  "Christmas Day": "Joulupäivä",
  "Boxing Day": "Tapaninpäivä",
};

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

/** Esim. "Äitienpäivä", "Sunnuntai" tai null jos ei automaattinen vapaa. */
export function holidayOrSundayLabelFi(isoDate: string): string | null {
  const [y, mo, d] = isoDate.split("-").map(Number);
  if (!y || !mo || !d) return null;
  const dt = new Date(Date.UTC(y, mo - 1, d, 12, 0, 0));
  const dow = dt.getUTCDay();
  const h = hd.isHoliday(dt);
  if (h) {
    const row = Array.isArray(h) ? h[0] : h;
    const name = String((row as { name?: string })?.name ?? "").trim();
    if (!name) return "Arkipyhä";
    return HOLIDAY_NAME_FI_MAP[name] ?? name;
  }
  if (dow === 0) return "Sunnuntai";
  return null;
}

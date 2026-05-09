import { useCallback, useEffect, useMemo, useState } from "react";
import { Link, useOutletContext } from "react-router-dom";
import type { EditorCaps } from "../lib/editorSession";
import { formatIsoShortFi, helDateRange8, weekdayShortFi } from "../lib/helDates";
import { isFinnishSundayOrHoliday } from "../lib/holidaysFi";
import { supabase } from "../lib/supabase";

type Entry = {
  id: string;
  person_slug: string;
  entry_date: string;
  title: string;
  notes: string | null;
};

const SHIFT_RE = /^(\d{1,2}:\d{2})\s*[–\-]\s*(\d{1,2}:\d{2})\s*$/;
const EN_DASH = "\u2013";

const BEEN_DEFAULT_LS = "kotinaytto_been_shift_v1";

function loadBeenDefaults(): { start: string; end: string } {
  try {
    const j = JSON.parse(localStorage.getItem(BEEN_DEFAULT_LS) ?? "{}") as { start?: string; end?: string };
    if (j.start && j.end) return { start: j.start, end: j.end };
  } catch {
    /* ignore */
  }
  return { start: "08:00", end: "14:00" };
}

function saveBeenDefaults(start: string, end: string) {
  localStorage.setItem(BEEN_DEFAULT_LS, JSON.stringify({ start, end }));
}

function parseTitle(title: string): { kind: "free" } | { kind: "shift"; a: string; b: string } | { kind: "legacy"; text: string } {
  const t = title.trim();
  if (!t) return { kind: "shift", a: "", b: "" };
  if (t.toLowerCase() === "vapaa") return { kind: "free" };
  const m = t.match(SHIFT_RE);
  if (m) return { kind: "shift", a: m[1], b: m[2] };
  return { kind: "legacy", text: t };
}

/** Muuntaa "8:15" → "08:15" time-inputille */
function toTimeInput(hm: string): string {
  const m = hm.trim().match(/^(\d{1,2}):(\d{2})$/);
  if (!m) return "";
  const h = Number(m[1]);
  const min = m[2];
  if (h < 0 || h > 23) return "";
  return `${String(h).padStart(2, "0")}:${min}`;
}

function weekdayNumberMon0(iso: string): number {
  const [y, mo, d] = iso.split("-").map(Number);
  const dt = new Date(Date.UTC(y, mo - 1, d, 12, 0, 0));
  const sun0 = dt.getUTCDay();
  return sun0 === 0 ? 6 : sun0 - 1;
}

export default function HallintaWeekSchedulePage() {
  const caps = useOutletContext<EditorCaps>();
  const personSlug = caps.personSlug;
  const scheduleSecret = caps.scheduleSecret;

  const dates = useMemo(() => helDateRange8(), []);

  const [entries, setEntries] = useState<Entry[]>([]);
  const [drafts, setDrafts] = useState<Record<string, { start: string; end: string; legacy: string }>>({});
  const [error, setError] = useState<string | null>(null);
  const [busyDate, setBusyDate] = useState<string | null>(null);

  const heading =
    personSlug === "been" ? "Bee – koulu" : personSlug === "maija" ? "Maija – työvuorot" : "Joni – työpäivät";

  const byDate = useMemo(() => {
    const m = new Map<string, Entry>();
    entries.forEach((e) => {
      if (e.person_slug === personSlug) m.set(e.entry_date, e);
    });
    return m;
  }, [entries, personSlug]);

  const initDraftsFromEntries = useCallback(
    (rows: Entry[]) => {
      const map = new Map<string, Entry>();
      rows.forEach((e) => {
        if (e.person_slug === personSlug) map.set(e.entry_date, e);
      });
      const next: Record<string, { start: string; end: string; legacy: string }> = {};
      const defaults = loadBeenDefaults();
      for (const iso of dates) {
        const row = map.get(iso);
        const free = isFinnishSundayOrHoliday(iso);
        if (free) continue;
        const parsed = parseTitle(row?.title ?? "");
        if (parsed.kind === "free") {
          next[iso] = { start: "", end: "", legacy: "" };
        } else if (parsed.kind === "shift") {
          let s = toTimeInput(parsed.a);
          let e = toTimeInput(parsed.b);
          if (!s && !e && personSlug === "been") {
            s = toTimeInput(defaults.start);
            e = toTimeInput(defaults.end);
          }
          next[iso] = { start: s, end: e, legacy: "" };
        } else {
          next[iso] = { start: "", end: "", legacy: parsed.text };
        }
      }
      setDrafts(next);
    },
    [dates, personSlug],
  );

  const load = useCallback(async () => {
    if (!supabase) return;
    const { data, error: e } = await supabase
      .from("schedule_entries")
      .select("*")
      .eq("person_slug", personSlug)
      .gte("entry_date", dates[0])
      .lte("entry_date", dates[dates.length - 1])
      .order("entry_date", { ascending: true });
    if (e) {
      setError(e.message);
      return;
    }
    const rows = (data as Entry[]) ?? [];
    setEntries(rows);
    initDraftsFromEntries(rows);

    for (const iso of dates) {
      if (!isFinnishSundayOrHoliday(iso)) continue;
      const row = rows.find((r) => r.entry_date === iso && r.person_slug === personSlug);
      if (row?.title === "Vapaa") continue;
      setError(null);
      await supabase.rpc("schedule_upsert", {
        p_secret: scheduleSecret,
        p_person_slug: personSlug,
        p_entry_id: row?.id ?? null,
        p_entry_date: iso,
        p_title: "Vapaa",
        p_notes: null,
      });
    }

    const { data: data2, error: e2 } = await supabase
      .from("schedule_entries")
      .select("*")
      .eq("person_slug", personSlug)
      .gte("entry_date", dates[0])
      .lte("entry_date", dates[dates.length - 1])
      .order("entry_date", { ascending: true });
    if (!e2 && data2) {
      const rows2 = data2 as Entry[];
      setEntries(rows2);
      initDraftsFromEntries(rows2);
    }
  }, [dates, initDraftsFromEntries, personSlug, scheduleSecret]);

  useEffect(() => {
    void load();
  }, [load]);

  const saveDate = async (iso: string) => {
    if (!supabase || isFinnishSundayOrHoliday(iso)) return;
    setBusyDate(iso);
    setError(null);
    try {
      const row = byDate.get(iso);
      const d = drafts[iso] ?? { start: "", end: "", legacy: "" };
      let title: string;
      if (d.legacy.trim()) {
        title = d.legacy.trim();
      } else {
        const s = d.start.trim();
        const en = d.end.trim();
        if (!s && !en) {
          if (row?.id) {
            const { error: delE } = await supabase.rpc("schedule_delete", {
              p_secret: scheduleSecret,
              p_person_slug: personSlug,
              p_entry_id: row.id,
            });
            if (delE) throw delE;
          }
          await load();
          return;
        }
        title = `${s}${EN_DASH}${en}`;
      }

      const { error: upE } = await supabase.rpc("schedule_upsert", {
        p_secret: scheduleSecret,
        p_person_slug: personSlug,
        p_entry_id: row?.id ?? null,
        p_entry_date: iso,
        p_title: title,
        p_notes: null,
      });
      if (upE) throw upE;

      const dowMon0 = weekdayNumberMon0(iso);
      if (personSlug === "been" && dowMon0 >= 0 && dowMon0 <= 4 && !d.legacy.trim()) {
        const s = d.start.trim();
        const en = d.end.trim();
        if (s && en) saveBeenDefaults(s, en);
      }

      await load();
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Tallennus epäonnistui");
    } finally {
      setBusyDate(null);
    }
  };

  return (
    <div className="page">
      <h1>{heading}</h1>
      <p className="muted">
        Näytetään tänään ja seuraavat 7 päivää. Sunnuntai ja arkipyhät ovat automaattisesti vapaita. TV näyttää vuoroista
        vain kellonajat.
      </p>
      <p className="muted">
        <Link to="/hallinta">← Takaisin</Link>
      </p>

      <div className="stack">
        {dates.map((iso) => {
          const free = isFinnishSundayOrHoliday(iso);
          const wd = weekdayShortFi(iso);
          const shortD = formatIsoShortFi(iso);
          return (
            <div key={iso} className="card schedule-row">
              <div className="schedule-row-head">
                <strong>
                  {wd} {shortD}
                </strong>
                {free && <span className="pill vapaa">Vapaa</span>}
              </div>
              {!free && (
                <>
                  {drafts[iso]?.legacy ? (
                    <label className="muted small-label">
                      Merkintä (vanha muoto)
                      <input
                        type="text"
                        value={drafts[iso]?.legacy ?? ""}
                        onChange={(e) =>
                          setDrafts((prev) => ({
                            ...prev,
                            [iso]: { ...(prev[iso] ?? { start: "", end: "", legacy: "" }), legacy: e.target.value },
                          }))
                        }
                      />
                    </label>
                  ) : (
                    <div className="row time-row">
                      <label className="muted small-label">
                        Aloitus
                        <input
                          type="time"
                          value={drafts[iso]?.start ?? ""}
                          onChange={(e) =>
                            setDrafts((prev) => ({
                              ...prev,
                              [iso]: { ...(prev[iso] ?? { start: "", end: "", legacy: "" }), start: e.target.value },
                            }))
                          }
                        />
                      </label>
                      <label className="muted small-label">
                        Lopetus
                        <input
                          type="time"
                          value={drafts[iso]?.end ?? ""}
                          onChange={(e) =>
                            setDrafts((prev) => ({
                              ...prev,
                              [iso]: { ...(prev[iso] ?? { start: "", end: "", legacy: "" }), end: e.target.value },
                            }))
                          }
                        />
                      </label>
                      <button type="button" className="touch-btn" disabled={busyDate === iso} onClick={() => void saveDate(iso)}>
                        {busyDate === iso ? "…" : "Tallenna"}
                      </button>
                    </div>
                  )}
                  {drafts[iso]?.legacy ? (
                    <button type="button" className="touch-btn secondary slim-top" disabled={busyDate === iso} onClick={() => void saveDate(iso)}>
                      Tallenna
                    </button>
                  ) : null}
                </>
              )}
            </div>
          );
        })}
      </div>
      {error && (
        <p style={{ color: "#ffab91" }} role="alert">
          {error}
        </p>
      )}
    </div>
  );
}

import { useCallback, useEffect, useMemo, useState } from "react";
import { Link, useOutletContext } from "react-router-dom";
import type { EditorCaps } from "../lib/editorSession";
import { formatIsoShortFi, helDateRange8, weekdayShortFi } from "../lib/helDates";
import { holidayOrSundayLabelFi, isFinnishSundayOrHoliday } from "../lib/holidaysFi";
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
const EXTRA_NOTES_PREFIX = "koti_extra:";
const JONI_LOCATION_PREFIX = "koti_joni_location:";
const MAIJA_LOCATION_PREFIX = "koti_maija_location:";

const BEE_SHIFT_LS = "kotinaytto_been_shift_v1";

function loadBeeShiftDefaults(): { start: string; end: string } {
  try {
    const j = JSON.parse(localStorage.getItem(BEE_SHIFT_LS) ?? "{}") as { start?: string; end?: string };
    if (j.start && j.end) return { start: j.start, end: j.end };
  } catch {
    /* ignore */
  }
  return { start: "08:00", end: "14:00" };
}

function saveBeeShiftDefaults(start: string, end: string) {
  localStorage.setItem(BEE_SHIFT_LS, JSON.stringify({ start, end }));
}

function parseTitle(title: string): { kind: "free" } | { kind: "shift"; a: string; b: string } | { kind: "legacy"; text: string } {
  const t = title.trim();
  if (!t) return { kind: "shift", a: "", b: "" };
  if (t.toLowerCase().startsWith("vapaa")) return { kind: "free" };
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

function parseExtraFromNotes(notes: string | null): { showOnTv: boolean; label: string; time: string } {
  if (!notes || !notes.startsWith(EXTRA_NOTES_PREFIX)) return { showOnTv: false, label: "", time: "" };
  try {
    const obj = JSON.parse(notes.slice(EXTRA_NOTES_PREFIX.length)) as {
      showOnTv?: boolean;
      label?: string;
      time?: string;
    };
    return {
      showOnTv: Boolean(obj.showOnTv),
      label: (obj.label ?? "").trim(),
      time: (obj.time ?? "").trim(),
    };
  } catch {
    return { showOnTv: false, label: "", time: "" };
  }
}

function buildExtraNotes(showOnTv: boolean, label: string, time: string): string | null {
  const cleanLabel = label.trim();
  const cleanTime = time.trim();
  if (!showOnTv && !cleanLabel && !cleanTime) return null;
  return `${EXTRA_NOTES_PREFIX}${JSON.stringify({ showOnTv, label: cleanLabel, time: cleanTime })}`;
}

type JoniLocation = "" | "home" | "office" | "tampere";

function parseJoniLocationFromNotes(notes: string | null): JoniLocation {
  if (!notes || !notes.startsWith(JONI_LOCATION_PREFIX)) return "";
  const v = notes.slice(JONI_LOCATION_PREFIX.length).trim().toLowerCase();
  if (v === "home" || v === "office" || v === "tampere") return v;
  return "";
}

function buildJoniLocationNotes(loc: JoniLocation): string | null {
  if (!loc) return null;
  return `${JONI_LOCATION_PREFIX}${loc}`;
}

type MaijaPreset = "" | "arkadia" | "kasarmitori" | "taivallahti";

function parseMaijaFromNotes(notes: string | null): { preset: MaijaPreset; custom: string } {
  if (!notes || !notes.startsWith(MAIJA_LOCATION_PREFIX)) return { preset: "", custom: "" };
  try {
    const obj = JSON.parse(notes.slice(MAIJA_LOCATION_PREFIX.length)) as { preset?: string; custom?: string };
    const p = (obj.preset ?? "").trim().toLowerCase();
    const preset: MaijaPreset =
      p === "arkadia" || p === "kasarmitori" || p === "taivallahti" ? (p as MaijaPreset) : "";
    return { preset, custom: (obj.custom ?? "").trim() };
  } catch {
    return { preset: "", custom: "" };
  }
}

function buildMaijaLocationNotes(preset: MaijaPreset, custom: string): string | null {
  const c = custom.trim();
  if (!preset && !c) return null;
  return `${MAIJA_LOCATION_PREFIX}${JSON.stringify({ preset: preset || "", custom: c })}`;
}

type Draft = {
  start: string;
  end: string;
  legacy: string;
  manualFree: boolean;
  extraLabel: string;
  extraTime: string;
  showExtraOnTv: boolean;
  joniLocation: JoniLocation;
  maijaPreset: MaijaPreset;
  maijaCustom: string;
};

function emptyDraft(): Draft {
  return {
    start: "",
    end: "",
    legacy: "",
    manualFree: false,
    extraLabel: "",
    extraTime: "",
    showExtraOnTv: false,
    joniLocation: "",
    maijaPreset: "",
    maijaCustom: "",
  };
}

export default function HallintaWeekSchedulePage() {
  const caps = useOutletContext<EditorCaps>();
  const personSlug = caps.personSlug;
  const scheduleSecret = caps.scheduleSecret;

  const dates = useMemo(() => helDateRange8(), []);

  const [entries, setEntries] = useState<Entry[]>([]);
  const [drafts, setDrafts] = useState<Record<string, Draft>>({});
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
      const next: Record<string, Draft> = {};
      const defaults = loadBeeShiftDefaults();
      for (const iso of dates) {
        const row = map.get(iso);
        const free = isFinnishSundayOrHoliday(iso);
        if (free) continue;
        const parsed = parseTitle(row?.title ?? "");
        const extra = parseExtraFromNotes(row?.notes ?? null);
        const joniLocation = parseJoniLocationFromNotes(row?.notes ?? null);
        const maijaLoc = parseMaijaFromNotes(row?.notes ?? null);
        if (parsed.kind === "free") {
          next[iso] = {
            ...emptyDraft(),
            manualFree: true,
            extraLabel: extra.label,
            extraTime: extra.time,
            showExtraOnTv: extra.showOnTv,
            joniLocation,
            maijaPreset: maijaLoc.preset,
            maijaCustom: maijaLoc.custom,
          };
        } else if (parsed.kind === "shift") {
          let s = toTimeInput(parsed.a);
          let e = toTimeInput(parsed.b);
          if (!s && !e && personSlug === "been") {
            s = toTimeInput(defaults.start);
            e = toTimeInput(defaults.end);
          }
          next[iso] = {
            ...emptyDraft(),
            start: s,
            end: e,
            extraLabel: extra.label,
            extraTime: extra.time,
            showExtraOnTv: extra.showOnTv,
            joniLocation,
            maijaPreset: maijaLoc.preset,
            maijaCustom: maijaLoc.custom,
          };
        } else {
          next[iso] = {
            ...emptyDraft(),
            legacy: parsed.text,
            extraLabel: extra.label,
            extraTime: extra.time,
            showExtraOnTv: extra.showOnTv,
            joniLocation,
            maijaPreset: maijaLoc.preset,
            maijaCustom: maijaLoc.custom,
          };
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
      if (row?.title?.trim()?.toLowerCase()?.startsWith("vapaa")) continue;
      const freeLabel = holidayOrSundayLabelFi(iso) ?? "Vapaa"
      setError(null);
      await supabase.rpc("schedule_upsert", {
        p_secret: scheduleSecret,
        p_person_slug: personSlug,
        p_entry_id: row?.id ?? null,
        p_entry_date: iso,
        p_title: `Vapaa — ${freeLabel}`,
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
      const d = drafts[iso] ?? emptyDraft();
      let title: string;
      if (d.manualFree) {
        title = "Vapaa";
      } else if (d.legacy.trim()) {
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
        p_notes:
          personSlug === "been"
            ? buildExtraNotes(d.showExtraOnTv, d.extraLabel, d.extraTime)
            : personSlug === "joni"
              ? buildJoniLocationNotes(d.joniLocation)
              : personSlug === "maija"
                ? buildMaijaLocationNotes(d.maijaPreset, d.maijaCustom)
                : null,
      });
      if (upE) throw upE;

      const dowMon0 = weekdayNumberMon0(iso);
      if (personSlug === "been" && dowMon0 >= 0 && dowMon0 <= 4 && !d.legacy.trim()) {
        const s = d.start.trim();
        const en = d.end.trim();
        if (s && en) saveBeeShiftDefaults(s, en);
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
      <p className="muted">Näytetään tänään ja seuraavat 7 päivää. Sunnuntai ja arkipyhät ovat automaattisesti vapaita.</p>
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
                    <>
                      <label className="row muted" style={{ gap: 6 }}>
                        <input
                          type="checkbox"
                          checked={Boolean(drafts[iso]?.manualFree)}
                          onChange={(e) =>
                            setDrafts((prev) => ({
                              ...prev,
                              [iso]: {
                                ...(prev[iso] ?? emptyDraft()),
                                manualFree: e.target.checked,
                              },
                            }))
                          }
                        />
                        Vapaa
                      </label>
                      <label className="muted small-label">
                        Merkintä (vanha muoto)
                        <input
                          type="text"
                          value={drafts[iso]?.legacy ?? ""}
                          disabled={Boolean(drafts[iso]?.manualFree)}
                          onChange={(e) =>
                            setDrafts((prev) => ({
                              ...prev,
                              [iso]: {
                                ...(prev[iso] ?? emptyDraft()),
                                legacy: e.target.value,
                              },
                            }))
                          }
                        />
                      </label>
                    </>
                  ) : (
                    <>
                      <div className="schedule-main-row">
                        <label className="row muted schedule-free-toggle" style={{ gap: 6 }}>
                          <input
                            type="checkbox"
                            checked={Boolean(drafts[iso]?.manualFree)}
                            onChange={(e) =>
                              setDrafts((prev) => ({
                                ...prev,
                                [iso]: {
                                  ...(prev[iso] ?? emptyDraft()),
                                  manualFree: e.target.checked,
                                },
                              }))
                            }
                          />
                          Vapaa
                        </label>
                        <div className="row time-row schedule-time-pair">
                          <label className="muted small-label">
                            Aloitus
                            <input
                              type="time"
                              value={drafts[iso]?.start ?? ""}
                              disabled={Boolean(drafts[iso]?.manualFree)}
                              onChange={(e) =>
                                setDrafts((prev) => ({
                                  ...prev,
                                  [iso]: {
                                    ...(prev[iso] ?? emptyDraft()),
                                    start: e.target.value,
                                  },
                                }))
                              }
                            />
                          </label>
                          <label className="muted small-label">
                            Lopetus
                            <input
                              type="time"
                              value={drafts[iso]?.end ?? ""}
                              disabled={Boolean(drafts[iso]?.manualFree)}
                              onChange={(e) =>
                                setDrafts((prev) => ({
                                  ...prev,
                                  [iso]: {
                                    ...(prev[iso] ?? emptyDraft()),
                                    end: e.target.value,
                                  },
                                }))
                              }
                            />
                          </label>
                        </div>
                      </div>
                      {personSlug === "maija" && (
                        <div className="maija-location-block">
                          <div className="joni-location-row">
                            <span className="muted">Työpaikka:</span>
                            <label className="row muted" style={{ gap: 6 }}>
                              <input
                                type="checkbox"
                                disabled={Boolean(drafts[iso]?.manualFree)}
                                checked={drafts[iso]?.maijaPreset === "arkadia"}
                                onChange={() =>
                                  setDrafts((prev) => ({
                                    ...prev,
                                    [iso]: {
                                      ...(prev[iso] ?? emptyDraft()),
                                      maijaPreset: prev[iso]?.maijaPreset === "arkadia" ? "" : "arkadia",
                                    },
                                  }))
                                }
                              />
                              Arkadia
                            </label>
                            <label className="row muted" style={{ gap: 6 }}>
                              <input
                                type="checkbox"
                                disabled={Boolean(drafts[iso]?.manualFree)}
                                checked={drafts[iso]?.maijaPreset === "kasarmitori"}
                                onChange={() =>
                                  setDrafts((prev) => ({
                                    ...prev,
                                    [iso]: {
                                      ...(prev[iso] ?? emptyDraft()),
                                      maijaPreset: prev[iso]?.maijaPreset === "kasarmitori" ? "" : "kasarmitori",
                                    },
                                  }))
                                }
                              />
                              Kasarmitori
                            </label>
                            <label className="row muted" style={{ gap: 6 }}>
                              <input
                                type="checkbox"
                                disabled={Boolean(drafts[iso]?.manualFree)}
                                checked={drafts[iso]?.maijaPreset === "taivallahti"}
                                onChange={() =>
                                  setDrafts((prev) => ({
                                    ...prev,
                                    [iso]: {
                                      ...(prev[iso] ?? emptyDraft()),
                                      maijaPreset: prev[iso]?.maijaPreset === "taivallahti" ? "" : "taivallahti",
                                    },
                                  }))
                                }
                              />
                              Taivallahti
                            </label>
                          </div>
                          <label className="muted small-label">
                            Muu / tarkenne (vapaa teksti)
                            <input
                              type="text"
                              placeholder="esim. etäkokous, vierailu…"
                              value={drafts[iso]?.maijaCustom ?? ""}
                              disabled={Boolean(drafts[iso]?.manualFree)}
                              onChange={(e) =>
                                setDrafts((prev) => ({
                                  ...prev,
                                  [iso]: {
                                    ...(prev[iso] ?? emptyDraft()),
                                    maijaCustom: e.target.value,
                                  },
                                }))
                              }
                            />
                          </label>
                        </div>
                      )}
                      {personSlug === "joni" && (
                        <div className="joni-location-row" style={{ marginTop: 10 }}>
                          <span className="muted">Paikka:</span>
                          <label className="row muted" style={{ gap: 6 }}>
                            <input
                              type="checkbox"
                              disabled={Boolean(drafts[iso]?.manualFree)}
                              checked={drafts[iso]?.joniLocation === "home"}
                              onChange={() =>
                                setDrafts((prev) => ({
                                  ...prev,
                                  [iso]: {
                                    ...(prev[iso] ?? emptyDraft()),
                                    joniLocation: prev[iso]?.joniLocation === "home" ? "" : "home",
                                  },
                                }))
                              }
                            />
                            Kotona
                          </label>
                          <label className="row muted" style={{ gap: 6 }}>
                            <input
                              type="checkbox"
                              disabled={Boolean(drafts[iso]?.manualFree)}
                              checked={drafts[iso]?.joniLocation === "office"}
                              onChange={() =>
                                setDrafts((prev) => ({
                                  ...prev,
                                  [iso]: {
                                    ...(prev[iso] ?? emptyDraft()),
                                    joniLocation: prev[iso]?.joniLocation === "office" ? "" : "office",
                                  },
                                }))
                              }
                            />
                            Toimistolla
                          </label>
                          <label className="row muted" style={{ gap: 6 }}>
                            <input
                              type="checkbox"
                              disabled={Boolean(drafts[iso]?.manualFree)}
                              checked={drafts[iso]?.joniLocation === "tampere"}
                              onChange={() =>
                                setDrafts((prev) => ({
                                  ...prev,
                                  [iso]: {
                                    ...(prev[iso] ?? emptyDraft()),
                                    joniLocation: prev[iso]?.joniLocation === "tampere" ? "" : "tampere",
                                  },
                                }))
                              }
                            />
                            Tampereella
                          </label>
                        </div>
                      )}
                      {personSlug === "been" && (
                        <div className="bee-extra-row" style={{ marginTop: 10 }}>
                          <label className="muted small-label">
                            Treenit / muu
                            <input
                              type="text"
                              value={drafts[iso]?.extraLabel ?? ""}
                              onChange={(e) =>
                                setDrafts((prev) => ({
                                  ...prev,
                                  [iso]: {
                                    ...(prev[iso] ?? emptyDraft()),
                                    extraLabel: e.target.value,
                                  },
                                }))
                              }
                            />
                          </label>
                          <label className="muted small-label">
                            Aika
                            <input
                              type="text"
                              placeholder="esim. 17:00–18:30"
                              value={drafts[iso]?.extraTime ?? ""}
                              onChange={(e) =>
                                setDrafts((prev) => ({
                                  ...prev,
                                  [iso]: {
                                    ...(prev[iso] ?? emptyDraft()),
                                    extraTime: e.target.value,
                                  },
                                }))
                              }
                            />
                          </label>
                          <label className="row muted" style={{ gap: 6, minWidth: 210 }}>
                            <input
                              type="checkbox"
                              checked={Boolean(drafts[iso]?.showExtraOnTv)}
                              onChange={(e) =>
                                setDrafts((prev) => ({
                                  ...prev,
                                  [iso]: {
                                    ...(prev[iso] ?? emptyDraft()),
                                    showExtraOnTv: e.target.checked,
                                  },
                                }))
                              }
                            />
                            Näytä TV:ssä
                          </label>
                        </div>
                      )}
                      <div className="row" style={{ marginTop: 8 }}>
                        <button type="button" className="touch-btn" disabled={busyDate === iso} onClick={() => void saveDate(iso)}>
                          {busyDate === iso ? "…" : "Tallenna"}
                        </button>
                      </div>
                    </>
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

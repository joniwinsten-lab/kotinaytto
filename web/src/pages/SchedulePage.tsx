import { useEffect, useMemo, useState } from "react";
import { Link, useParams, useSearchParams } from "react-router-dom";
import { supabase } from "../lib/supabase";

type Entry = {
  id: string;
  person_slug: string;
  entry_date: string;
  title: string;
  notes: string | null;
};

const SLUGS = new Set(["been", "maija", "joni"]);

function envTokenForSlug(slug: string): string {
  if (slug === "been") return import.meta.env.VITE_TOKEN_BEEN || "";
  if (slug === "maija") return import.meta.env.VITE_TOKEN_MAIJA || "";
  if (slug === "joni") return import.meta.env.VITE_TOKEN_JONI || "";
  return "";
}

export default function SchedulePage() {
  const { slug } = useParams();
  const [sp] = useSearchParams();
  const token = (sp.get("t") || envTokenForSlug(slug || "") || "").trim();
  const person = (slug || "").toLowerCase();

  const [items, setItems] = useState<Entry[]>([]);
  const [error, setError] = useState<string | null>(null);

  const [entryId, setEntryId] = useState<string | null>(null);
  const [date, setDate] = useState(() => new Date().toISOString().slice(0, 10));
  const [title, setTitle] = useState("");
  const [notes, setNotes] = useState("");

  const valid = SLUGS.has(person) && token.length > 0;

  const load = async () => {
    if (!supabase || !SLUGS.has(person)) return;
    const { data, error: e } = await supabase
      .from("schedule_entries")
      .select("*")
      .eq("person_slug", person)
      .order("entry_date", { ascending: true });
    if (e) setError(e.message);
    else setItems((data as Entry[]) ?? []);
  };

  useEffect(() => {
    void load();
  }, [person]);

  const heading = useMemo(() => {
    if (person === "been") return "Been – koulu";
    if (person === "maija") return "Maija – työvuorot";
    if (person === "joni") return "Joni – työpäivät";
    return "Aikataulu";
  }, [person]);

  const save = async () => {
    if (!supabase || !valid) return;
    const t = title.trim();
    if (!t) return;
    setError(null);
    const { error: e } = await supabase.rpc("schedule_upsert", {
      p_secret: token,
      p_person_slug: person,
      p_entry_id: entryId,
      p_entry_date: date,
      p_title: t,
      p_notes: notes.trim() || null,
    });
    if (e) setError(e.message);
    setEntryId(null);
    setTitle("");
    setNotes("");
    await load();
  };

  const edit = (row: Entry) => {
    setEntryId(row.id);
    setDate(row.entry_date);
    setTitle(row.title);
    setNotes(row.notes || "");
  };

  const del = async (id: string) => {
    if (!supabase || !valid) return;
    setError(null);
    const { error: e } = await supabase.rpc("schedule_delete", {
      p_secret: token,
      p_person_slug: person,
      p_entry_id: id,
    });
    if (e) setError(e.message);
    await load();
  };

  if (!SLUGS.has(person)) {
    return (
      <div className="page">
        <p>Tuntematon osoite.</p>
        <Link to="/">← Etusivu</Link>
      </div>
    );
  }

  if (!valid) {
    return (
      <div className="page">
        <h1>{heading}</h1>
        <p className="muted">Lisää osoitteeseen ?t=TOKEN tai aseta VITE_TOKEN_* .env-tiedostoon.</p>
        <Link to="/">← Etusivu</Link>
      </div>
    );
  }

  return (
    <div className="page">
      <h1>{heading}</h1>
      <p className="muted">
        <Link to="/">← Etusivu</Link>
      </p>

      <div className="card">
        <h2>{entryId ? "Muokkaa merkintää" : "Uusi merkintä"}</h2>
        <label className="muted">Päivä</label>
        <input type="date" value={date} onChange={(e) => setDate(e.target.value)} style={{ marginBottom: 10 }} />
        <label className="muted">Otsikko</label>
        <input type="text" value={title} onChange={(e) => setTitle(e.target.value)} style={{ marginBottom: 10 }} />
        <label className="muted">Muistiinpanot</label>
        <textarea rows={3} value={notes} onChange={(e) => setNotes(e.target.value)} style={{ marginBottom: 10 }} />
        <div className="row">
          <button type="button" onClick={() => void save()}>
            Tallenna
          </button>
          {entryId && (
            <button
              type="button"
              className="secondary"
              onClick={() => {
                setEntryId(null);
                setTitle("");
                setNotes("");
              }}
            >
              Peruuta muokkaus
            </button>
          )}
        </div>
        {error && <p style={{ color: "#ffab91" }}>{error}</p>}
      </div>

      <div className="card">
        <h2>Merkinnät</h2>
        <ul className="list">
          {items.map((i) => (
            <li key={i.id} className="row" style={{ justifyContent: "space-between", alignItems: "flex-start" }}>
              <div>
                <div>
                  <strong>{i.entry_date}</strong> {i.title}
                </div>
                {i.notes && <div className="muted">{i.notes}</div>}
              </div>
              <div className="row">
                <button type="button" className="secondary" onClick={() => edit(i)}>
                  Muokkaa
                </button>
                <button type="button" className="secondary" onClick={() => void del(i.id)}>
                  Poista
                </button>
              </div>
            </li>
          ))}
        </ul>
      </div>
    </div>
  );
}

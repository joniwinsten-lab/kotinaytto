import { useEffect, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { supabase } from "../lib/supabase";

type Meal = {
  week_start: string;
  day_index: number;
  meal_text: string;
};

type Wish = {
  id: string;
  wish_text: string;
  created_by: string | null;
};

function useSharedToken(): string {
  const [sp] = useSearchParams();
  return (sp.get("t") || import.meta.env.VITE_TOKEN_SHARED || "").trim();
}

const dayNames = ["Maanantai", "Tiistai", "Keskiviikko", "Torstai", "Perjantai", "Lauantai", "Sunnuntai"];

export default function MealsPage() {
  const token = useSharedToken();
  const [weekStart, setWeekStart] = useState<string>("");
  const [wishes, setWishes] = useState<Wish[]>([]);
  const [drafts, setDrafts] = useState<string[]>(() => Array.from({ length: 7 }, () => ""));
  const [wishText, setWishText] = useState("");
  const [wishBy, setWishBy] = useState("");
  const [error, setError] = useState<string | null>(null);

  const canEdit = token.length > 0;

  const load = async () => {
    if (!supabase) return;
    const { data: ws, error: wsErr } = await supabase.rpc("current_week_start_hel");
    if (wsErr) {
      setError(wsErr.message);
      return;
    }
    const week = String(ws);
    setWeekStart(week);

    const { data: m, error: mErr } = await supabase
      .from("weekly_meals")
      .select("*")
      .eq("week_start", week)
      .order("day_index", { ascending: true });
    if (mErr) setError(mErr.message);
    else {
      const rows = (m as Meal[]) ?? [];
      setDrafts(() => {
        const nextDrafts: string[] = Array.from({ length: 7 }, () => "");
        rows.forEach((r) => {
          if (r.day_index >= 0 && r.day_index <= 6) nextDrafts[r.day_index] = r.meal_text;
        });
        return nextDrafts;
      });
    }

    const { data: w, error: wErr } = await supabase.from("meal_wishes").select("*").order("created_at", {
      ascending: false,
    });
    if (wErr) setError(wErr.message);
    else setWishes(((w as Wish[]) ?? []).slice(0, 50));
  };

  useEffect(() => {
    void load();
  }, []);

  const saveDay = async (dayIndex: number) => {
    if (!supabase || !canEdit || !weekStart) return;
    setError(null);
    const { error: e } = await supabase.rpc("meals_set_week_day", {
      p_secret: token,
      p_week_start: weekStart,
      p_day_index: dayIndex,
      p_meal_text: drafts[dayIndex] ?? "",
    });
    if (e) setError(e.message);
    await load();
  };

  const addWish = async () => {
    if (!supabase || !canEdit) return;
    const t = wishText.trim();
    if (!t) return;
    setError(null);
    const { error: e } = await supabase.rpc("meal_wish_add", {
      p_secret: token,
      p_text: t,
      p_created_by: wishBy.trim() || null,
    });
    if (e) setError(e.message);
    setWishText("");
    await load();
  };

  const delWish = async (id: string) => {
    if (!supabase || !canEdit) return;
    setError(null);
    const { error: e } = await supabase.rpc("meal_wish_delete", { p_secret: token, p_wish_id: id });
    if (e) setError(e.message);
    await load();
  };

  if (!canEdit) {
    return (
      <div className="page">
        <h1>Lounaat</h1>
        <p className="muted">Lisää osoitteeseen ?t=YHTEINEN_TOKEN tai aseta VITE_TOKEN_SHARED.</p>
        <Link to="/">← Etusivu</Link>
      </div>
    );
  }

  return (
    <div className="page">
      <h1>Viikon lounaat</h1>
      <p className="muted">
        Viikko alkaen <strong>{weekStart || "…"}</strong> (Helsinki). <Link to="/">← Etusivu</Link>
      </p>

      <div className="card">
        {dayNames.map((label, idx) => (
          <div key={label} style={{ marginBottom: 12 }}>
            <div className="muted">{label}</div>
            <div className="row">
              <input
                type="text"
                value={drafts[idx] ?? ""}
                onChange={(e) => {
                  const v = e.target.value;
                  setDrafts((prev) => {
                    const copy = [...prev];
                    copy[idx] = v;
                    return copy;
                  });
                }}
              />
              <button type="button" onClick={() => void saveDay(idx)}>
                Tallenna
              </button>
            </div>
          </div>
        ))}
        {error && <p style={{ color: "#ffab91" }}>{error}</p>}
      </div>

      <div className="card">
        <h2>Toiveet</h2>
        <div className="row" style={{ marginBottom: 10 }}>
          <input type="text" placeholder="Uusi toive…" value={wishText} onChange={(e) => setWishText(e.target.value)} />
        </div>
        <div className="row" style={{ marginBottom: 10 }}>
          <input type="text" placeholder="Kuka (valinnainen)" value={wishBy} onChange={(e) => setWishBy(e.target.value)} />
          <button type="button" onClick={() => void addWish()}>
            Lisää toive
          </button>
        </div>
        <ul className="list">
          {wishes.map((w) => (
            <li key={w.id} className="row" style={{ justifyContent: "space-between" }}>
              <span>
                {w.wish_text}
                {w.created_by && <span className="muted"> ({w.created_by})</span>}
              </span>
              <button type="button" className="secondary" onClick={() => void delWish(w.id)}>
                Poista
              </button>
            </li>
          ))}
        </ul>
      </div>
    </div>
  );
}

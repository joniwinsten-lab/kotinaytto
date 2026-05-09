import { useEffect, useState } from "react";
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

const dayNames = ["Maanantai", "Tiistai", "Keskiviikko", "Torstai", "Perjantai", "Lauantai", "Sunnuntai"];

export function WeeklyMealsEditor({ sharedSecret }: { sharedSecret: string }) {
  const [weekStart, setWeekStart] = useState<string>("");
  const [drafts, setDrafts] = useState<string[]>(() => Array.from({ length: 7 }, () => ""));
  const [error, setError] = useState<string | null>(null);

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
  };

  useEffect(() => {
    void load();
  }, []);

  const saveDay = async (dayIndex: number) => {
    if (!supabase || !weekStart) return;
    setError(null);
    const { error: e } = await supabase.rpc("meals_set_week_day", {
      p_secret: sharedSecret,
      p_week_start: weekStart,
      p_day_index: dayIndex,
      p_meal_text: drafts[dayIndex] ?? "",
    });
    if (e) setError(e.message);
    await load();
  };

  return (
    <div className="card">
      <p className="muted">
        Viikko alkaen <strong>{weekStart || "…"}</strong> (Helsinki).
      </p>
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
  );
}

export function MealWishesEditor({ sharedSecret, autoCreatedBy }: { sharedSecret: string; autoCreatedBy?: string }) {
  const [wishes, setWishes] = useState<Wish[]>([]);
  const [wishText, setWishText] = useState("");
  const [wishBy, setWishBy] = useState("");
  const [error, setError] = useState<string | null>(null);

  const load = async () => {
    if (!supabase) return;
    const { data: w, error: wErr } = await supabase.from("meal_wishes").select("*").order("created_at", {
      ascending: false,
    });
    if (wErr) setError(wErr.message);
    else setWishes(((w as Wish[]) ?? []).slice(0, 50));
  };

  useEffect(() => {
    void load();
  }, []);

  const addWish = async () => {
    if (!supabase) return;
    const t = wishText.trim();
    if (!t) return;
    setError(null);
    const { error: e } = await supabase.rpc("meal_wish_add", {
      p_secret: sharedSecret,
      p_text: t,
      p_created_by: autoCreatedBy?.trim() || wishBy.trim() || null,
    });
    if (e) setError(e.message);
    setWishText("");
    await load();
  };

  const delWish = async (id: string) => {
    if (!supabase) return;
    setError(null);
    const { error: e } = await supabase.rpc("meal_wish_delete", { p_secret: sharedSecret, p_wish_id: id });
    if (e) setError(e.message);
    await load();
  };

  return (
    <div className="card">
      <h2>Lounastoiveet</h2>
      <div className="row" style={{ marginBottom: 10 }}>
        <input type="text" placeholder="Uusi toive…" value={wishText} onChange={(e) => setWishText(e.target.value)} />
      </div>
      <div className="row" style={{ marginBottom: 10 }}>
        {autoCreatedBy ? (
          <span className="muted">
            Toivoja: <strong>{autoCreatedBy}</strong>
          </span>
        ) : (
          <input type="text" placeholder="Kuka (valinnainen)" value={wishBy} onChange={(e) => setWishBy(e.target.value)} />
        )}
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
      {error && <p style={{ color: "#ffab91" }}>{error}</p>}
    </div>
  );
}

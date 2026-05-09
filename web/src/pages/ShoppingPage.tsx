import { useEffect, useMemo, useState } from "react";
import { Link, useOutletContext, useSearchParams } from "react-router-dom";
import type { EditorCaps } from "../lib/editorSession";
import { supabase } from "../lib/supabase";

type Item = {
  id: string;
  title: string;
  done: boolean;
  sort_order: number;
  added_by: string | null;
};

function useSharedToken(): string {
  const caps = useOutletContext<EditorCaps | undefined>();
  const [sp] = useSearchParams();
  const fromHallinta = caps?.sharedSecret?.trim();
  if (fromHallinta) return fromHallinta;
  return (sp.get("t") || import.meta.env.VITE_TOKEN_SHARED || "").trim();
}

export default function ShoppingPage() {
  const capsCtx = useOutletContext<EditorCaps | undefined>();
  const token = useSharedToken();
  const [items, setItems] = useState<Item[]>([]);
  const [title, setTitle] = useState("");
  const [addedBy, setAddedBy] = useState("");
  const [error, setError] = useState<string | null>(null);

  const canEdit = token.length > 0;

  const load = async () => {
    if (!supabase) return;
    const { data, error: e } = await supabase.from("shopping_items").select("*").order("sort_order", {
      ascending: true,
    });
    if (e) setError(e.message);
    else setItems((data as Item[]) ?? []);
  };

  useEffect(() => {
    void load();
    if (!supabase) return;
    const ch = supabase
      .channel("shopping_items_changes")
      .on(
        "postgres_changes",
        { event: "*", schema: "public", table: "shopping_items" },
        () => {
          void load();
        },
      )
      .subscribe();
    return () => {
      void supabase.removeChannel(ch);
    };
  }, []);

  const sorted = useMemo(
    () => [...items].sort((a, b) => (a.sort_order - b.sort_order) || a.title.localeCompare(b.title)),
    [items],
  );

  const add = async () => {
    if (!supabase || !canEdit) return;
    const t = title.trim();
    if (!t) return;
    setError(null);
    const { error: e } = await supabase.rpc("shopping_add", {
      p_secret: token,
      p_title: t,
      p_added_by: addedBy.trim() || null,
    });
    if (e) setError(e.message);
    setTitle("");
    await load();
  };

  const toggle = async (id: string) => {
    if (!supabase || !canEdit) return;
    setError(null);
    const { error: e } = await supabase.rpc("shopping_toggle_done", { p_secret: token, p_item_id: id });
    if (e) setError(e.message);
    await load();
  };

  const remove = async (id: string) => {
    if (!supabase || !canEdit) return;
    setError(null);
    const { error: e } = await supabase.rpc("shopping_delete", { p_secret: token, p_item_id: id });
    if (e) setError(e.message);
    await load();
  };

  const clearDone = async () => {
    if (!supabase || !canEdit) return;
    setError(null);
    const { error: e } = await supabase.rpc("shopping_clear_done", { p_secret: token });
    if (e) setError(e.message);
    await load();
  };

  if (!canEdit) {
    return (
      <div className="page">
        <h1>Kauppalista</h1>
        <p className="muted">Lisää osoitteeseen ?t=YHTEINEN_TOKEN tai aseta VITE_TOKEN_SHARED .env-tiedostoon.</p>
        <Link to="/linkit">← Linkit</Link>
      </div>
    );
  }

  return (
    <div className="page">
      <h1>Kauppalista</h1>
      <p className="muted">
        <Link to={capsCtx ? "/hallinta" : "/linkit"}>← {capsCtx ? "Hallinta" : "Linkit"}</Link>
      </p>

      <div className="card">
        <div className="row" style={{ marginBottom: 12 }}>
          <input type="text" placeholder="Lisää tuote…" value={title} onChange={(e) => setTitle(e.target.value)} />
        </div>
        <div className="row" style={{ marginBottom: 12 }}>
          <input
            type="text"
            placeholder="Kuka lisäsi (valinnainen)"
            value={addedBy}
            onChange={(e) => setAddedBy(e.target.value)}
          />
          <button type="button" onClick={() => void add()}>
            Lisää
          </button>
        </div>
        {error && <p style={{ color: "#ffab91" }}>{error}</p>}
      </div>

      <div className="card">
        <h2>Avoinna</h2>
        <ul className="list">
          {sorted
            .filter((i) => !i.done)
            .map((i) => (
              <li key={i.id} className="row" style={{ justifyContent: "space-between" }}>
                <label className="row" style={{ gap: 12, flex: 1 }}>
                  <input className="check" type="checkbox" checked={i.done} onChange={() => void toggle(i.id)} />
                  <span>{i.title}</span>
                </label>
                <button type="button" className="secondary" onClick={() => void remove(i.id)}>
                  Poista
                </button>
              </li>
            ))}
        </ul>
      </div>

      <div className="card">
        <h2>Ostettu</h2>
        <ul className="list">
          {sorted
            .filter((i) => i.done)
            .map((i) => (
              <li key={i.id} className="row" style={{ justifyContent: "space-between" }}>
                <label className="row" style={{ gap: 12, flex: 1 }}>
                  <input className="check" type="checkbox" checked={i.done} onChange={() => void toggle(i.id)} />
                  <span style={{ color: "#90a4ae", textDecoration: "line-through" }}>{i.title}</span>
                </label>
                <button type="button" className="secondary" onClick={() => void remove(i.id)}>
                  Poista
                </button>
              </li>
            ))}
        </ul>
        <button type="button" className="secondary" style={{ marginTop: 12 }} onClick={() => void clearDone()}>
          Tyhjennä ostetut
        </button>
      </div>
    </div>
  );
}

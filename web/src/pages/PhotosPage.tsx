import { useEffect, useState } from "react";
import { Link, useOutletContext, useSearchParams } from "react-router-dom";
import type { EditorCaps } from "../lib/editorSession";
import { functionsBase, publicPhotoUrl, supabase } from "../lib/supabase";

type Photo = {
  id: string;
  storage_path: string;
  added_by_slug: string | null;
};

function useSharedToken(): string {
  const caps = useOutletContext<EditorCaps | undefined>();
  const [sp] = useSearchParams();
  const fromHallinta = caps?.sharedSecret?.trim();
  if (fromHallinta) return fromHallinta;
  return (sp.get("t") || import.meta.env.VITE_TOKEN_SHARED || "").trim();
}

export default function PhotosPage() {
  const capsCtx = useOutletContext<EditorCaps | undefined>();
  const token = useSharedToken();
  const [photos, setPhotos] = useState<Photo[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [busyDel, setBusyDel] = useState<string | null>(null);
  const [by, setBy] = useState("");

  const canEdit = token.length > 0;

  const load = async () => {
    if (!supabase) return;
    const { data, error: e } = await supabase.from("photos").select("*").order("created_at", { ascending: false }).limit(80);
    if (e) setError(e.message);
    else setPhotos((data as Photo[]) ?? []);
  };

  useEffect(() => {
    void load();
  }, []);

  const upload = async (file: File | null) => {
    if (!file || !canEdit) return;
    setBusy(true);
    setError(null);
    try {
      const fd = new FormData();
      fd.set("token", token);
      fd.set("file", file);
      const slug = by.trim().toLowerCase();
      if (slug === "been" || slug === "maija" || slug === "joni") fd.set("added_by_slug", slug);

      const url = import.meta.env.VITE_SUPABASE_URL;
      const key = import.meta.env.VITE_SUPABASE_ANON_KEY;
      const res = await fetch(`${functionsBase()}/photo-ingest`, {
        method: "POST",
        headers: {
          apikey: key,
          Authorization: `Bearer ${key}`,
        },
        body: fd,
      });
      if (!res.ok) {
        const txt = await res.text();
        throw new Error(txt || `HTTP ${res.status}`);
      }
      await load();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Upload epäonnistui");
    } finally {
      setBusy(false);
    }
  };

  const removePhoto = async (photoId: string) => {
    if (!canEdit) return;
    const urlBase = import.meta.env.VITE_SUPABASE_URL;
    const key = import.meta.env.VITE_SUPABASE_ANON_KEY;
    if (!urlBase || !key) return;
    setBusyDel(photoId);
    setError(null);
    try {
      const res = await fetch(`${functionsBase()}/photo-delete`, {
        method: "POST",
        headers: {
          apikey: key,
          Authorization: `Bearer ${key}`,
          "Content-Type": "application/json",
        },
        body: JSON.stringify({ token, photo_id: photoId }),
      });
      if (!res.ok) {
        const txt = await res.text();
        throw new Error(txt || `HTTP ${res.status}`);
      }
      await load();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Poisto epäonnistui");
    } finally {
      setBusyDel(null);
    }
  };

  if (!canEdit) {
    return (
      <div className="page">
        <h1>Kuvat</h1>
        <p className="muted">Lisää osoitteeseen ?t=YHTEINEN_TOKEN tai aseta VITE_TOKEN_SHARED.</p>
        <Link to="/">← Etusivu</Link>
      </div>
    );
  }

  const back = capsCtx ? "/hallinta" : "/linkit";
  const backLabel = capsCtx ? "Hallinta" : "Linkit";

  return (
    <div className="page">
      <h1>Päivän kuva ‑arkisto</h1>
      <p className="muted">
        Kaikki voivat lisätä ja poistaa kuvia. Ne kiertävät TV:ssä ja &quot;Päivän kuva&quot; ‑laatikossa.
      </p>
      <p className="muted">
        <Link to={back}>← {backLabel}</Link>
      </p>

      <div className="card">
        <div className="row" style={{ marginBottom: 10 }}>
          <input type="text" placeholder="been / maija / joni (valinnainen)" value={by} onChange={(e) => setBy(e.target.value)} />
        </div>
        <div className="row">
          <input
            type="file"
            accept="image/*"
            disabled={busy}
            onChange={(e) => {
              const f = e.target.files?.[0] ?? null;
              e.target.value = "";
              void upload(f);
            }}
          />
        </div>
        {error && <p style={{ color: "#ffab91" }}>{error}</p>}
      </div>

      <div className="grid2">
        {photos.map((p) => (
          <div key={p.id} className="card photo-card" style={{ padding: 8 }}>
            <img
              src={publicPhotoUrl(p.storage_path)}
              alt=""
              style={{ width: "100%", borderRadius: 10, display: "block" }}
              loading="lazy"
            />
            <div className="row photo-card-actions" style={{ marginTop: 8, justifyContent: "space-between" }}>
              <span className="muted">{p.added_by_slug ?? "—"}</span>
              <button type="button" className="secondary touch-btn-small" disabled={busyDel === p.id} onClick={() => void removePhoto(p.id)}>
                Poista
              </button>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}

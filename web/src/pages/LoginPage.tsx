import { FormEvent, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { saveEditorSession, type EditorCaps } from "../lib/editorSession";
import { supabase } from "../lib/supabase";

type RpcPinOk = {
  person_slug: string;
  schedule_secret: string;
  shared_secret: string;
  can_weekly_meals: boolean;
  can_meal_wishes: boolean;
};

function mapRpcToCaps(row: RpcPinOk): EditorCaps | null {
  const slug = row.person_slug;
  if (slug !== "been" && slug !== "maija" && slug !== "joni") return null;
  return {
    personSlug: slug,
    scheduleSecret: row.schedule_secret,
    sharedSecret: row.shared_secret,
    canWeeklyMeals: row.can_weekly_meals,
    canMealWishes: row.can_meal_wishes,
  };
}

export default function LoginPage() {
  const nav = useNavigate();
  const [pin, setPin] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    if (!supabase) return;
    const p = pin.trim();
    if (!p) return;
    setBusy(true);
    setError(null);
    try {
      const { data, error: rpcErr } = await supabase.rpc("exchange_editor_pin", { p_pin: p });
      if (rpcErr) throw rpcErr;
      const caps = mapRpcToCaps(data as RpcPinOk);
      if (!caps) throw new Error("Virheellinen vastaus");
      saveEditorSession(caps);
      nav("/hallinta", { replace: true });
    } catch {
      setError("Virheellinen koodi.");
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="page hallinta-login">
      <h1>Kodinäyttö</h1>
      <p className="muted">Syötä hallintakoodi puhelimesta.</p>

      <form className="card" onSubmit={(e) => void submit(e)}>
        <label className="muted" htmlFor="pin">
          Koodi
        </label>
        <input
          id="pin"
          name="pin"
          inputMode="numeric"
          autoComplete="one-time-code"
          placeholder="••••"
          value={pin}
          onChange={(e) => setPin(e.target.value)}
          style={{ fontSize: "1.35rem", letterSpacing: "0.12em", marginTop: 8, marginBottom: 16 }}
        />
        <button type="submit" className="touch-btn" disabled={busy}>
          {busy ? "Tarkistetaan…" : "Jatka"}
        </button>
        {error && (
          <p style={{ color: "#ffab91", marginTop: 12 }} role="alert">
            {error}
          </p>
        )}
      </form>

      <p className="muted" style={{ marginTop: 24 }}>
        <Link to="/linkit">Vanhat linkit (token)</Link>
      </p>
    </div>
  );
}

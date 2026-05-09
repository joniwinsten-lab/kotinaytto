import { Link, Navigate } from "react-router-dom";
import { clearEditorSession, loadEditorSession } from "../lib/editorSession";

function personLabel(slug: string): string {
  if (slug === "been") return "Bee";
  if (slug === "maija") return "Maija";
  if (slug === "joni") return "Joni";
  return slug;
}

export default function HallintaHubPage() {
  const caps = loadEditorSession();
  if (!caps) return <Navigate to="/" replace />;

  const logout = () => {
    clearEditorSession();
    window.location.href = "https://joniwinsten-lab.github.io/kotinaytto/";
  };

  return (
    <div className="page">
      <h1>Hei, {personLabel(caps.personSlug)}</h1>
      <p className="muted">Valitse mitä päivität.</p>

      <nav className="hallinta-grid">
        <Link className="hallinta-tile" to="/hallinta/aikataulu">
          Omat aikataulut
        </Link>
        <Link className="hallinta-tile" to="/hallinta/kauppa">
          Kauppalista
        </Link>
        <Link className="hallinta-tile" to="/hallinta/kuvat">
          Päivän kuva ‑arkisto
        </Link>
        {caps.canWeeklyMeals && (
          <Link className="hallinta-tile" to="/hallinta/lounaat">
            Viikon lounaat
          </Link>
        )}
        {caps.canMealWishes && (
          <Link className="hallinta-tile" to="/hallinta/toiveet">
            Lounastoiveet
          </Link>
        )}
      </nav>

      <button type="button" className="secondary touch-btn" style={{ marginTop: 28 }} onClick={() => logout()}>
        Kirjaudu ulos
      </button>
    </div>
  );
}

import { useEffect, useState } from "react";
import { Navigate, Outlet } from "react-router-dom";
import { loadEditorSession } from "../lib/editorSession";
import { applyThemeMode, loadThemeMode, saveThemeMode, type ThemeMode } from "../lib/themePreference";

export default function HallintaLayout() {
  const caps = loadEditorSession();
  if (!caps) return <Navigate to="/" replace />;

  const [theme, setTheme] = useState<ThemeMode>(() => loadThemeMode());

  useEffect(() => {
    applyThemeMode(theme);
    saveThemeMode(theme);
  }, [theme]);

  return (
    <>
      <div className="hallinta-topbar">
        <button
          type="button"
          className="secondary touch-btn-small"
          onClick={() => setTheme((prev) => (prev === "dark" ? "light" : "dark"))}
          aria-label="Vaihda teema"
        >
          Teema: {theme === "dark" ? "Tumma" : "Vaalea"}
        </button>
      </div>
      <Outlet context={caps} />
    </>
  );
}

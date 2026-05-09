import { Navigate, Route, Routes } from "react-router-dom";
import { supabaseConfigured } from "./lib/supabase";
import HallintaLayout from "./layouts/HallintaLayout";
import HallintaHubPage from "./pages/HallintaHubPage";
import HallintaMealsPage from "./pages/HallintaMealsPage";
import HallintaWishesPage from "./pages/HallintaWishesPage";
import HallintaWeekSchedulePage from "./pages/HallintaWeekSchedulePage";
import HomePage from "./pages/HomePage";
import LoginPage from "./pages/LoginPage";
import MealsPage from "./pages/MealsPage";
import PhotosPage from "./pages/PhotosPage";
import SchedulePage from "./pages/SchedulePage";
import ShoppingPage from "./pages/ShoppingPage";

export default function App() {
  if (!supabaseConfigured) {
    const onGithubPages = typeof window !== "undefined" && window.location.hostname.endsWith("github.io");
    return (
      <div className="page">
        <h1>Kodinäyttö</h1>
        {onGithubPages ? (
          <>
            <p>
              Sivu on rakennettu ilman Supabase-asetuksia. Lisää repoon{" "}
              <strong>GitHub Actions ‑secretit</strong> (sama arvo kuin paikallisessa <code>.env</code>):
            </p>
            <ul className="list" style={{ paddingLeft: "1.2rem" }}>
              <li>
                <code>VITE_SUPABASE_URL</code> — projektin osoite (Dashboard → API)
              </li>
              <li>
                <code>VITE_SUPABASE_ANON_KEY</code> — <strong>anon public</strong> ‑avain (ei service_role)
              </li>
            </ul>
            <p className="muted">
              Polku: <strong>Settings → Secrets and variables → Actions → New repository secret</strong>. Sen jälkeen{" "}
              <strong>Actions → Web GitHub Pages → Run workflow</strong> (tai työnna commit <code>web/</code>‑kansioon).
            </p>
          </>
        ) : (
          <p>
            Aseta <code>VITE_SUPABASE_URL</code> ja <code>VITE_SUPABASE_ANON_KEY</code> tiedostoon <code>web/.env</code>{" "}
            (kopioi <code>env.example</code> juureen) ja käynnistä <code>npm run dev</code> uudestaan.
          </p>
        )}
      </div>
    );
  }

  return (
    <Routes>
      <Route path="/" element={<LoginPage />} />
      <Route path="/linkit" element={<HomePage />} />

      <Route path="/hallinta" element={<HallintaLayout />}>
        <Route index element={<HallintaHubPage />} />
        <Route path="aikataulu" element={<HallintaWeekSchedulePage />} />
        <Route path="kauppa" element={<ShoppingPage />} />
        <Route path="lounaat" element={<HallintaMealsPage />} />
        <Route path="toiveet" element={<HallintaWishesPage />} />
        <Route path="kuvat" element={<PhotosPage />} />
      </Route>

      <Route path="/kauppa" element={<ShoppingPage />} />
      <Route path="/aikataulu/:slug" element={<SchedulePage />} />
      <Route path="/lounaat" element={<MealsPage />} />
      <Route path="/kuvat" element={<PhotosPage />} />

      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}

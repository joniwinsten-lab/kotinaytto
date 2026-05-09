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
    return (
      <div className="page">
        <h1>Kodinäyttö</h1>
        <p>
          Aseta <code>VITE_SUPABASE_URL</code> ja <code>VITE_SUPABASE_ANON_KEY</code> tiedostoon{" "}
          <code>.env</code> (kopioi <code>env.example</code>).
        </p>
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

import { Link, Navigate, Route, Routes } from "react-router-dom";
import { supabaseConfigured } from "./lib/supabase";
import HomePage from "./pages/HomePage";
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
      <Route path="/" element={<HomePage />} />
      <Route path="/kauppa" element={<ShoppingPage />} />
      <Route path="/aikataulu/:slug" element={<SchedulePage />} />
      <Route path="/lounaat" element={<MealsPage />} />
      <Route path="/kuvat" element={<PhotosPage />} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}

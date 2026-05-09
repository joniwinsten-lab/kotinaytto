import { Link, Navigate, useOutletContext } from "react-router-dom";
import { MealWishesEditor } from "../components/MealEditors";
import type { EditorCaps } from "../lib/editorSession";

export default function HallintaWishesPage() {
  const caps = useOutletContext<EditorCaps>();
  if (!caps.canMealWishes) return <Navigate to="/hallinta" replace />;
  const autoBy = caps.personSlug === "been" ? "Bee" : caps.personSlug === "maija" ? "Maija" : "Joni";

  return (
    <div className="page">
      <h1>Lounastoiveet</h1>
      <p className="muted">
        <Link to="/hallinta">← Takaisin</Link>
      </p>
      <MealWishesEditor sharedSecret={caps.sharedSecret} autoCreatedBy={autoBy} />
    </div>
  );
}

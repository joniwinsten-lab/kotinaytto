import { Link, useSearchParams } from "react-router-dom";
import { MealWishesEditor, WeeklyMealsEditor } from "../components/MealEditors";
import { supabase } from "../lib/supabase";

function useSharedToken(): string {
  const [sp] = useSearchParams();
  return (sp.get("t") || import.meta.env.VITE_TOKEN_SHARED || "").trim();
}

export default function MealsPage() {
  const token = useSharedToken();
  const canEdit = token.length > 0;

  if (!supabase || !canEdit) {
    return (
      <div className="page">
        <h1>Lounaat</h1>
        <p className="muted">Lisää osoitteeseen ?t=YHTEINEN_TOKEN tai aseta VITE_TOKEN_SHARED.</p>
        <Link to="/">← Etusivu</Link>
      </div>
    );
  }

  return (
    <div className="page">
      <h1>Viikon lounaat ja toiveet</h1>
      <p className="muted">
        <Link to="/linkit">← Linkit</Link>
      </p>
      <WeeklyMealsEditor sharedSecret={token} />
      <MealWishesEditor sharedSecret={token} />
    </div>
  );
}

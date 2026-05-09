import { Link, Navigate, useOutletContext } from "react-router-dom";
import { WeeklyMealsEditor } from "../components/MealEditors";
import type { EditorCaps } from "../lib/editorSession";

export default function HallintaMealsPage() {
  const caps = useOutletContext<EditorCaps>();
  if (!caps.canWeeklyMeals) return <Navigate to="/hallinta" replace />;

  return (
    <div className="page">
      <h1>Viikon lounaat</h1>
      <p className="muted">
        <Link to="/hallinta">← Takaisin</Link>
      </p>
      <WeeklyMealsEditor sharedSecret={caps.sharedSecret} />
    </div>
  );
}

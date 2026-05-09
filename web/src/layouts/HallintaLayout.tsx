import { Navigate, Outlet } from "react-router-dom";
import { loadEditorSession } from "../lib/editorSession";

export default function HallintaLayout() {
  const caps = loadEditorSession();
  if (!caps) return <Navigate to="/" replace />;
  return <Outlet context={caps} />;
}

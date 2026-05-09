export type EditorCaps = {
  personSlug: "been" | "maija" | "joni";
  scheduleSecret: string;
  sharedSecret: string;
  canWeeklyMeals: boolean;
  canMealWishes: boolean;
};

const STORAGE_KEY = "kotinaytto_editor_v1";

export function saveEditorSession(c: EditorCaps): void {
  sessionStorage.setItem(STORAGE_KEY, JSON.stringify(c));
}

export function loadEditorSession(): EditorCaps | null {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY);
    if (!raw) return null;
    const o = JSON.parse(raw) as Partial<EditorCaps>;
    if (
      (o.personSlug === "been" || o.personSlug === "maija" || o.personSlug === "joni") &&
      typeof o.scheduleSecret === "string" &&
      typeof o.sharedSecret === "string" &&
      typeof o.canWeeklyMeals === "boolean" &&
      typeof o.canMealWishes === "boolean"
    ) {
      return o as EditorCaps;
    }
  } catch {
    /* ignore */
  }
  return null;
}

export function clearEditorSession(): void {
  sessionStorage.removeItem(STORAGE_KEY);
}

export type ThemeMode = "dark" | "light";

const LS_KEY = "kotinaytto_theme_mode_v1";

function isThemeMode(value: string | null): value is ThemeMode {
  return value === "dark" || value === "light";
}

export function loadThemeMode(): ThemeMode {
  if (typeof window === "undefined") return "dark";
  const stored = window.localStorage.getItem(LS_KEY);
  return isThemeMode(stored) ? stored : "dark";
}

export function saveThemeMode(mode: ThemeMode) {
  if (typeof window === "undefined") return;
  window.localStorage.setItem(LS_KEY, mode);
}

export function applyThemeMode(mode: ThemeMode) {
  if (typeof document === "undefined") return;
  document.documentElement.setAttribute("data-theme", mode);
}

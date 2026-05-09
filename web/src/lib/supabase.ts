import { createClient } from "@supabase/supabase-js";

const url = import.meta.env.VITE_SUPABASE_URL;
const key = import.meta.env.VITE_SUPABASE_ANON_KEY;

export const supabaseConfigured = Boolean(url && key);

export const supabase = supabaseConfigured
  ? createClient(url, key, {
      auth: { persistSession: false, autoRefreshToken: false },
    })
  : null;

export function functionsBase(): string {
  const custom = import.meta.env.VITE_FUNCTIONS_BASE?.replace(/\/$/, "");
  if (custom) return custom;
  if (!url) return "";
  return `${url.replace(/\/$/, "")}/functions/v1`;
}

export function publicPhotoUrl(path: string): string {
  if (!url) return "";
  return `${url.replace(/\/$/, "")}/storage/v1/object/public/family_photos/${path}`;
}

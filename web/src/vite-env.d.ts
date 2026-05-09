/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_SUPABASE_URL: string;
  readonly VITE_SUPABASE_ANON_KEY: string;
  readonly VITE_FAMILY_READ_TOKEN: string;
  readonly VITE_TOKEN_BEEN: string;
  readonly VITE_TOKEN_MAIJA: string;
  readonly VITE_TOKEN_JONI: string;
  readonly VITE_TOKEN_SHARED: string;
  readonly VITE_FUNCTIONS_BASE: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}

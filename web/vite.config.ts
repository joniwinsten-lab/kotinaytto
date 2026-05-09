import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

/** GitHub Pages -projektissa: VITE_BASE_PATH=/repo-nimi/ */
const base = process.env.VITE_BASE_PATH ?? "/";

export default defineConfig({
  base,
  plugins: [react()],
  server: {
    port: 5173,
  },
});

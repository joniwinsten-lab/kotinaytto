import { copyFileSync } from "node:fs";
import { resolve } from "node:path";
import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

/** GitHub Pages -projektissa: VITE_BASE_PATH=/repo-nimi/ */
const base = process.env.VITE_BASE_PATH ?? "/";

/** GitHub Pages: tuntemattomat polut (esim. /repo/hallinta) tarvitsevat 404.html = index.html, jotta React Router käynnistyy. */
function spaGithubPages404(): import("vite").Plugin {
  return {
    name: "spa-github-pages-404",
    closeBundle() {
      const dist = resolve(__dirname, "dist");
      copyFileSync(resolve(dist, "index.html"), resolve(dist, "404.html"));
    },
  };
}

export default defineConfig({
  base,
  plugins: [react(), spaGithubPages404()],
  server: {
    port: 5173,
  },
});

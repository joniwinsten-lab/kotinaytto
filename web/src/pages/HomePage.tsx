import { Link } from "react-router-dom";

function tokenOrHint(env: string | undefined, path: string, label: string) {
  const t = env?.trim();
  if (t) {
    const qs = `?t=${encodeURIComponent(t)}`;
    return (
      <div className="card">
        <Link to={`${path}${qs}`}>{label}</Link>
        <p className="muted">Token on tallennettu .env-tiedostoon tälle kehitysbuildille.</p>
      </div>
    );
  }
  return (
    <div className="card">
      <p>
        <strong>{label}</strong>
      </p>
      <p className="muted">
        Avaa osoite muodossa <code>{path}?t=JAETTU_TOKEN</code> (token SQL:{" "}
        <code>select slug, secret from editor_tokens;</code>).
      </p>
    </div>
  );
}

export default function HomePage() {
  const shared = import.meta.env.VITE_TOKEN_SHARED;
  const been = import.meta.env.VITE_TOKEN_BEEN;
  const maija = import.meta.env.VITE_TOKEN_MAIJA;
  const joni = import.meta.env.VITE_TOKEN_JONI;

  return (
    <div className="page">
      <h1>Vanhat linkit (token)</h1>
      <p className="muted">
        Suositeltu tapa: <a href="/">etusivun PIN-koodi</a>.
      </p>

      {tokenOrHint(shared, "/kauppa", "Kauppalista")}
      {tokenOrHint(been, "/aikataulu/been", "Been – koulu")}
      {tokenOrHint(maija, "/aikataulu/maija", "Maija – työvuorot")}
      {tokenOrHint(joni, "/aikataulu/joni", "Joni – työpäivät")}
      {tokenOrHint(shared, "/lounaat", "Viikon lounaat ja toiveet")}
      {tokenOrHint(shared, "/kuvat", "Perhekuvat")}

      <div className="card muted">
        <p>
          TV-sovellus käyttää <code>get_dashboard</code>-kutsua ja omaa{" "}
          <code>families.read_token</code> -arvoa (Android <code>local.properties</code>).
        </p>
      </div>
      <p className="muted">
        <a href="/">← PIN-kirjautuminen</a>
      </p>
    </div>
  );
}

# Kodinäyttö (Shield + puhelimet)

Perheen infonäyttö: **Android TV** -sovellus Nvidia Shieldille ja **Vite + React** -PWA puhelimella. Data **Supabasessa** (Postgres, Realtime, Storage, Edge Functions).

## 1. Supabase

1. Luo projekti Supabaseen ja aja migraatiot:
   - `supabase link` (valinnainen) tai kopioi SQL Supabase SQL Editoriin tiedostoista [`supabase/migrations/`](supabase/migrations/).
2. Deployaa Edge Functions (RSS, sää, kuvan upload):
   - `supabase functions deploy sync-rss`
   - `supabase functions deploy sync-weather`
   - `supabase functions deploy photo-ingest`
3. Aseta salaisuudet funktioille (Dashboard → Edge Functions → Secrets): `SUPABASE_URL`, `SUPABASE_SERVICE_ROLE_KEY` (yleensä automaattisesti).
4. Hae tokenit SQL:stä kerran:
   - `select read_token from families limit 1;` → TV + `VITE_FAMILY_READ_TOKEN` (vain jos tarvitset webissä dashboard-RPC:tä).
   - `select slug, secret from editor_tokens order by slug;` → jaetut muokkauslinkit (`VITE_TOKEN_SHARED`, `VITE_TOKEN_BEEN`, …).

### Ajastukset (RSS + sää)

Kutsu funktioita säännöllisesti (GitHub Actions, cron, tai Supabase Scheduler):

```bash
export PROJECT="https://xxxx.supabase.co"
export SERVICE_ROLE="eyJ..." # service role vain palvelimelle

curl -sS -X POST "$PROJECT/functions/v1/sync-rss" \
  -H "Authorization: Bearer $SERVICE_ROLE" \
  -H "apikey: $SERVICE_ROLE"

curl -sS -X POST "$PROJECT/functions/v1/sync-weather" \
  -H "Authorization: Bearer $SERVICE_ROLE" \
  -H "apikey: $SERVICE_ROLE"
```

Perheen sijainti säähän: päivitä `families.home_latitude`, `home_longitude` (oletus Helsinki).

## 2. Web (puhelin)

```bash
cd web
cp ../env.example .env
# täytä VITE_SUPABASE_URL, VITE_SUPABASE_ANON_KEY, tokenit
npm install
npm run dev
```

Tuotanto: `npm run build` → `web/dist` staattisena hostingina (Vercel, Netlify, oma palvelin).

## 3. Android TV (Shield)

1. Asenna **Android SDK** ja kopioi [`android-tv/local.properties.example`](android-tv/local.properties.example) → `android-tv/local.properties`; täytä `sdk.dir`, `supabase.url`, `supabase.anonKey`, `family.readToken` (`families.read_token`).
2. Käännä (JDK **17 tai 21**, ei Java 25 Gradle 8.7 -yhteensopivuuden takia):

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)   # macOS
cd android-tv
./gradlew :app:assembleDebug
```

APK: `android-tv/app/build/outputs/apk/debug/app-debug.apk`.

3. **Sideload Shieldille**: `adb connect <shield-ip>:5555` (kehittäjäasetukset → USB debugging / wireless), sitten `adb install -r app-debug.apk`.

### Autokäynnistys ja näyttö 24/7

- **Launcher**: aseta kotinäyttö-sovellus oletuskäynnistysohjelmaksi (Shield TV:n asetukset → Apps → oletus; laitekohtainen).
- **Näytönsäästäjä (Daydream)**: tämä repo käyttää täyttä sovellusta dashboardina; erillinen `DreamService` ei ole toteutettu. Voit pitää näytön käynnissä ja himmentää TV:n **Ambient Mode** / virransäästö pois päältä keittiön näytöllä.
- **Näyttöpoltto**: käytä tummaa teemaa (sovellus) ja vaihtelevaa taustaa (sää + kuvat) – vähentää kiinteitä kirkkaita elementtejä.

## 4. Rakenne

- [`supabase/migrations/`](supabase/migrations/) – skeema, RLS, RPC:t (`get_dashboard`, kauppa, aikataulut, lounaat).
- [`supabase/functions/`](supabase/functions/) – RSS, Open-Meteo -sää, kuvan ingest.
- [`android-tv/`](android-tv/) – Kotlin + Compose, `get_dashboard` + kaupan polling.
- [`web/`](web/) – muokkaussivut ja Realtime kauppalistalle.

## 5. Turvallisuus (MVP)

Yhden perheen asennus: anon-luku rajattu `single_family_id()`-mallilla. **Älä jaa** Supabase anon -avainta julkisesti laajasti; token-URLit (`?t=`) ovat käytännössä salasanoja – tallenna vain perheen laitteille tai `.env`-tiedostoihin.

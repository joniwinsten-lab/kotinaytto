# Kodinäyttö (Shield + puhelimet)

Perheen infonäyttö: **Android TV** -sovellus Nvidia Shieldille ja **Vite + React** -PWA puhelimella. Data **Supabasessa** (Postgres, Realtime, Storage, Edge Functions).

**GitHub-repo:** [joniwinsten-lab/kotinaytto](https://github.com/joniwinsten-lab/kotinaytto)

## 0. Kytkentä: GitHub ↔ Supabase (kotinaytto-projekti)

Käytä **omaan Kodinäyttöön** tarkoitettua Supabase-projektia.

### Vaihtoehta A – Supabasen GitHub-integraatio

1. Supabase Dashboard → projekti **kotinaytto** → **Integrations** → **GitHub**.
2. Valitse repo **joniwinsten-lab/kotinaytto** ja seuraa ohjattua linkitystä / migraatiopolitiikkaa.

### Vaihtoehta B – GitHub Actions (`.github/workflows/supabase-deploy.yml`)

1. **Project Settings** → **General** → kopioi **Reference ID**.
2. **Account** → **Access Tokens** → luo token.
3. GitHub → repo → **Settings** → **Secrets and variables** → **Actions**:
   - `SUPABASE_ACCESS_TOKEN` – [Access Tokens](https://supabase.com/dashboard/account/tokens) (tili)
   - `SUPABASE_PROJECT_REF` – Reference ID (Dashboard → Project Settings → General)
   - *(GitHub Actions – sisältösynk)* **`SUPABASE_SERVICE_ROLE_KEY`** – API → **service_role** (vain secretiin; käytössä workflowssa [Supabase sync content](.github/workflows/supabase-sync-content.yml))
   - *(valinnainen)* `SUPABASE_DB_PASSWORD` – vain jos `db push` vaatii; alla selitys

**Database password -kohdasta:** Supabase **ei näytä** Postgres-salasanaa uudelleen projektin luonnin jälkeen (turvallisuus). Voit vain **Reset database password** (Project Settings → Database) ja silloin saat **uuden** salasanan kerran kopioitavaksi. Sitä ei voi “hakea” ulkopuolelta ilman sinun kirjautumistasi.

GitHub Actions -workflow on päivitetty niin, että **`SUPABASE_DB_PASSWORD` ei ole pakollinen**: käytössä ovat access token + projektin ref, ja tyhjä DB-salasana CLI:ssa vastaa “ohita validointi” -polkua. Jos `db push` silti epäonnistuu autentikointiin, tee reset ja lisää salasana secretiksi `SUPABASE_DB_PASSWORD`, tai aja migraatiot kerran **SQL Editorissa** reposta [`supabase/migrations/`](supabase/migrations/).

4. **Actions** → **Supabase deploy** → **Run workflow**.

### Paikallinen CLI

```bash
brew install supabase/tap/supabase   # tai: npx supabase@latest ...
supabase login
supabase link --project-ref <REFERENCE_ID>
supabase db push
supabase functions deploy sync-rss
supabase functions deploy sync-weather
supabase functions deploy photo-ingest
```

## 1. Supabase (ajon jälkeen)

### Seuraavat askeleet tästä eteenpäin

1. Varmista että **Supabase deploy** -workflow on ajettu vihreäksi (`Actions`).
2. Lisää GitHubissa secret **`SUPABASE_SERVICE_ROLE_KEY`**: Supabase → **Project Settings** → **API** → **service_role** (älä koskaan commitoi / chattiin).
3. **Actions** → **Supabase sync content** → **Run workflow** (täyttää uutiset ja sään kerran). Sama workflow ajaa myös päivittäisen cronin (`news_items`, `weather_cache`).
4. SQL Editorissa hae tokenit talteen:
   - `select read_token from families limit 1;` → Android `family.readToken` + tarvittaessa `VITE_FAMILY_READ_TOKEN`
   - `select slug, secret from editor_tokens order by slug;` → web `.env`: `VITE_TOKEN_*`
5. *(Valinnainen)* Päivitä koti sääkartalle:  
   `update families set home_latitude = 60.xx, home_longitude = 24.xx where id = (select id from families limit 1);`

*(Edge Functions saavat `SUPABASE_URL` ja `SUPABASE_SERVICE_ROLE_KEY` automaattisesti Supabasen hostissa deployn jälkeen.)*

### Ajastukset (RSS + sää)

Repo sisältää workflow **[Supabase sync content](.github/workflows/supabase-sync-content.yml)** (manuaali + päivittäinen cron). Tarvitset GitHub Secretin **`SUPABASE_SERVICE_ROLE_KEY`** (ja jo olemassa olevan `SUPABASE_PROJECT_REF`).

Paikallinen curl (vain esimerkki – älä commitoi avaimia):

```bash
export PROJECT="https://<PROJECT_REF>.supabase.co"
export SERVICE_ROLE="..." # service_role vain palvelimelle / CI-secret

curl -sS -X POST "$PROJECT/functions/v1/sync-rss" \
  -H "Authorization: Bearer $SERVICE_ROLE" \
  -H "apikey: $SERVICE_ROLE"

curl -sS -X POST "$PROJECT/functions/v1/sync-weather" \
  -H "Authorization: Bearer $SERVICE_ROLE" \
  -H "apikey: $SERVICE_ROLE"
```

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

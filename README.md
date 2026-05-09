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
supabase functions deploy photo-delete
```

Uusi migraatio **`supabase/migrations/20250509140000_editor_pins.sql`** lisää PIN-kirjautumisen (`exchange_editor_pin`). Aja `supabase db push` tai kopioi SQL Dashboardiin.

Alustavat PINit (vaihda tarvittaessa taulussa `editor_pins`): **Maija 2505**, **Joni 0407**, **Bee 0304**.

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

### Hallinta PIN-koodilla (suositeltu)

1. Etusivu `/` → syötä oma **PIN** (Maija **2505**, Joni **0407**, Bee **0304** — vaihdettavissa `editor_pins`-taulussa).
2. **Maija / Joni:** oma aikataulu (tänään + 7 päivää; sunnuntai + Suomen arkipyhät automaattisesti **Vapaa**), kauppalista, viikon lounaat, kuva-arkisto.
3. **Bee:** Been aikataulu (sama kalenterilogiikka; arkisin oletusajat muistetaan selaimessa), kauppalista, **lounastoiveet**, kuva-arkisto.
4. **Kaikki** voivat lisätä ja poistaa kuvia arkistosta (Edge Function **`photo-delete`**).

Vanhat **token-linkit** löytyvät polusta **`/linkit`** (`?t=` ‑URLit kuten ennen).

```bash
cd web
cp ../env.example .env
# täytä VITE_SUPABASE_URL, VITE_SUPABASE_ANON_KEY (tokenit valinnaisia PIN-tilan takia)
npm install
npm run dev
```

### GitHub Pages (Gitin kautta)

Workflow **[Web GitHub Pages](.github/workflows/web-pages.yml)** buildaa `web/dist`:in ja työntää **`gh-pages`** ‑haaran.

1. Repo → **Settings** → **Pages** → Source: branch **`gh-pages`** / **`/`** (tai `/root`).
2. **Supabase-arvot buildiin** — jompikumpi:
   - **Automaattinen (suositus):** sama reposecretit kuin Supabase deploy ‑workflowssa: **`SUPABASE_ACCESS_TOKEN`** + **`SUPABASE_PROJECT_REF`**. Pages-buildi hakee **anon**‑avaimen Supabase Management API:sta ja asettaa `https://<ref>.supabase.co`.
   - **Käsin:** **`VITE_SUPABASE_URL`** + **`VITE_SUPABASE_ANON_KEY`** (anon public, ei service_role).
3. **Actions** → **Web GitHub Pages** → **Run workflow** (tai push `web/` / workflow).
4. Sivun osoite on muotoa `https://<org>.github.io/<repo>/` — build käyttää automaattisesti polkua `/<repo>/`.

**404 GitHub Pagesissa:** käytä aina **projektin** osoitetta loppuun asti, esim. `https://joniwinsten-lab.github.io/kotinaytto/` (älä jätä `/kotinaytto` pois). Suora päivitys alisivulla (`…/hallinta`) vaatii `404.html`:n; se generoidaan automaattisesti Vite-buildissa.

Tuotanto muualle: `npm run build` → `web/dist` (aseta tarvittaessa `VITE_BASE_PATH=/` tai alikansio).

## 3. Android TV (Shield)

1. Asenna **Android SDK** ja kopioi [`android-tv/local.properties.example`](android-tv/local.properties.example) → `android-tv/local.properties`; täytä `sdk.dir`, `supabase.url`, `supabase.anonKey`, `family.readToken` (`families.read_token`).
2. Käännä (JDK **17 tai 21**, ei Java 25 Gradle 8.7 -yhteensopivuuden takia):

```bash
cd android-tv
./scripts/build-debug.sh
```

*(Tai käsin: `export JAVA_HOME=$(/usr/libexec/java_home -v 21)` ja `./gradlew :app:assembleDebug`.)*

APK: `android-tv/app/build/outputs/apk/debug/app-debug.apk`.

3. **Sideload Shieldille** (macOS):

```bash
brew install android-platform-tools   # adb
cd android-tv
./scripts/install-shield.sh 192.168.x.x   # Shieldin IP kotiverkosta
```

**Shieldilla (tiivis polku – valikon nimet voivat vaihdella mallin mukaan):**

1. **Asetukset** → **Laite** / **Device Preferences** → **Tietoja** → paina **Build**-kohtaa monta kertaa (n. 7×), kunnes sanotaan että olet kehittäjä.
2. Palaa takaisin → **Kehittäjäasetukset** → ota **USB-debuggaus** ja mahdollinen **Langaton debuggaus** / **Verkkodebuggaus** käyttöön.
3. Jos käytössä on **Langaton debuggaus**, avaa se ja katso **IP-osoite ja portti** (usein muotoa `192.168.x.x`, portti esim. **5555** tai näytössä oma portti). Jos järjestelmä pyytää **paritus**, tee paritus komennolla `adb pair IP:PORT` ja näytöllä näkyvä koodi (Android 11+).
4. Koneella: `./scripts/install-shield.sh 192.168.x.x` *(tai täysi `osoite:portti`, jos ei ole 5555)*. Vaihtoehtoisesti: `adb connect 192.168.x.x:5555` ja `adb install -r app/build/outputs/apk/debug/app-debug.apk`. Ensimmäisellä kerralla Shield saattaa näyttää hyväksyntädialogin debug-yhteydestä.

*(Vaihtoehto: kopioi `app-debug.apk` USB-tikulle ja asenna Shieldin tiedostonhallinnalla, jos laite sallii tuntemattomat lähteet – polku riippuu Shieldin Android-versiosta.)*

### Esikatselu ulkoasusta (ilman Shieldiä)

- **Android Studio**: avaa [`android-tv/app/src/debug/java/.../HomeScreenPreviews.kt`](android-tv/app/src/debug/java/fi/kotinaytto/tv/ui/preview/HomeScreenPreviews.kt), valitse **`PreviewDashboardTv`** tai **`PreviewDreamTv`** ja käytä **Split / Design** -näkymää (Compose Preview).
- **Havainnekuva** (ei täsmällinen screenshot, vain tunnelma): [`android-tv/docs/kotinaytto-tv-preview.png`](android-tv/docs/kotinaytto-tv-preview.png).

### Peilaa Shieldin ruutu Macille (ei tarvitse toiseen huoneeseen)

1. **Skripti** [`android-tv/scripts/mirror-shield.sh`](android-tv/scripts/mirror-shield.sh) (käyttää **scrcpy**-työkalua; asenna kerran: `brew install scrcpy`). Kun `adb devices` näyttää `device`:

   ```bash
   cd android-tv
   ./scripts/mirror-shield.sh
   ```

   Oletusosoite ja portti ovat skriptissä (`192.168.1.116:5555`); muu laite tai portti: `./scripts/mirror-shield.sh 192.168.1.x:PORTTI`. Skripti käyttää oletuksena **`--no-audio`** (Shieldin äänipolku kaataa muuten scrcpy:n); lisäargumentit: `./scripts/mirror-shield.sh 192.168.1.116:5555 --record=file.mp4`.

2. **Android Studio**: **Android Studio → Settings** (macOS: **Preferences**) → **Tools → Device Mirroring** → ota käyttöön **Enable mirroring of physical Android devices** (halutessasi myös automaattinen peilaus kun laite yhdistyy / kun sovellus käynnistetään). Varmista että Shield on **adb devices** -listalla, sitten **View → Tool Windows → Running Devices** (tai vastaava) — näet laitteen ruudun Studion sisällä. Langaton ADB riittää, kun yhteys on kunnossa.

### Autokäynnistys ja näyttö 24/7

- **Launcher**: aseta kotinäyttö-sovellus oletuskäynnistysohjelmaksi (Shield TV:n asetukset → Apps → oletus; laitekohtainen).
- **Näytönsäästäjä (Daydream)**: sovelluksessa on **`KotiDreamService`** (lista nimellä **Kodinäyttö — maisema**) — sama perhedata ja YoWindow-tyylinen maisematausta kuin dashboardissa. Valitse se Shieldin näytönsäästäjä-asetuksista; dashboard-sovellus voi edelleen olla erikseen käynnistyvänä.
- **Näyttöpoltto**: käytä tummaa teemaa (sovellus) ja vaihtelevaa taustaa (sää + kuvat) – vähentää kiinteitä kirkkaita elementtejä.

## 4. Rakenne

- [`supabase/migrations/`](supabase/migrations/) – skeema, RLS, RPC:t (`get_dashboard`, kauppa, aikataulut, lounaat).
- [`supabase/functions/`](supabase/functions/) – RSS, Open-Meteo -sää, kuvan ingest.
- [`android-tv/`](android-tv/) – Kotlin + Compose, `get_dashboard` + kaupan polling.
- [`web/`](web/) – muokkaussivut ja Realtime kauppalistalle.

## 5. Turvallisuus (MVP)

Yhden perheen asennus: anon-luku rajattu `single_family_id()`-mallilla. **Älä jaa** Supabase anon -avainta julkisesti laajasti; token-URLit (`?t=`) ovat käytännössä salasanoja – tallenna vain perheen laitteille tai `.env`-tiedostoihin.

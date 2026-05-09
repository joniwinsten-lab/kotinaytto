-- Kodin infonäyttö – initial schema (single-family Supabase project)
-- Tokens: TV reads via RPC; anon Realtime uses first row in families (single-tenant).

create extension if not exists "pgcrypto";

-- ---------------------------------------------------------------------------
-- Core
-- ---------------------------------------------------------------------------

create table public.families (
  id uuid primary key default gen_random_uuid(),
  name text not null default 'Perhe',
  read_token uuid not null default gen_random_uuid(),
  home_latitude double precision,
  home_longitude double precision,
  weather_location_label text,
  created_at timestamptz not null default now()
);

comment on column public.families.read_token is 'Secret UUID used by TV app + get_dashboard RPC';

insert into public.families (name)
select 'Perhe'
where not exists (select 1 from public.families);

-- Ensure exactly one family row exists for policies (single-tenant MVP)
create or replace function public.single_family_id()
returns uuid
language sql
stable
security definer
set search_path = public
as $$
  select id from public.families order by created_at asc limit 1;
$$;

-- ---------------------------------------------------------------------------
-- Editor tokens (Been / Maija / Joni / shared)
-- ---------------------------------------------------------------------------

create table public.editor_tokens (
  id uuid primary key default gen_random_uuid(),
  family_id uuid not null references public.families(id) on delete cascade,
  slug text not null check (slug in ('been', 'maija', 'joni', 'shared')),
  secret uuid not null default gen_random_uuid(),
  label text,
  created_at timestamptz not null default now(),
  unique (family_id, slug)
);

comment on table public.editor_tokens is 'Per-person or shared edit links; secret is the token value in URLs';

-- ---------------------------------------------------------------------------
-- Schedules (school / shifts / work days)
-- ---------------------------------------------------------------------------

create table public.schedule_entries (
  id uuid primary key default gen_random_uuid(),
  family_id uuid not null references public.families(id) on delete cascade,
  person_slug text not null check (person_slug in ('been', 'maija', 'joni')),
  entry_date date not null,
  title text not null,
  notes text,
  sort_order int not null default 0,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create index schedule_entries_family_date_idx on public.schedule_entries (family_id, entry_date);
create index schedule_entries_person_idx on public.schedule_entries (family_id, person_slug);

-- ---------------------------------------------------------------------------
-- Shopping list
-- ---------------------------------------------------------------------------

create table public.shopping_items (
  id uuid primary key default gen_random_uuid(),
  family_id uuid not null references public.families(id) on delete cascade,
  title text not null,
  done boolean not null default false,
  sort_order int not null default 0,
  added_by text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create index shopping_items_family_sort_idx on public.shopping_items (family_id, sort_order);

do $$
begin
  alter publication supabase_realtime add table public.shopping_items;
exception
  when duplicate_object then null;
end $$;

-- ---------------------------------------------------------------------------
-- Meals & wishes
-- ---------------------------------------------------------------------------

create table public.weekly_meals (
  id uuid primary key default gen_random_uuid(),
  family_id uuid not null references public.families(id) on delete cascade,
  week_start date not null,
  day_index int not null check (day_index >= 0 and day_index <= 6),
  meal_text text not null default '',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (family_id, week_start, day_index)
);

create table public.meal_wishes (
  id uuid primary key default gen_random_uuid(),
  family_id uuid not null references public.families(id) on delete cascade,
  wish_text text not null,
  created_by text,
  created_at timestamptz not null default now()
);

-- ---------------------------------------------------------------------------
-- Photos metadata (files in Storage)
-- ---------------------------------------------------------------------------

create table public.photos (
  id uuid primary key default gen_random_uuid(),
  family_id uuid not null references public.families(id) on delete cascade,
  storage_path text not null,
  added_by_slug text check (added_by_slug is null or added_by_slug in ('been', 'maija', 'joni')),
  created_at timestamptz not null default now()
);

create index photos_family_idx on public.photos (family_id, created_at desc);

-- ---------------------------------------------------------------------------
-- News cache (filled by Edge Function / cron)
-- ---------------------------------------------------------------------------

create table public.news_items (
  id uuid primary key default gen_random_uuid(),
  family_id uuid not null references public.families(id) on delete cascade,
  source text not null,
  title text not null,
  url text,
  published_at timestamptz,
  fetched_at timestamptz not null default now()
);

create index news_items_family_fetched_idx on public.news_items (family_id, fetched_at desc);

-- ---------------------------------------------------------------------------
-- Weather cache (optional server-side; TV may also call Open-Meteo directly)
-- ---------------------------------------------------------------------------

create table public.weather_cache (
  family_id uuid primary key references public.families(id) on delete cascade,
  payload jsonb not null default '{}'::jsonb,
  fetched_at timestamptz not null default now()
);

-- ---------------------------------------------------------------------------
-- RLS
-- ---------------------------------------------------------------------------

alter table public.families enable row level security;
alter table public.editor_tokens enable row level security;
alter table public.schedule_entries enable row level security;
alter table public.shopping_items enable row level security;
alter table public.weekly_meals enable row level security;
alter table public.meal_wishes enable row level security;
alter table public.photos enable row level security;
alter table public.news_items enable row level security;
alter table public.weather_cache enable row level security;

-- Single-tenant: anon can read family-scoped rows (first family). No public SELECT on families / editor_tokens.
-- Mutations from web use SECURITY DEFINER RPCs with editor secrets.

-- Schedules: readable on dashboard
create policy schedule_select on public.schedule_entries
  for select to anon, authenticated
  using (family_id = public.single_family_id());

-- Shopping: realtime needs SELECT for anon
create policy shopping_select on public.shopping_items
  for select to anon, authenticated
  using (family_id = public.single_family_id());

create policy shopping_modify on public.shopping_items
  for all to authenticated
  using (family_id = public.single_family_id())
  with check (family_id = public.single_family_id());

-- Allow anon inserts/updates/deletes only via RPC (no direct anon policy)

create policy meals_select on public.weekly_meals
  for select to anon, authenticated
  using (family_id = public.single_family_id());

create policy wishes_select on public.meal_wishes
  for select to anon, authenticated
  using (family_id = public.single_family_id());

create policy photos_select on public.photos
  for select to anon, authenticated
  using (family_id = public.single_family_id());

create policy news_select on public.news_items
  for select to anon, authenticated
  using (family_id = public.single_family_id());

create policy weather_select on public.weather_cache
  for select to anon, authenticated
  using (family_id = public.single_family_id());

-- ---------------------------------------------------------------------------
-- Token helpers (SECURITY DEFINER)
-- ---------------------------------------------------------------------------

create or replace function public.family_id_for_editor(p_secret uuid, p_slug text)
returns uuid
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  fid uuid;
begin
  select family_id into fid
  from public.editor_tokens
  where secret = p_secret and slug = p_slug;
  return fid;
end;
$$;

create or replace function public.family_id_for_shared_editor(p_secret uuid)
returns uuid
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  fid uuid;
begin
  select family_id into fid
  from public.editor_tokens
  where secret = p_secret and slug = 'shared';
  return fid;
end;
$$;

-- ---------------------------------------------------------------------------
-- Dashboard bundle for TV (validates read_token)
-- ---------------------------------------------------------------------------

create or replace function public.get_dashboard(p_read_token uuid)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  fid uuid;
  fs date := date_trunc('week', timezone('Europe/Helsinki', now()))::date;
begin
  select id into fid from public.families where read_token = p_read_token;
  if fid is null then
    raise exception 'invalid read token';
  end if;

  return jsonb_build_object(
    'family', (
      select jsonb_build_object(
        'id', f.id,
        'name', f.name,
        'home_latitude', f.home_latitude,
        'home_longitude', f.home_longitude,
        'weather_location_label', f.weather_location_label
      )
      from public.families f where id = fid
    ),
    'shopping_items', coalesce(
      (select jsonb_agg(to_jsonb(s) order by s.sort_order, s.created_at)
       from public.shopping_items s where s.family_id = fid),
      '[]'::jsonb
    ),
    'schedules', coalesce(
      (select jsonb_agg(to_jsonb(e) order by e.entry_date, e.sort_order)
       from public.schedule_entries e where e.family_id = fid),
      '[]'::jsonb
    ),
    'weekly_meals', coalesce(
      (select jsonb_agg(to_jsonb(m) order by m.week_start desc, m.day_index)
       from public.weekly_meals m where m.family_id = fid and m.week_start = fs),
      '[]'::jsonb
    ),
    'meal_wishes', coalesce(
      (select jsonb_agg(to_jsonb(w) order by w.created_at desc)
       from (
         select * from public.meal_wishes mw
         where mw.family_id = fid
         order by mw.created_at desc
         limit 50
       ) w),
      '[]'::jsonb
    ),
    'photos', coalesce(
      (select jsonb_agg(to_jsonb(p) order by p.created_at desc)
       from (
         select * from public.photos ph
         where ph.family_id = fid
         order by ph.created_at desc
         limit 100
       ) p),
      '[]'::jsonb
    ),
    'news_items', coalesce(
      (select jsonb_agg(to_jsonb(n) order by n.fetched_at desc)
       from (
         select * from public.news_items ni
         where ni.family_id = fid
         order by ni.fetched_at desc
         limit 30
       ) n),
      '[]'::jsonb
    ),
    'weather_cache', (select to_jsonb(w) from public.weather_cache w where w.family_id = fid)
  );
end;
$$;

grant execute on function public.get_dashboard(uuid) to anon, authenticated;

-- ---------------------------------------------------------------------------
-- Shopping RPCs (shared editor token)
-- ---------------------------------------------------------------------------

create or replace function public.shopping_add(p_secret uuid, p_title text, p_added_by text default null)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
  fid uuid := public.family_id_for_shared_editor(p_secret);
  mx int;
  new_id uuid;
begin
  if fid is null then raise exception 'invalid editor token'; end if;
  select coalesce(max(sort_order), 0) into mx from public.shopping_items where family_id = fid;
  insert into public.shopping_items (family_id, title, sort_order, added_by)
  values (fid, trim(p_title), mx + 1, p_added_by)
  returning id into new_id;
  return new_id;
end;
$$;

create or replace function public.shopping_toggle_done(p_secret uuid, p_item_id uuid)
returns boolean
language plpgsql
security definer
set search_path = public
as $$
declare
  fid uuid := public.family_id_for_shared_editor(p_secret);
  new_done boolean;
begin
  if fid is null then raise exception 'invalid editor token'; end if;
  update public.shopping_items
  set done = not done, updated_at = now()
  where id = p_item_id and family_id = fid
  returning done into new_done;
  if new_done is null then raise exception 'not found'; end if;
  return new_done;
end;
$$;

create or replace function public.shopping_delete(p_secret uuid, p_item_id uuid)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  fid uuid := public.family_id_for_shared_editor(p_secret);
begin
  if fid is null then raise exception 'invalid editor token'; end if;
  delete from public.shopping_items where id = p_item_id and family_id = fid;
end;
$$;

create or replace function public.shopping_clear_done(p_secret uuid)
returns int
language plpgsql
security definer
set search_path = public
as $$
declare
  fid uuid := public.family_id_for_shared_editor(p_secret);
  deleted int;
begin
  if fid is null then raise exception 'invalid editor token'; end if;
  delete from public.shopping_items where family_id = fid and done = true;
  get diagnostics deleted = row_count;
  return deleted;
end;
$$;

grant execute on function public.shopping_add(uuid, text, text) to anon, authenticated;
grant execute on function public.shopping_toggle_done(uuid, uuid) to anon, authenticated;
grant execute on function public.shopping_delete(uuid, uuid) to anon, authenticated;
grant execute on function public.shopping_clear_done(uuid) to anon, authenticated;

-- ---------------------------------------------------------------------------
-- Schedule RPCs (per-person editor token)
-- ---------------------------------------------------------------------------

create or replace function public.schedule_upsert(
  p_secret uuid,
  p_person_slug text,
  p_entry_id uuid,
  p_entry_date date,
  p_title text,
  p_notes text default null
)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
  fid uuid := public.family_id_for_editor(p_secret, p_person_slug);
  rid uuid;
begin
  if fid is null then raise exception 'invalid editor token'; end if;
  if p_entry_id is null then
    insert into public.schedule_entries (family_id, person_slug, entry_date, title, notes)
    values (fid, p_person_slug, p_entry_date, trim(p_title), p_notes)
    returning id into rid;
  else
    update public.schedule_entries
    set entry_date = p_entry_date,
        title = trim(p_title),
        notes = p_notes,
        updated_at = now()
    where id = p_entry_id and family_id = fid and person_slug = p_person_slug
    returning id into rid;
    if rid is null then raise exception 'not found'; end if;
  end if;
  return rid;
end;
$$;

create or replace function public.schedule_delete(p_secret uuid, p_person_slug text, p_entry_id uuid)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  fid uuid := public.family_id_for_editor(p_secret, p_person_slug);
begin
  if fid is null then raise exception 'invalid editor token'; end if;
  delete from public.schedule_entries
  where id = p_entry_id and family_id = fid and person_slug = p_person_slug;
end;
$$;

grant execute on function public.schedule_upsert(uuid, text, uuid, date, text, text) to anon, authenticated;
grant execute on function public.schedule_delete(uuid, text, uuid) to anon, authenticated;

-- ---------------------------------------------------------------------------
-- Meals RPCs (shared editor)
-- ---------------------------------------------------------------------------

create or replace function public.meals_set_week_day(
  p_secret uuid,
  p_week_start date,
  p_day_index int,
  p_meal_text text
)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  fid uuid := public.family_id_for_shared_editor(p_secret);
begin
  if fid is null then raise exception 'invalid editor token'; end if;
  insert into public.weekly_meals (family_id, week_start, day_index, meal_text)
  values (fid, p_week_start, p_day_index, trim(p_meal_text))
  on conflict (family_id, week_start, day_index)
  do update set meal_text = excluded.meal_text, updated_at = now();
end;
$$;

create or replace function public.meal_wish_add(p_secret uuid, p_text text, p_created_by text default null)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
  fid uuid := public.family_id_for_shared_editor(p_secret);
  nid uuid;
begin
  if fid is null then raise exception 'invalid editor token'; end if;
  insert into public.meal_wishes (family_id, wish_text, created_by)
  values (fid, trim(p_text), p_created_by)
  returning id into nid;
  return nid;
end;
$$;

create or replace function public.meal_wish_delete(p_secret uuid, p_wish_id uuid)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  fid uuid := public.family_id_for_shared_editor(p_secret);
begin
  if fid is null then raise exception 'invalid editor token'; end if;
  delete from public.meal_wishes where id = p_wish_id and family_id = fid;
end;
$$;

grant execute on function public.meals_set_week_day(uuid, date, int, text) to anon, authenticated;
grant execute on function public.meal_wish_add(uuid, text, text) to anon, authenticated;
grant execute on function public.meal_wish_delete(uuid, uuid) to anon, authenticated;

-- ---------------------------------------------------------------------------
-- Photo registration after upload (shared editor)
-- ---------------------------------------------------------------------------

create or replace function public.photo_register(p_secret uuid, p_storage_path text, p_added_by_slug text default null)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
  fid uuid := public.family_id_for_shared_editor(p_secret);
  nid uuid;
begin
  if fid is null then raise exception 'invalid editor token'; end if;
  insert into public.photos (family_id, storage_path, added_by_slug)
  values (fid, p_storage_path, p_added_by_slug)
  returning id into nid;
  return nid;
end;
$$;

grant execute on function public.photo_register(uuid, text, text) to anon, authenticated;

-- Seed editor token rows for the default family (secrets generated once)
insert into public.editor_tokens (family_id, slug, label)
select f.id, v.slug, v.lbl
from public.families f
cross join (
  values
    ('been', 'Been'),
    ('maija', 'Maija'),
    ('joni', 'Joni'),
    ('shared', 'Yhteinen')
) as v(slug, lbl)
on conflict (family_id, slug) do nothing;

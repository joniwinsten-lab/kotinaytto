-- PIN-kirjautuminen hallintasivulle (single-family). PIN ei näy TV:lle.

create table public.editor_pins (
  family_id uuid not null references public.families(id) on delete cascade,
  slug text not null check (slug in ('been', 'maija', 'joni')),
  pin_code text not null check (length(trim(pin_code)) >= 4),
  primary key (family_id, slug),
  unique (family_id, pin_code)
);

alter table public.editor_pins enable row level security;

-- Ei suoria SELECT-politiikkoja anonille — vain SECURITY DEFINER -RPC.

create or replace function public.exchange_editor_pin(p_pin text)
returns json
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  fid uuid := public.single_family_id();
  matched_slug text;
  sched_secret uuid;
  shr_secret uuid;
begin
  if fid is null then raise exception 'no family'; end if;

  select ep.slug into matched_slug
  from public.editor_pins ep
  where ep.family_id = fid and ep.pin_code = trim(p_pin)
  limit 1;

  if matched_slug is null then raise exception 'invalid pin'; end if;

  select et.secret into sched_secret
  from public.editor_tokens et
  where et.family_id = fid and et.slug = matched_slug;

  select et.secret into shr_secret
  from public.editor_tokens et
  where et.family_id = fid and et.slug = 'shared';

  if sched_secret is null or shr_secret is null then raise exception 'tokens missing'; end if;

  return json_build_object(
    'person_slug', matched_slug,
    'schedule_secret', sched_secret::text,
    'shared_secret', shr_secret::text,
    'can_weekly_meals', matched_slug in ('maija', 'joni'),
    'can_meal_wishes', matched_slug = 'been'
  );
end;
$$;

grant execute on function public.exchange_editor_pin(text) to anon, authenticated;

-- Alustuspinnit (vaihda tuotannossa halutessasi): Maija 2505, Joni 0407, Bee 0304
insert into public.editor_pins (family_id, slug, pin_code)
select public.single_family_id(), v.slug, v.pin
from (
  values
    ('maija'::text, '2505'::text),
    ('joni'::text, '0407'::text),
    ('been'::text, '0304'::text)
) as v(slug, pin)
on conflict (family_id, slug) do update set pin_code = excluded.pin_code;

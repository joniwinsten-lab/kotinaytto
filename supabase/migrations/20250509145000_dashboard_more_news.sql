-- Dashboardiin enemmän uutisrivejä, jotta ticker voi pyörittää koko syötettä pidempään.

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
         limit 120
       ) n),
      '[]'::jsonb
    ),
    'weather_cache', (select to_jsonb(w) from public.weather_cache w where w.family_id = fid)
  );
end;
$$;

grant execute on function public.get_dashboard(uuid) to anon, authenticated;


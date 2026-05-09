-- Week start (ISO Monday) in Europe/Helsinki for meal editors

create or replace function public.current_week_start_hel()
returns date
language sql
stable
security definer
set search_path = public
as $$
  select (date_trunc('week', timezone('Europe/Helsinki', now())))::date;
$$;

grant execute on function public.current_week_start_hel() to anon, authenticated;

-- Public bucket for family photos (readable URLs on TV). Writes via Edge Function service role only.

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values (
  'family_photos',
  'family_photos',
  true,
  10485760,
  array['image/jpeg', 'image/png', 'image/webp', 'image/heic', 'image/heif']
)
on conflict (id) do update set public = excluded.public;

create policy "family_photos_select_public"
on storage.objects for select
to public
using (bucket_id = 'family_photos');

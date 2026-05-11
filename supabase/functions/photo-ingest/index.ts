import { createClient } from "https://esm.sh/@supabase/supabase-js@2.49.1";
import { corsHeaders } from "../_shared/cors.ts";

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  if (req.method !== "POST") {
    return new Response("Method not allowed", { status: 405, headers: corsHeaders });
  }

  const supabaseUrl = Deno.env.get("SUPABASE_URL")!;
  const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
  const supabase = createClient(supabaseUrl, serviceKey);

  const form = await req.formData();
  const token = String(form.get("token") ?? "");
  const addedBy = form.get("added_by_slug");
  const file = form.get("file");

  if (!token || !(file instanceof File)) {
    return new Response(JSON.stringify({ error: "token and file required" }), {
      status: 400,
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
  }

  const { data: row, error: tokErr } = await supabase
    .from("editor_tokens")
    .select("family_id")
    .eq("secret", token)
    .maybeSingle();

  if (tokErr || !row?.family_id) {
    return new Response(JSON.stringify({ error: "invalid token" }), {
      status: 401,
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
  }

  const familyId = row.family_id as string;
  const ext = (file.name.split(".").pop() || "jpg").toLowerCase();
  const objectPath = `${familyId}/${crypto.randomUUID()}.${ext}`;

  const buf = new Uint8Array(await file.arrayBuffer());
  const { error: upErr } = await supabase.storage.from("family_photos").upload(objectPath, buf, {
    contentType: file.type || "image/jpeg",
    upsert: false,
  });

  if (upErr) {
    return new Response(JSON.stringify({ error: upErr.message }), {
      status: 500,
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
  }

  const { data: photo, error: insErr } = await supabase
    .from("photos")
    .insert({
      family_id: familyId,
      storage_path: objectPath,
      added_by_slug: addedBy ? String(addedBy) : null,
    })
    .select("id")
    .single();

  if (insErr) {
    return new Response(JSON.stringify({ error: insErr.message }), {
      status: 500,
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
  }

  return new Response(JSON.stringify({ ok: true, id: photo.id, path: objectPath }), {
    headers: { ...corsHeaders, "Content-Type": "application/json" },
  });
});

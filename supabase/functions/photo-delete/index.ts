import { createClient } from "https://esm.sh/@supabase/supabase-js@2.49.1";
import { corsHeaders } from "../_shared/cors.ts";

type Body = { token?: string; photo_id?: string };

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  if (req.method !== "POST") {
    return new Response("Method not allowed", { status: 405, headers: corsHeaders });
  }

  let body: Body;
  try {
    body = (await req.json()) as Body;
  } catch {
    return new Response(JSON.stringify({ error: "invalid json" }), {
      status: 400,
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
  }

  const token = String(body.token ?? "").trim();
  const photoId = String(body.photo_id ?? "").trim();

  if (!token || !photoId) {
    return new Response(JSON.stringify({ error: "token and photo_id required" }), {
      status: 400,
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
  }

  const supabaseUrl = Deno.env.get("SUPABASE_URL")!;
  const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
  const supabase = createClient(supabaseUrl, serviceKey);

  const { data: tokRow, error: tokErr } = await supabase
    .from("editor_tokens")
    .select("family_id")
    .eq("slug", "shared")
    .eq("secret", token)
    .maybeSingle();

  if (tokErr || !tokRow?.family_id) {
    return new Response(JSON.stringify({ error: "invalid token" }), {
      status: 401,
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
  }

  const familyId = tokRow.family_id as string;

  const { data: photo, error: pErr } = await supabase
    .from("photos")
    .select("id, storage_path")
    .eq("id", photoId)
    .eq("family_id", familyId)
    .maybeSingle();

  if (pErr || !photo?.storage_path) {
    return new Response(JSON.stringify({ error: "not found" }), {
      status: 404,
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
  }

  const path = photo.storage_path as string;

  const { error: rmErr } = await supabase.storage.from("family_photos").remove([path]);
  if (rmErr) {
    return new Response(JSON.stringify({ error: rmErr.message }), {
      status: 500,
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
  }

  const { error: delErr } = await supabase.from("photos").delete().eq("id", photoId).eq("family_id", familyId);
  if (delErr) {
    return new Response(JSON.stringify({ error: delErr.message }), {
      status: 500,
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
  }

  return new Response(JSON.stringify({ ok: true }), {
    headers: { ...corsHeaders, "Content-Type": "application/json" },
  });
});

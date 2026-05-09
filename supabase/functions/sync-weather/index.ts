import { createClient } from "https://esm.sh/@supabase/supabase-js@2.49.1";
import { corsHeaders } from "../_shared/cors.ts";

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  const supabaseUrl = Deno.env.get("SUPABASE_URL")!;
  const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
  const supabase = createClient(supabaseUrl, serviceKey);

  const { data: fam, error: famErr } = await supabase
    .from("families")
    .select("id, home_latitude, home_longitude")
    .order("created_at", { ascending: true })
    .limit(1)
    .maybeSingle();

  if (famErr || !fam) {
    return new Response(JSON.stringify({ error: "no family", details: famErr }), {
      status: 500,
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
  }

  const lat = fam.home_latitude ?? 60.17;
  const lon = fam.home_longitude ?? 24.94;

  const url =
    `https://api.open-meteo.com/v1/forecast?latitude=${lat}&longitude=${lon}` +
    "&current=temperature_2m,relative_humidity_2m,weather_code,is_day,wind_speed_10m" +
    "&daily=weather_code,temperature_2m_max,temperature_2m_min&timezone=Europe%2FHelsinki";

  const res = await fetch(url);
  const json = await res.json();

  const { error } = await supabase.from("weather_cache").upsert({
    family_id: fam.id,
    payload: json,
    fetched_at: new Date().toISOString(),
  });

  if (error) {
    return new Response(JSON.stringify({ error: error.message }), {
      status: 500,
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
  }

  return new Response(JSON.stringify({ ok: true }), {
    headers: { ...corsHeaders, "Content-Type": "application/json" },
  });
});

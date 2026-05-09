import { createClient } from "https://esm.sh/@supabase/supabase-js@2.49.1";
import { corsHeaders } from "../_shared/cors.ts";

const RSS_URLS = [
  { source: "Yle", url: "https://feeds.yle.fi/uutiset/v1/recent.rss?language=fi" },
];

async function parseRss(xml: string, source: string) {
  const items: { title: string; url?: string; published_at?: string }[] = [];
  const itemRegex = /<item[\s\S]*?<\/item>/gi;
  const blocks = xml.match(itemRegex) ?? [];
  for (const block of blocks.slice(0, 20)) {
    const titleM = block.match(/<title><!\[CDATA\[(.*?)\]\]><\/title>/i) ??
      block.match(/<title>(.*?)<\/title>/i);
    const linkM = block.match(/<link>(.*?)<\/link>/i);
    const pubM = block.match(/<pubDate>(.*?)<\/pubDate>/i);
    const title = titleM?.[1]?.trim();
    if (!title) continue;
    items.push({
      title,
      url: linkM?.[1]?.trim(),
      published_at: pubM?.[1]?.trim(),
    });
  }
  return items.map((i) => ({ ...i, source }));
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  const supabaseUrl = Deno.env.get("SUPABASE_URL")!;
  const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
  const supabase = createClient(supabaseUrl, serviceKey);

  const { data: fam, error: famErr } = await supabase
    .from("families")
    .select("id")
    .order("created_at", { ascending: true })
    .limit(1)
    .maybeSingle();

  if (famErr || !fam) {
    return new Response(JSON.stringify({ error: "no family", details: famErr }), {
      status: 500,
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
  }

  const familyId = fam.id as string;
  const aggregated: { source: string; title: string; url?: string; published_at?: string }[] = [];

  for (const feed of RSS_URLS) {
    try {
      const res = await fetch(feed.url);
      const xml = await res.text();
      const parsed = await parseRss(xml, feed.source);
      aggregated.push(...parsed);
    } catch (e) {
      console.error(feed.url, e);
    }
  }

  await supabase.from("news_items").delete().eq("family_id", familyId);

  if (aggregated.length > 0) {
    const rows = aggregated.slice(0, 30).map((n) => ({
      family_id: familyId,
      source: n.source,
      title: n.title,
      url: n.url ?? null,
      published_at: n.published_at ? new Date(n.published_at).toISOString() : null,
    }));
    const { error } = await supabase.from("news_items").insert(rows);
    if (error) {
      return new Response(JSON.stringify({ error: error.message }), {
        status: 500,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }
  }

  return new Response(JSON.stringify({ ok: true, count: aggregated.length }), {
    headers: { ...corsHeaders, "Content-Type": "application/json" },
  });
});

// Relais des rapports de plantage de WiDo : reçoit le rapport envoyé par l'appli (bouton « Envoyer le
// rapport ») et le dépose dans le dépôt GitHub, sous forme d'issue. C'est le seul endroit qui connaît
// la clé GitHub : l'appli, publique, n'embarque que l'adresse de ce relais.
//
// Pensé pour un Cloudflare Worker (gratuit), mais sans rien de propre à Cloudflare dans la logique :
// `handle(request, env, fetchImpl)` se teste avec `node tools/crash-relay/relay.test.mjs`.

export const MAX_BODY = 12_000;
export const MAX_REPORT = 8_000;
const VERSION = /^\d{1,3}\.\d{1,3}\.\d{1,3}(?:[-+][\w.]{1,20})?$/;
const HEADER = /^WiDo (\d{1,3}\.\d{1,3}\.\d{1,3}\S*) · ([^\n]{1,120})\n/;

const json = (status, body) => new Response(JSON.stringify(body), { status, headers: { 'content-type': 'application/json' } });

/** Ce que le relais accepte : un rapport d'appli WiDo, ni plus gros ni d'une autre forme. */
export function validate(payload) {
    if (!payload || typeof payload !== 'object') return null;
    const { report, version } = payload;
    if (typeof report !== 'string' || typeof version !== 'string') return null;
    if (report.length < 40 || report.length > MAX_REPORT || !VERSION.test(version)) return null;
    const head = HEADER.exec(report);
    if (!head || head[1] !== version) return null;
    return { report, version, device: head[2].trim() };
}

/** La raison de la fermeture, pour le titre et le regroupement des doublons. */
export function reasonOf(report) {
    const line = report.split('\n').find((l) => l.startsWith('Fermeture par Android :') || l.startsWith('Page arrêtée'));
    if (line) return line.replace(/\s*·\s*mémoire.*$/, '').replace(/\s*\(appli à l'écran\)|\s*\(en arrière-plan\)/, '').slice(0, 90);
    const exception = report.split('\n').find((l) => /^[\w.$]+(Exception|Error)\b/.test(l));
    return (exception ?? 'Erreur de l’appli').slice(0, 90);
}

async function sha256Hex(text) {
    const bytes = new TextEncoder().encode(text);
    const digest = await crypto.subtle.digest('SHA-256', bytes);
    return [...new Uint8Array(digest)].map((b) => b.toString(16).padStart(2, '0')).join('');
}

/** Le rapport dans un bloc de code : pas de lien, de @mention ni de HTML interprétés. */
export function asMarkdown(report) {
    return '```text\n' + report.replace(/```/g, "'''") + '\n```';
}

export async function handle(request, env, fetchImpl = fetch) {
    if (request.method !== 'POST') return json(200, { service: 'wido-crash-relay' });
    const raw = await request.text();
    if (raw.length > MAX_BODY) return json(413, { error: 'trop gros' });
    let payload;
    try {
        payload = JSON.parse(raw);
    } catch {
        return json(400, { error: 'json' });
    }
    const valid = validate(payload);
    if (!valid) return json(400, { error: 'rapport invalide' });

    // Anti-spam : une limite par adresse IP (liaison « ratelimit » de Cloudflare, si configurée).
    const ip = request.headers.get('cf-connecting-ip') ?? 'inconnue';
    if (env.LIMITER && !(await env.LIMITER.limit({ key: ip })).success) return json(429, { error: 'trop de rapports' });

    const repo = env.REPO;
    const headers = {
        authorization: `Bearer ${env.GITHUB_TOKEN}`,
        accept: 'application/vnd.github+json',
        'user-agent': 'wido-crash-relay',
        'x-github-api-version': '2022-11-28',
        'content-type': 'application/json',
    };
    const reason = reasonOf(valid.report);
    // Même version + même raison : un seul sujet, les autres rapports s'y ajoutent en commentaire.
    const fingerprint = (await sha256Hex(`${valid.version}|${reason}`)).slice(0, 12);

    const search = await fetchImpl(
        `https://api.github.com/search/issues?q=${encodeURIComponent(`repo:${repo} is:issue label:crash-report in:body "fp:${fingerprint}"`)}&per_page=1`,
        { headers },
    );
    const found = search.ok ? (await search.json()).items?.[0] : null;
    if (found) {
        const comment = await fetchImpl(`https://api.github.com/repos/${repo}/issues/${found.number}/comments`, {
            method: 'POST',
            headers,
            body: JSON.stringify({ body: `Un autre appareil : **${valid.device}**\n\n${asMarkdown(valid.report)}` }),
        });
        return comment.ok ? json(200, { issue: found.number, duplicate: true }) : json(502, { error: 'github' });
    }
    const created = await fetchImpl(`https://api.github.com/repos/${repo}/issues`, {
        method: 'POST',
        headers,
        body: JSON.stringify({
            title: `[plantage] ${valid.version} · ${reason}`.slice(0, 200),
            body: `Rapport envoyé depuis l'appli (bouton « Envoyer le rapport »).\n\n${asMarkdown(valid.report)}\n\n<!-- fp:${fingerprint} -->`,
            labels: ['crash-report'],
        }),
    });
    if (!created.ok) return json(502, { error: 'github' });
    return json(201, { issue: (await created.json()).number });
}

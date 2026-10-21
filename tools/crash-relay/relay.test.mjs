// Tests du relais de rapports. Lancer : node tools/crash-relay/relay.test.mjs
import assert from 'node:assert/strict';
import { handle, validate, reasonOf, asMarkdown, MAX_REPORT } from './relay.mjs';

const report = (extra = '') =>
    `WiDo 1.0.3 · SkyworthDigital TV Stick · Android 11 (API 30)\n2026-10-21 20:04:36\nFermeture par Android : mémoire insuffisante (appli à l'écran) · mémoire 238 Mo\n\nDerniers événements :\n2026-10-21 19:25:24  Lecteur ouvert : site${extra}`;
const post = (body, headers = {}) => new Request('https://relay.example/', { method: 'POST', body: typeof body === 'string' ? body : JSON.stringify(body), headers });
const env = { REPO: 'wicra/WiDo', GITHUB_TOKEN: 'secret-token' };

// Validation
assert.equal(validate(null), null);
assert.equal(validate({ report: report(), version: '1.0.4' }), null, 'la version doit être celle de l\'en-tête');
assert.equal(validate({ report: 'trop court', version: '1.0.3' }), null);
assert.equal(validate({ report: 'x'.repeat(MAX_REPORT + 1), version: '1.0.3' }), null);
assert.equal(validate({ report: report().replace('WiDo 1.0.3', 'Autre 1.0.3'), version: '1.0.3' }), null);
assert.deepEqual(validate({ report: report(), version: '1.0.3' }), { report: report(), version: '1.0.3', device: 'SkyworthDigital TV Stick · Android 11 (API 30)' });

// Raison
assert.equal(reasonOf(report()), 'Fermeture par Android : mémoire insuffisante');
assert.equal(reasonOf('WiDo 1.0.3 · X\njava.lang.IllegalStateException: boum\n at a.b'), 'java.lang.IllegalStateException: boum');

// Markdown : pas de bloc de code qui s'échappe
assert.ok(!asMarkdown('a ``` @tous <img src=x>').slice(8, -4).includes('```'));

// Appels GitHub simulés
function github(searchResult) {
    const calls = [];
    const fetchImpl = async (url, init = {}) => {
        calls.push({ url, init });
        if (url.includes('/search/issues')) return new Response(JSON.stringify({ items: searchResult }), { status: 200 });
        if (url.endsWith('/comments')) return new Response('{}', { status: 201 });
        return new Response(JSON.stringify({ number: 42 }), { status: 201 });
    };
    return { calls, fetchImpl };
}

{ // Nouveau problème : une issue, étiquetée, avec l'empreinte
    const gh = github([]);
    const res = await handle(post({ report: report(), version: '1.0.3' }), env, gh.fetchImpl);
    assert.equal(res.status, 201);
    assert.equal((await res.json()).issue, 42);
    const create = gh.calls.find((c) => c.url.endsWith('/issues'));
    const sent = JSON.parse(create.init.body);
    assert.match(sent.title, /^\[plantage\] 1\.0\.3 · Fermeture par Android : mémoire insuffisante$/);
    assert.deepEqual(sent.labels, ['crash-report']);
    assert.match(sent.body, /<!-- fp:[0-9a-f]{12} -->/);
    assert.equal(create.init.headers.authorization, 'Bearer secret-token');
}

{ // Même problème déjà signalé : un commentaire, pas une nouvelle issue
    const gh = github([{ number: 7 }]);
    const res = await handle(post({ report: report(), version: '1.0.3' }), env, gh.fetchImpl);
    const body = await res.json();
    assert.equal(res.status, 200);
    assert.equal(body.duplicate, true);
    assert.ok(gh.calls.some((c) => c.url.endsWith('/issues/7/comments')));
    assert.ok(!gh.calls.some((c) => c.url.endsWith('/issues') && c.init.method === 'POST'));
}

{ // Rejets : méthode, taille, JSON, forme
    const gh = github([]);
    assert.equal((await handle(new Request('https://relay.example/'), env, gh.fetchImpl)).status, 200);
    assert.equal((await handle(post('x'.repeat(13_000)), env, gh.fetchImpl)).status, 413);
    assert.equal((await handle(post('pas du json'), env, gh.fetchImpl)).status, 400);
    assert.equal((await handle(post({ report: 'hello', version: '1.0.3' }), env, gh.fetchImpl)).status, 400);
    assert.equal(gh.calls.length, 0, 'rien n\'est envoyé à GitHub pour une requête invalide');
}

{ // Anti-spam
    const gh = github([]);
    const limited = { ...env, LIMITER: { limit: async () => ({ success: false }) } };
    assert.equal((await handle(post({ report: report(), version: '1.0.3' }), limited, gh.fetchImpl)).status, 429);
    assert.equal(gh.calls.length, 0);
}

{ // GitHub en panne
    const res = await handle(post({ report: report(), version: '1.0.3' }), env, async () => new Response('{}', { status: 500 }));
    assert.equal(res.status, 502);
}

console.log('relay: tous les tests passent');

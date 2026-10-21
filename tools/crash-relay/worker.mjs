// Point d'entrée Cloudflare Worker. Variables : REPO (ex. wicra/WiDo), secret GITHUB_TOKEN.
import { handle } from './relay.mjs';

export default { fetch: (request, env) => handle(request, env) };

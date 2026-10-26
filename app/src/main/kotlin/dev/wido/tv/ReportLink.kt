package dev.wido.tv

import dev.wido.core.MiniHttpServer
import dev.wido.core.PhoneProtocol
import java.security.SecureRandom

/**
 * Le rapport de plantage en entier sur le téléphone : un QR code ne tient que 1 200 signes environ, le
 * rapport (journal détaillé compris) bien plus. Le QR code donne donc l'adresse d'une page servie par la TV
 * sur le réseau local, ouverte seulement tant que le rapport est affiché, protégée par un jeton aléatoire.
 */
class ReportLink(
    /** Le rapport au moment de la demande (le journal détaillé continue de s'écrire pendant que la fenêtre est ouverte). */
    private val report: () -> String,
    private val title: String, private val copyLabel: String, private val shareLabel: String,
) {
    private val token = ByteArray(16).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
    private val server = MiniHttpServer("WiDoReport", log = { m, e -> android.util.Log.w("WiDoHttp", m, e) }) { request, _ ->
        when {
            !PhoneProtocol.tokenMatches(token, request.query["t"]) -> MiniHttpServer.FORBIDDEN
            request.method == "GET" && request.path == "/" -> MiniHttpServer.Response(200, "text/html; charset=utf-8", page())
            request.method == "GET" && request.path == "/report.txt" -> MiniHttpServer.Response(200, "text/plain; charset=utf-8", report())
            else -> MiniHttpServer.NOT_FOUND
        }
    }

    /** Démarre le serveur ; renvoie l'adresse à mettre dans le QR code, ou null sans réseau local. */
    fun start(): String? {
        val ip = MiniHttpServer.localAddress() ?: return null
        val port = server.start(PORT) ?: return null
        // `w` : l'appli WiDo du téléphone qui scanne le code affiche le rapport elle-même (les navigateurs l'ignorent).
        return dev.wido.core.ScanCodes.tag("http://$ip:$port/?t=$token", dev.wido.core.ScanCodes.REPORT)
    }

    fun stop() = server.stop()

    private fun page(): String = """<!doctype html><html><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1"><title>${escape(title)}</title>
<style>body{margin:0;background:#0B0B12;color:#E8E8F0;font:15px system-ui,sans-serif}
header{position:sticky;top:0;display:flex;gap:8px;padding:12px;background:#15151F}
button{flex:1;padding:12px;border:0;border-radius:10px;background:#7C6CFF;color:#fff;font-weight:700;font-size:15px}
pre{margin:0;padding:12px;white-space:pre-wrap;word-break:break-word;font:12px/1.45 ui-monospace,monospace}</style></head>
<body><header><button id="c">${escape(copyLabel)}</button><button id="s">${escape(shareLabel)}</button></header>
<pre id="r">${escape(report())}</pre>
<script>
const text=document.getElementById('r').textContent;
document.getElementById('c').onclick=async()=>{try{await navigator.clipboard.writeText(text)}catch(e){const r=document.createRange();r.selectNodeContents(document.getElementById('r'));const s=getSelection();s.removeAllRanges();s.addRange(r);document.execCommand('copy')}};
const share=document.getElementById('s');
if(navigator.share)share.onclick=()=>navigator.share({title:document.title,text}).catch(()=>{});else share.style.display='none';
</script></body></html>"""

    private fun escape(text: String) = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private companion object {
        const val PORT = 8766
    }
}

package dev.wido.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Touches de la télécommande sur téléphone. */
enum class RemoteKey { UP, DOWN, LEFT, RIGHT, OK, BACK, HOME, MENU, PLAY_PAUSE, REWIND, FORWARD, VOLUME_UP, VOLUME_DOWN, MUTE, DEL, PLAY, PAUSE }

/** Une commande envoyée par la télécommande sur téléphone. */
sealed interface RemoteCommand {
    data class Key(val key: RemoteKey) : RemoteCommand
    /** Pavé tactile : déplacement relatif du curseur, en points d'écran du téléphone. */
    data class Move(val dx: Float, val dy: Float) : RemoteCommand
    data object Tap : RemoteCommand
    data class Scroll(val dy: Float) : RemoteCommand
    data class Type(val text: String, val submit: Boolean) : RemoteCommand
    data class Open(val url: String) : RemoteCommand
    /** Va à cette position (secondes) dans la vidéo en cours. */
    data class Seek(val position: Double) : RemoteCommand
}

/**
 * Protocole de la télécommande sur téléphone : le téléphone, appairé une fois par QR code (jeton
 * permanent dans l'adresse), envoie des commandes JSON à la TV tant que WiDo est ouvert.
 */
object RemoteProtocol {
    const val MAX_MOVE = 400f

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** `{"cmd":"key","key":"up"}`, `{"cmd":"move","dx":3,"dy":-2}`, `{"cmd":"open","url":"…"}`… ; null si invalide. */
    fun parse(body: String): RemoteCommand? {
        val o = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null
        fun str(k: String) = runCatching { o[k]?.jsonPrimitive?.content }.getOrNull()
        fun num(k: String) = runCatching { o[k]?.jsonPrimitive?.floatOrNull }.getOrNull()
        return when (str("cmd")) {
            "key" -> str("key")?.let { k -> RemoteKey.entries.firstOrNull { it.name.equals(k.replace('-', '_'), ignoreCase = true) } }?.let { RemoteCommand.Key(it) }
            "move" -> {
                val dx = num("dx") ?: return null
                val dy = num("dy") ?: return null
                if (dx.isNaN() || dy.isNaN()) return null
                RemoteCommand.Move(dx.coerceIn(-MAX_MOVE, MAX_MOVE), dy.coerceIn(-MAX_MOVE, MAX_MOVE))
            }
            "tap" -> RemoteCommand.Tap
            "scroll" -> num("dy")?.takeIf { !it.isNaN() }?.let { RemoteCommand.Scroll(it.coerceIn(-2000f, 2000f)) }
            "type" -> {
                val text = str("text").orEmpty().replace("\r", "").trimEnd('\n')
                val submit = bool(o, "submit")
                // Texte vide accepté : le clavier en direct du téléphone envoie le champ entier, effacé compris.
                if (text.length > PhoneProtocol.MAX_TEXT) null else RemoteCommand.Type(text, submit)
            }
            "open" -> str("url")?.let { LocalSites.normalizeUrl(it) }
                ?.takeIf { val s = Hosts.schemeOf(it); s == "https" || s == "http" }
                ?.let { RemoteCommand.Open(it) }
            "seek" -> num("to")?.takeIf { !it.isNaN() && it >= 0 }?.let { RemoteCommand.Seek(it.toDouble()) }
            else -> null
        }
    }

    private fun bool(o: JsonObject, key: String) = runCatching { o[key]?.jsonPrimitive?.booleanOrNull }.getOrNull() == true

    /** Libellés de la page, dans la langue de la TV. */
    data class Labels(
        val touchpad: String,
        val keys: String,
        val text: String,
        val link: String,
        val touchpadHint: String,
        val scroll: String,
        val placeholder: String,
        val send: String,
        val sendEnter: String,
        val linkPlaceholder: String,
        val open: String,
        val sent: String,
        val failed: String,
    )

    /** La page de la télécommande : pavé tactile, touches, texte, lien. */
    fun page(token: String, l: Labels): String {
        fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
        fun js(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("<", "\\u003c") + "\""
        return """<!doctype html><html><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no">
<meta name="theme-color" content="#0B0B12"><title>WiDo</title>
<style>
*{box-sizing:border-box;-webkit-tap-highlight-color:transparent}
html,body{margin:0;height:100%;background:#0B0B12;color:#fff;font:16px system-ui,sans-serif;overflow:hidden}
body{display:flex;flex-direction:column;background:radial-gradient(ellipse 80% 50% at 10% 0%,#1E5BFF,transparent 70%),radial-gradient(ellipse 70% 50% at 100% 100%,#7A2BE0,transparent 70%),#0B0B12}
header{padding:16px 20px 8px;font-size:24px;font-weight:800;letter-spacing:-.5px}
main{flex:1;display:flex;padding:8px 16px;min-height:0}
section{display:none;flex:1;flex-direction:column;gap:12px;min-height:0}
section.on{display:flex}
#pad{flex:1;display:flex;gap:10px;min-height:0}
#touch{flex:1;border-radius:24px;background:rgba(255,255,255,.1);border:1px solid rgba(255,255,255,.16);display:flex;align-items:center;justify-content:center;color:rgba(255,255,255,.5);text-align:center;padding:20px;touch-action:none}
#scroll{width:56px;border-radius:24px;background:rgba(255,255,255,.07);border:1px solid rgba(255,255,255,.12);display:flex;align-items:center;justify-content:center;writing-mode:vertical-rl;color:rgba(255,255,255,.45);font-size:13px;touch-action:none}
.grid{display:grid;grid-template-columns:repeat(3,1fr);gap:10px}
button{border:0;border-radius:20px;padding:16px 8px;font:700 17px system-ui,sans-serif;background:rgba(255,255,255,.14);color:#fff}
button:active{background:#fff;color:#0B0B12}
button.main{background:#fff;color:#0B0B12}
.ok{border-radius:50%;aspect-ratio:1}
textarea,input{width:100%;border:1px solid rgba(255,255,255,.2);border-radius:18px;background:rgba(255,255,255,.12);color:#fff;font:18px system-ui,sans-serif;padding:14px}
textarea{min-height:120px}
textarea:focus,input:focus{outline:none;background:#fff;color:#0B0B12}
.row{display:flex;gap:10px}.row button{flex:1}
nav{display:flex;gap:6px;padding:10px 12px 16px}
nav button{flex:1;padding:12px 4px;font-size:14px;border-radius:999px;background:rgba(255,255,255,.1)}
nav button.on{background:#fff;color:#0B0B12}
#s{min-height:20px;color:rgba(255,255,255,.7);font-size:14px;text-align:center}
</style></head><body>
<header>WiDo</header>
<main>
<section id="tab-pad" class="on"><div id="pad"><div id="touch">${esc(l.touchpadHint)}</div><div id="scroll">${esc(l.scroll)}</div></div></section>
<section id="tab-keys">
<div class="grid">
<button data-k="back">↩</button><button data-k="up">▲</button><button data-k="home">⌂</button>
<button data-k="left">◀</button><button data-k="ok" class="main ok">OK</button><button data-k="right">▶</button>
<button data-k="menu">☰</button><button data-k="down">▼</button><button data-k="mute">🔇</button>
<button data-k="rewind">⏪</button><button data-k="play_pause">⏯</button><button data-k="forward">⏩</button>
<button data-k="volume_down">🔉</button><span></span><button data-k="volume_up">🔊</button>
</div></section>
<section id="tab-text"><textarea id="t" placeholder="${esc(l.placeholder)}"></textarea>
<div class="row"><button id="send">${esc(l.send)}</button><button id="sendEnter" class="main">${esc(l.sendEnter)}</button></div></section>
<section id="tab-link"><input id="u" type="url" inputmode="url" placeholder="${esc(l.linkPlaceholder)}">
<div class="row"><button id="open" class="main">${esc(l.open)}</button></div></section>
</main>
<div id="s"></div>
<nav><button data-tab="pad" class="on">${esc(l.touchpad)}</button><button data-tab="keys">${esc(l.keys)}</button><button data-tab="text">${esc(l.text)}</button><button data-tab="link">${esc(l.link)}</button></nav>
<script>
var T='${esc(token)}',S=document.getElementById('s');
function send(o,ok){return fetch('/cmd?t='+T,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(o)})
.then(function(r){if(!r.ok)throw 0;if(ok)S.textContent=ok;}).catch(function(){S.textContent=${js(l.failed)};});}
document.querySelectorAll('nav button').forEach(function(b){b.onclick=function(){
document.querySelectorAll('nav button,section').forEach(function(e){e.classList.remove('on');});
b.classList.add('on');document.getElementById('tab-'+b.dataset.tab).classList.add('on');S.textContent='';};});
document.querySelectorAll('[data-k]').forEach(function(b){b.onclick=function(){if(navigator.vibrate)navigator.vibrate(10);send({cmd:'key',key:b.dataset.k});};});
var pad=document.getElementById('touch'),last=null,moved=0,acc={x:0,y:0},timer=null,start=0;
function flush(){if(acc.x||acc.y){send({cmd:'move',dx:acc.x*1.6,dy:acc.y*1.6});acc={x:0,y:0};}timer=null;}
pad.addEventListener('pointerdown',function(e){last={x:e.clientX,y:e.clientY};moved=0;start=Date.now();pad.setPointerCapture(e.pointerId);});
pad.addEventListener('pointermove',function(e){if(!last)return;var dx=e.clientX-last.x,dy=e.clientY-last.y;last={x:e.clientX,y:e.clientY};
moved+=Math.abs(dx)+Math.abs(dy);acc.x+=dx;acc.y+=dy;if(!timer)timer=setTimeout(flush,30);});
pad.addEventListener('pointerup',function(){flush();if(moved<8&&Date.now()-start<350)send({cmd:'tap'});last=null;});
var sc=document.getElementById('scroll'),sl=null;
sc.addEventListener('pointerdown',function(e){sl=e.clientY;sc.setPointerCapture(e.pointerId);});
sc.addEventListener('pointermove',function(e){if(sl===null)return;var d=e.clientY-sl;if(Math.abs(d)>12){send({cmd:'scroll',dy:-d*4});sl=e.clientY;}});
sc.addEventListener('pointerup',function(){sl=null;});
function typeText(submit){var t=document.getElementById('t');send({cmd:'type',text:t.value,submit:submit},${js(l.sent)}).then(function(){t.value='';});}
document.getElementById('send').onclick=function(){typeText(false);};
document.getElementById('sendEnter').onclick=function(){typeText(true);};
document.getElementById('open').onclick=function(){var u=document.getElementById('u');send({cmd:'open',url:u.value},${js(l.sent)}).then(function(){u.value='';});};
</script></body></html>"""
    }
}

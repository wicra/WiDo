package dev.wido.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URLDecoder

/**
 * Protocole de la saisie depuis un téléphone : la TV sert une petite page sur le réseau local, le
 * téléphone y tape un texte et l'envoie. Chaque session a un jeton aléatoire, présent dans l'adresse
 * du QR code : sans lui, la TV refuse tout.
 */
object PhoneProtocol {
    const val MAX_TEXT = 2000
    const val MAX_BODY = 8 * 1024

    data class Request(val method: String, val path: String, val query: Map<String, String>)

    @Serializable
    data class Message(val text: String = "", val submit: Boolean = false)

    /** Libellés de la page, dans la langue de la TV. */
    data class Labels(
        val placeholder: String,
        val send: String,
        val sendEnter: String,
        val sent: String,
        val failed: String,
    )

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** `GET /send?t=abc HTTP/1.1` → requête, ou null si la ligne est invalide. */
    fun parseRequestLine(line: String): Request? {
        val parts = line.trim().split(' ')
        if (parts.size != 3 || !parts[2].startsWith("HTTP/")) return null
        val target = parts[1]
        if (!target.startsWith("/")) return null
        val path = target.substringBefore('?')
        val query = target.substringAfter('?', "").split('&').filter { it.isNotEmpty() }.associate { pair ->
            val key = pair.substringBefore('=')
            val value = pair.substringAfter('=', "")
            decode(key) to decode(value)
        }
        return Request(parts[0].uppercase(), path, query)
    }

    /** Message envoyé par le téléphone ; null s'il est illisible, vide ou trop long. */
    fun parseMessage(body: String): Message? {
        val message = runCatching { json.decodeFromString<Message>(body) }.getOrNull() ?: return null
        val text = message.text.replace("\r", "").trimEnd('\n')
        if (text.isEmpty() && !message.submit) return null
        if (text.length > MAX_TEXT) return null
        return message.copy(text = text)
    }

    /** Compare deux jetons en temps constant. */
    fun tokenMatches(expected: String, given: String?): Boolean {
        if (given == null || given.length != expected.length) return false
        var diff = 0
        for (i in expected.indices) diff = diff or (expected[i].code xor given[i].code)
        return diff == 0
    }

    /** La page du téléphone : un champ, deux boutons, au style de l'app. */
    fun page(token: String, labels: Labels): String {
        fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
        fun js(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("<", "\\u003c") + "\""
        return """<!doctype html><html lang="fr"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1"><title>WiDo</title>
<style>
body{margin:0;min-height:100vh;background:radial-gradient(ellipse 80% 60% at 20% 0%,#1E5BFF,transparent 70%),radial-gradient(ellipse 70% 60% at 100% 100%,#7A2BE0,transparent 70%),#0B0B12;color:#fff;font:16px system-ui,sans-serif;display:flex;flex-direction:column;gap:16px;padding:28px 20px;box-sizing:border-box}
h1{margin:0;font-size:28px;font-weight:800;letter-spacing:-.5px}
textarea{width:100%;min-height:120px;box-sizing:border-box;border:1px solid rgba(255,255,255,.2);border-radius:16px;background:rgba(255,255,255,.12);color:#fff;font:18px system-ui,sans-serif;padding:14px}
textarea:focus{outline:none;background:#fff;color:#0B0B12}
.row{display:flex;gap:10px}
button{flex:1;border:0;border-radius:999px;padding:14px;font:700 16px system-ui,sans-serif;background:rgba(255,255,255,.22);color:#fff}
button.main{background:#fff;color:#0B0B12}
#s{min-height:20px;color:rgba(255,255,255,.7);font-size:14px}
</style></head><body>
<h1>WiDo</h1>
<textarea id="t" placeholder="${esc(labels.placeholder)}" autofocus></textarea>
<div class="row"><button id="a">${esc(labels.send)}</button><button id="b" class="main">${esc(labels.sendEnter)}</button></div>
<div id="s"></div>
<script>
function go(submit){var t=document.getElementById('t'),s=document.getElementById('s');
fetch('/send?t=${esc(token)}',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({text:t.value,submit:submit})})
.then(function(r){if(!r.ok)throw 0;s.textContent=${js(labels.sent)};t.value='';})
.catch(function(){s.textContent=${js(labels.failed)};});}
document.getElementById('a').onclick=function(){go(false)};
document.getElementById('b').onclick=function(){go(true)};
</script></body></html>"""
    }

    private fun decode(s: String): String = runCatching { URLDecoder.decode(s, "UTF-8") }.getOrDefault(s)
}

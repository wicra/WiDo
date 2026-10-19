package dev.wido.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReleaseTest {
    private val hash = "a".repeat(64)

    private fun release(body: String = "", digest: String? = null, url: String = "https://github.com/o/r/releases/download/v1.2.0/wido.apk", draft: Boolean = false): String {
        val d = digest?.let { ""","digest":"$it"""" } ?: ""
        return """{"tag_name":"v1.2.0","draft":$draft,"body":"$body","assets":[
          {"name":"notes.txt","browser_download_url":"https://github.com/o/r/releases/download/v1.2.0/notes.txt"},
          {"name":"wido.apk","size":1234,"browser_download_url":"$url"$d}]}"""
    }

    @Test fun parsesDigestFromAsset() {
        val r = assertNotNull(ReleaseParser.parse(release(digest = "sha256:$hash")))
        assertEquals(hash, r.sha256)
        assertEquals(Version.parse("1.2.0"), r.version)
        assertEquals(1234L, r.apkSize)
    }

    @Test fun fallsBackToHashInNotes() {
        val r = assertNotNull(ReleaseParser.parse(release(body = "Notes\\nsha256: ${hash.uppercase()}")))
        assertEquals(hash, r.sha256)
    }

    @Test fun missingHashIsReportedAsNull() {
        assertNull(assertNotNull(ReleaseParser.parse(release())).sha256)
    }

    @Test fun rejectsForeignDownloadHostAndDrafts() {
        assertNull(ReleaseParser.parse(release(url = "https://evil.example/wido.apk")))
        assertNull(ReleaseParser.parse(release(url = "http://github.com/o/r/wido.apk")))
        assertNull(ReleaseParser.parse(release(draft = true)))
        assertNull(ReleaseParser.parse("{}"))
        assertNull(ReleaseParser.parse("nope"))
    }

    @Test fun versionOrdering() {
        fun v(s: String) = Version.parse(s)!!
        assertTrue(v("1.10.0") > v("1.9.9"))
        assertTrue(v("v2.0") > v("1.99.99"))
        assertTrue(v("1.0.0") > v("1.0.0-beta1"))
        assertTrue(v("1.0.0-beta2") > v("1.0.0-beta1"))
        assertEquals(0, v("1.0").compareTo(v("1.0.0")))
        assertNull(Version.parse("abc"))
    }
}

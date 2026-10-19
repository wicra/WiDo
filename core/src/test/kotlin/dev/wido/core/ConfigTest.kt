package dev.wido.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ConfigTest {
    @Test fun parsesMinimalConfig() {
        val p = ConfigParser.parse("""{"sites":[{"id":"a","name":"A","url":"https://a.example/"}]}""")
        assertEquals(1, p.config.sites.size)
        assertEquals("default", p.config.sites[0].userAgent)
        assertTrue(p.warnings.isEmpty())
    }

    @Test fun invalidSiteIsDroppedNotFatal() {
        val p = ConfigParser.parse(
            """{"sites":[
              {"id":"ok","name":"Ok","url":"https://ok.example/"},
              {"id":"Bad Id","name":"X","url":"https://x.example/"},
              {"id":"http","name":"H","url":"http://h.example/"},
              {"id":"ok","name":"Dup","url":"https://d.example/"}
            ]}""",
        )
        assertEquals(listOf("ok"), p.config.sites.map { it.id })
        assertEquals(3, p.warnings.size)
    }

    @Test fun httpAllowedOnlyWithAllowInsecure() {
        val p = ConfigParser.parse("""{"sites":[{"id":"nas","name":"NAS","url":"http://192.168.1.10:8096/","allowInsecure":true}]}""")
        assertEquals(1, p.config.sites.size)
    }

    @Test fun nonHttpsBlocklistIsDropped() {
        val p = ConfigParser.parse("""{"blocklists":["http://x.example/l.txt","https://x.example/l.txt"]}""")
        assertEquals(listOf("https://x.example/l.txt"), p.config.blocklists)
        assertEquals(1, p.warnings.size)
    }

    @Test fun unknownKeysAreIgnored() {
        ConfigParser.parse("""{"futur":true,"sites":[]}""")
    }

    @Test fun tooNewSchemaFails() {
        assertFailsWith<ConfigException> { ConfigParser.parse("""{"schema":99}""") }
    }

    @Test fun garbageFails() {
        assertFailsWith<ConfigException> { ConfigParser.parse("pas du json") }
    }

    @Test fun iconMustBeHttps() {
        val p = ConfigParser.parse(
            """{"sites":[
              {"id":"a","name":"A","url":"https://a.example/","icon":"https://a.example/i.png"},
              {"id":"b","name":"B","url":"https://b.example/","icon":"http://b.example/i.png"}
            ]}""",
        )
        assertEquals("https://a.example/i.png", p.config.sites[0].icon)
        assertEquals(null, p.config.sites[1].icon)
        assertEquals(1, p.warnings.size)
    }
}

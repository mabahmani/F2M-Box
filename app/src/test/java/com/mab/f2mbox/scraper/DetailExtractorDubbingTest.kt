package com.mab.f2mbox.scraper

import com.mab.f2mbox.data.remote.Dub
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Dubbing extraction against real page shapes: one `<p>` holding every dub
 * separated by `...` lines, and the legacy one-`<p>`-per-dub layout.
 */
class DetailExtractorDubbingTest {

    // Real markup: all dubs in a single <p>, cut by "..." separator lines.
    private val singleParagraphHtml = """
        <div class="dubbed-info"><p>دوبله فارسی سریال سرزمین اوباش<br> عوامل دوبله کوالیما (قرار گرفته روی سریال – پیشفرض - )<br> مدیر دوبلاژ : مهدی امینی<br> مترجم : پریسا مرتضوی<br> صدابردار : شادی کیا<br> صداپردازان : محمد مطمئن ‌زاده ، حسین مطمئن ‌زاده<br> گویندگان : غلامرضا صادقی (تام هاردی / هری د سوزا) ، سعید مظفری (پیرس برازنان / کنراد هریگان)<br> دوبله شده در استدیو کوالیما<br> ...<br> عوامل دوبله رنگین کمان سخن (قرار گرفته روی سریال – دوبله سوم)<br> مدیر دوبلاژ : مجید صیادی<br> صدابردار : علی مطلق<br> گوینده عنوان : سعید داننده<br> گویندگان : کسری کیانی (تام هاردی / هری د سوزا) ، کتایون اعظمی (هلن میرن / میو هریگان)<br> دوبله شده در استودیو رنگین‌ کمان سخن<br> پخش از گپ فیلم<br> ...<br> عوامل دوبله ساندفیلم (قرار گرفته روی سریال – دوبله سوم)<br> مدیر دوبلاژ : مریم رادپور<br> مترجم : مریم صرافها<br> گویندگان : شروین قطعه‌ ای (تام هاردی / هری د سوزا) ، همت مومیوند (پیرس برازنان / کنراد هریگان)<br> دوبله شده در استودیو قرن ۲۱<br> پخش از فیلیمو</p></div>
    """.trimIndent()

    @Test
    fun `splits one paragraph into three dubs at the separator lines`() {
        val doc = Jsoup.parse(singleParagraphHtml)

        val dubbing = DetailExtractor.dubbing(doc.body())

        assertEquals(3, dubbing?.dubs?.size)
        assertEquals("دوبله فارسی سریال سرزمین اوباش", dubbing?.dubs?.get(0)?.title)
        assertTrue(dubbing?.dubs?.get(1)?.title?.startsWith("عوامل دوبله رنگین کمان سخن") == true)
        assertTrue(dubbing?.dubs?.get(2)?.title?.startsWith("عوامل دوبله ساندفیلم") == true)
    }

    @Test
    fun `keeps every dub's credits in its own fields`() {
        val doc = Jsoup.parse(singleParagraphHtml)
        val dubs = DetailExtractor.dubbing(doc.body())?.dubs.orEmpty()

        assertEquals("مهدی امینی", dubs[0].fields["مدیر دوبلاژ"])
        assertEquals("پریسا مرتضوی", dubs[0].fields["مترجم"])
        assertEquals("مجید صیادی", dubs[1].fields["مدیر دوبلاژ"])
        assertEquals("مریم رادپور", dubs[2].fields["مدیر دوبلاژ"])
        assertEquals("مریم صرافها", dubs[2].fields["مترجم"])
    }

    @Test
    fun `keeps every dub's own voice actors`() {
        val doc = Jsoup.parse(singleParagraphHtml)
        val dubs = DetailExtractor.dubbing(doc.body())?.dubs.orEmpty()

        assertEquals("غلامرضا صادقی", dubs[0].voiceActors.first().name)
        assertEquals(listOf("تام هاردی", "هری د سوزا"), dubs[0].voiceActors.first().roles)
        assertEquals("کسری کیانی", dubs[1].voiceActors.first().name)
        assertTrue(dubs[2].voiceActors.first().name.startsWith("شروین"))
    }

    @Test
    fun `keeps trailing lines as notes`() {
        val doc = Jsoup.parse(singleParagraphHtml)
        val dubs = DetailExtractor.dubbing(doc.body())?.dubs.orEmpty()

        assertTrue(dubs[0].notes.contains("دوبله شده در استدیو کوالیما"))
        assertTrue(dubs[1].notes.contains("پخش از گپ فیلم"))
        assertTrue(dubs[2].notes.contains("پخش از فیلیمو"))
    }

    @Test
    fun `treats the title narrator as a credit not as the cast`() {
        val doc = Jsoup.parse(singleParagraphHtml)
        val dubs = DetailExtractor.dubbing(doc.body())?.dubs.orEmpty()

        assertEquals("سعید داننده", dubs[1].fields["گوینده عنوان"])
        assertFalse(dubs[1].voiceActors.any { it.name == "سعید داننده" && it.roles.isEmpty() })
    }

    @Test
    fun `still handles the legacy one paragraph per dub layout`() {
        val doc = Jsoup.parse(
            """
            <div id="dubbed-info">
              <p>دوبله اول<br> مدیر دوبلاژ : کسی یک<br> گویندگان : بازیگر اول (نقش یک)</p>
              <p>دوبله دوم<br> مدیر دوبلاژ : کسی دو<br> گویندگان : بازیگر دوم (نقش دو)</p>
            </div>
            """.trimIndent()
        )

        val dubs = DetailExtractor.dubbing(doc.body())?.dubs.orEmpty()

        assertEquals(2, dubs.size)
        assertEquals("دوبله اول", dubs[0].title)
        assertEquals("کسی یک", dubs[0].fields["مدیر دوبلاژ"])
        assertEquals("بازیگر دوم", dubs[1].voiceActors.first().name)
    }
}

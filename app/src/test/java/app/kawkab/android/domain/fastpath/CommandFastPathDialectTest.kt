package app.kawkab.android.domain.fastpath

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Sudanese, Gulf and Egyptian phrasings from the brain lab exams, and lines the fast path must leave
 * to the model because a contact, a body or the target is missing.
 */
class CommandFastPathDialectTest {
    private fun call(text: String) =
        CommandFastPath.parse(text)?.takeIf { it.tool == "call_phone" }?.args?.get("contact")

    @Test
    fun `dialect call phrasings place a call to the named person`() {
        mapOf(
            "ضرب لي أحمد هسي" to "أحمد", "ضرب لي عمر" to "عمر", "أديني عمر في التلفون" to "عمر",
            "أديني my sister في التلفون" to "my sister", "دق لأمي" to "أمي", "دق لي على أحمد" to "أحمد",
            "وصلني بسارة" to "سارة", "اضرب لي علي هسع" to "علي", "عايز أكلم mum" to "mum",
            "داير أكلم أمي" to "أمي", "عايز اتكلم مع اختي" to "اختي", "ابغى اكلم عمر" to "عمر",
            "عاوز أتصل بأخويا" to "أخويا", "خليني أكلم سارة هسه" to "سارة", "ممكن تتصل لي بعمر" to "عمر",
            "give Omar a ring" to "Omar", "please connect me to my sister" to "my sister", "kalim Omar" to "Omar",
            "Kawkab, call Omar" to "Omar", "phone Abdalla now" to "Abdalla",
        ).forEach { (said, who) -> assertEquals(said, who, call(said)) }
    }

    @Test
    fun `pronouns, placeholders and doubt are left to the model`() {
        listOf(
            "call her", "call him back", "اتصل بيهو", "قول لي نكتة", "قول لي حاجة تفرحني", "ابعت مسج لأحمد",
            "ابعت رسالة لعمر", "كلم ود عبدالله ولا عبدالله التاني، ما متأكد",
        ).forEach { assertNull(it, CommandFastPath.parse(it)) }
    }

    @Test
    fun `screenshots, the torch and Bluetooth are not apps`() {
        listOf(
            "افتح آخر سكرين", "open the latest screenshot", "شغل الفلاش", "شغل البلوتوث",
            "افتح لي آخر صورة شاشة صورتها",
        )
            .forEach { assertNull(it, CommandFastPath.parse(it)) }
    }

    @Test
    fun `dialect alarms read half, quarter and day-part words`() {
        mapOf(
            "صحيني الساعة ستة ونص" to ("6" to "30"), "منبه تسعة وربع" to ("9" to "15"),
            "صحيني خمسة إلا ربع" to ("4" to "45"), "منبه عشرة بالليل" to ("22" to "0"),
            "صحيني الساعة ستة الصباح" to ("6" to "0"), "حط منبه سبعة ونص" to ("7" to "30"),
            "حط لي منبه الساعة تمانية" to ("8" to "0"), "wake me up at 4:45" to ("4" to "45"),
            "alarm 7:30" to ("7" to "30"), "set alarm for 9:15" to ("9" to "15"),
        ).forEach { (said, time) ->
            val planned = CommandFastPath.parse(said)
            assertEquals(said, "set_alarm", planned?.tool)
            assertEquals(said, time, planned?.args?.get("hour") to planned?.args?.get("minute"))
        }
    }

    @Test
    fun `dialect and colon texts carry the body`() {
        mapOf(
            "message Omar: running late" to ("Omar" to "running late"),
            "send my sister: happy birthday" to ("my sister" to "happy birthday"),
            "send Omar a message: call me when free" to ("Omar" to "call me when free"),
            "ارسل لأمي إني بخير" to ("أمي" to "إني بخير"),
        ).forEach { (said, expected) ->
            val planned = CommandFastPath.parse(said)
            assertEquals(said, "send_sms", planned?.tool)
            assertEquals(said, expected, planned?.args?.get("contact") to planned?.args?.get("body"))
        }
    }
}

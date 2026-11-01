package app.kawkab.android.domain.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Task 25c: GitHub's release list becomes the newest build with an APK. */
@RunWith(RobolectricTestRunner::class)
class LatestBuildTest {
    @Test
    fun `the first release with an apk wins`() {
        val json = """
            [
              {"tag_name": "build-20261102-aaaaaaa", "assets": []},
              {"tag_name": "build-20261101-bbbbbbb", "assets": [
                {"name": "notes.txt", "browser_download_url": "https://x/notes.txt"},
                {"name": "kawkab-foss.apk", "browser_download_url": "https://x/kawkab-foss.apk"}
              ]}
            ]
        """.trimIndent()
        assertEquals(LatestBuild("build-20261101-bbbbbbb", "https://x/kawkab-foss.apk"), LatestBuild.parse(json))
    }

    @Test
    fun `no apk or bad json gives nothing`() {
        assertNull(LatestBuild.parse("[]"))
        assertNull(LatestBuild.parse("""{"message": "rate limited"}"""))
        assertNull(LatestBuild.parse("not json"))
    }

    @Test
    fun `the installed commit is not offered again`() {
        val build = LatestBuild("build-20261101-abc1234", "https://x/a.apk")
        assertFalse(build.isNewerThan("abc1234"))
        assertFalse(build.isNewerThan("abc1234def5678"))
        assertTrue(build.isNewerThan("fff0000"))
        assertTrue(build.isNewerThan(""))
    }
}

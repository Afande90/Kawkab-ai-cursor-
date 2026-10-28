package app.kawkab.android.domain.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rule that decides whether a downloaded model file may be installed, and the comparison that
 * later tells a checked model from an unchecked one.
 */
class DownloadedModelCheckTest {

    private val hash = ModelFileHash(sha256 = SHA, fileSizeBytes = 7L, fileModifiedAtMs = 1_000L)

    @Test
    fun `given the published hash equals the file's when checked then it is verified`() {
        assertEquals(DownloadedModelCheck.Verified(hash), DownloadedModelCheck.of(SHA, hash))
    }

    @Test
    fun `given the published hash in upper case when it equals the file's then it is verified`() {
        assertEquals(DownloadedModelCheck.Verified(hash), DownloadedModelCheck.of(SHA.uppercase(), hash))
    }

    @Test
    fun `given the published hash differs from the file's when checked then it is a mismatch`() {
        assertEquals(
            DownloadedModelCheck.Mismatch(publishedSha256 = OTHER, actualSha256 = SHA),
            DownloadedModelCheck.of(OTHER.uppercase(), hash),
        )
    }

    @Test
    fun `given no published hash when checked then the file is unverified with its hash`() {
        assertEquals(DownloadedModelCheck.Unverified(hash), DownloadedModelCheck.of(null, hash))
    }

    @Test
    fun `given no published hash and no file hash when checked then the file is unverified without one`() {
        assertEquals(DownloadedModelCheck.Unverified(null), DownloadedModelCheck.of(null, null))
    }

    @Test
    fun `given a published hash and no file hash when checked then the file is unreadable, not unverified`() {
        // Installing it as "unverified" would skip the check the source made possible.
        assertEquals(DownloadedModelCheck.Unreadable, DownloadedModelCheck.of(SHA, null))
    }

    @Test
    fun `given a model checked at download when its file still has that hash then it matches`() {
        assertTrue(model(sourceSha256 = SHA).matchesPublishedHash(SHA))
        assertTrue(model(sourceSha256 = SHA).matchesPublishedHash(SHA.uppercase()))
    }

    @Test
    fun `given a model checked at download when its file changed since then it does not match`() {
        assertFalse(model(sourceSha256 = SHA).matchesPublishedHash(OTHER))
    }

    @Test
    fun `given a model checked at download when its current hash is unknown then it does not match`() {
        assertFalse(model(sourceSha256 = SHA).matchesPublishedHash(null))
    }

    @Test
    fun `given a model never checked when its file has a hash then it does not match`() {
        assertFalse(model(sourceSha256 = null).matchesPublishedHash(SHA))
    }

    private fun model(sourceSha256: String?) =
        LocalModel(name = "m.litertlm", path = "/m.litertlm", size = 7L, isActive = false, sourceSha256 = sourceSha256)

    private companion object {
        const val SHA = "181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c"
        const val OTHER = "0b2a8980ce155fd97673d8e820b4d29d9c7d99b8fa6806f425d969b145bd52e0"
    }
}

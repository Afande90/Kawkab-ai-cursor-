package app.kawkab.android.domain.models

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the RAM line that separates a flagship phone from a smaller one.
 */
class DefaultBrainTest {

    @Test
    fun `given 16 GiB when classified then the phone is flagship`() {
        assertTrue(DefaultBrain.isFlagship(DefaultBrain.FLAGSHIP_RAM_BYTES))
    }

    @Test
    fun `given one byte under 16 GiB when classified then the phone is not flagship`() {
        assertFalse(DefaultBrain.isFlagship(DefaultBrain.FLAGSHIP_RAM_BYTES - 1L))
    }

    @Test
    fun `given 12 GiB when classified then the phone is not flagship`() {
        assertFalse(DefaultBrain.isFlagship(12L * DefaultBrain.BYTES_PER_GIB))
    }
}

package app.kawkab.android.presentation.ui.pocket

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Only plain model file names are accepted from the picker. */
class ModelFileImportTest {
    @Test fun acceptsBothModelFormats() {
        assertTrue(ModelFileImport.accepts("qwen35-0.8b-edited-Q4_K_M.gguf"))
        assertTrue(ModelFileImport.accepts("gemma-4-E2B-it.LITERTLM"))
    }

    @Test fun refusesOtherFilesAndPaths() {
        assertFalse(ModelFileImport.accepts("photo.jpg"))
        assertFalse(ModelFileImport.accepts(""))
        assertFalse(ModelFileImport.accepts("../evil.gguf"))
    }
}

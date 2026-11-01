package app.kawkab.android.presentation.ui.phoneaccess

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import app.kawkab.android.R
import app.kawkab.android.domain.phoneaccess.PhoneAccessStatus

/**
 * "Kawkab needs N permissions" at the top of the chat while anything is off (task 23). A tap
 * opens Phone access. The count is read again whenever the owner comes back.
 *
 * @param onOpen Opens Phone access.
 */
@Composable
fun PhoneAccessBanner(onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val off = remember(lifecycleState) { PhoneAccessStatus.offCount(AndroidPhoneAccess.lines(context)) }
    if (off == 0) return
    Text(
        text = pluralStringResource(R.plurals.phone_access_banner, off, off),
        color = Color(NEEDS_YOU_TEXT),
        style = MaterialTheme.typography.bodyMedium,
        modifier = modifier
            .fillMaxWidth()
            .background(Color(NEEDS_YOU))
            .clickable(onClick = onOpen)
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .testTag(PHONE_ACCESS_BANNER_TAG),
    )
}

/** Amber, the Kawkab colour for "needs you". */
private const val NEEDS_YOU: Long = 0xFFF2B45A

private const val NEEDS_YOU_TEXT: Long = 0xFF070A14

/** Test tag of the banner. */
const val PHONE_ACCESS_BANNER_TAG: String = "phone_access_banner"

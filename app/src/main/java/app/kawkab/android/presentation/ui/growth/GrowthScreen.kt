package app.kawkab.android.presentation.ui.growth

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import app.kawkab.android.R

/**
 * Lists successful plans and lets the owner delete one. Replay does not call a model.
 */
@Composable
fun GrowthScreen(onBack: () -> Unit, viewModel: GrowthViewModel = hiltViewModel()) {
    val skills by viewModel.skills.collectAsState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(all = 16.dp),
    ) {
        TextButton(onClick = onBack) { Text(stringResource(R.string.growth_back)) }
        Text(stringResource(R.string.growth_title))
        Text(stringResource(R.string.growth_body))
        if (skills.isEmpty()) {
            Text(stringResource(R.string.growth_empty))
        }
        skills.forEach { skill ->
            Text(skill.key)
            Text(pluralStringResource(R.plurals.growth_count, skill.successes, skill.successes))
            TextButton(onClick = { viewModel.delete(skill.key) }) {
                Text(stringResource(R.string.growth_delete))
            }
        }
    }
}

package com.am24.imbrel.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.am24.imbrel.R
import com.am24.imbrel.presentation.CitySearchUiState
import com.am24.imbrel.presentation.MainViewModel

@Composable
fun CitySearchDialog(
    viewModel: MainViewModel,
    onDismiss: () -> Unit,
    onSearchStarted: () -> Unit,
) {
    val state by viewModel.citySearchState.collectAsStateWithLifecycle()
    CitySearchDialogContent(
        state = state,
        onQueryChange = viewModel::updateCitySearchQuery,
        onSearch = {
            if (viewModel.submitCitySearch(closeOnSubmit = true)) onSearchStarted()
        },
        onDismiss = onDismiss,
    )
}

@Composable
fun CitySearchDialogContent(
    state: CitySearchUiState,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.city_name)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                CitySearchForm(state, onQueryChange, onSearch)
            }
        },
        confirmButton = {
            val submit = citySearchAction(state, onSearch)
            TextButton(onClick = submit, enabled = !state.isLoading) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun CitySearchForm(
    state: CitySearchUiState,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val submit = citySearchAction(state, onSearch)
    LaunchedEffect(Unit) {
        if (!state.isLoading && state.successId == null) focusRequester.requestFocus()
    }
    OutlinedTextField(
        value = state.query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
        label = { Text(stringResource(R.string.enter_city)) },
        enabled = !state.isLoading,
        singleLine = true,
        isError = state.isQueryBlank || state.error != null,
        keyboardOptions =
            KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Search,
            ),
        keyboardActions = KeyboardActions(onSearch = { submit() }),
    )
    val errorMessage =
        when {
            state.isQueryBlank -> stringResource(R.string.city_query_required)
            state.error != null -> stringResource(state.error.messageResource())
            else -> null
        }
    errorMessage?.let { message ->
        Text(
            text = message,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

@Composable
private fun citySearchAction(
    state: CitySearchUiState,
    onSearch: () -> Unit,
): () -> Unit {
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    return {
        if (!state.isLoading) {
            if (state.query.isNotBlank()) {
                keyboard?.hide()
                focusManager.clearFocus()
            }
            onSearch()
        }
    }
}

package com.sahyog.feature.worker.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sahyog.core.designsystem.component.SahyogButton
import com.sahyog.core.designsystem.component.SahyogCard
import com.sahyog.core.designsystem.component.SahyogFilterChip
import com.sahyog.core.designsystem.component.SahyogLoadingView
import com.sahyog.core.designsystem.component.SahyogOutlinedButton
import com.sahyog.core.designsystem.component.SahyogSegmentedControl
import com.sahyog.core.designsystem.component.SahyogTextButton
import com.sahyog.core.designsystem.component.SahyogTextField
import com.sahyog.core.designsystem.component.SahyogTopAppBar
import com.sahyog.core.designsystem.icon.SahyogIcons
import com.sahyog.core.designsystem.theme.SahyogElevation
import com.sahyog.core.designsystem.theme.SahyogSpacing
import com.sahyog.core.designsystem.theme.SahyogTheme
import com.sahyog.core.model.PriceUnit
import com.sahyog.core.model.WorkerProfileInput
import com.sahyog.core.ui.ObserveAsEvents
import com.sahyog.core.ui.catalog.categoryLabel
import com.sahyog.core.ui.location.openAppSettings
import com.sahyog.core.ui.location.rememberLocationPermissionRequester
import com.sahyog.core.ui.messageRes
import com.sahyog.feature.worker.R
import kotlinx.coroutines.launch

@Composable
fun WorkerProfileFormScreen(
    onNavigateBack: () -> Unit,
    onSaved: (needsIdDocument: Boolean) -> Unit,
    viewModel: WorkerProfileFormViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val resources = LocalResources.current
    ObserveAsEvents(viewModel.eventFlow) { event ->
        when (event) {
            is WorkerProfileFormEvent.Saved -> onSaved(event.needsIdDocument)
            is WorkerProfileFormEvent.Failed -> scope.launch {
                snackbarHostState.showSnackbar(resources.getString(event.error.messageRes()))
            }
        }
    }
    Scaffold(
        topBar = {
            SahyogTopAppBar(
                title = stringResource(
                    if (uiState.isEditing) R.string.worker_form_edit_title else R.string.worker_form_setup_title,
                ),
                onNavigateBack = onNavigateBack,
            )
        },
        bottomBar = {
            if (!uiState.isLoading) {
                Surface(color = SahyogTheme.colors.surfaceContainer, tonalElevation = SahyogElevation.medium) {
                    SahyogButton(
                        text = stringResource(
                            if (uiState.isEditing) R.string.worker_form_save_edit else R.string.worker_form_save,
                        ),
                        onClick = viewModel::onSave,
                        loading = uiState.isSaving,
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .imePadding()
                            .padding(horizontal = SahyogSpacing.screenGutter, vertical = SahyogSpacing.md),
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = SahyogTheme.colors.surface,
    ) { padding ->
        val modifier = Modifier
            .fillMaxSize()
            .padding(padding)
        if (uiState.isLoading) {
            SahyogLoadingView(modifier)
        } else {
            ProfileForm(uiState, viewModel, modifier)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileForm(state: WorkerProfileFormUiState, viewModel: WorkerProfileFormViewModel, modifier: Modifier) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(
            start = SahyogSpacing.screenGutter,
            end = SahyogSpacing.screenGutter,
            top = SahyogSpacing.sm,
            bottom = SahyogSpacing.xxl,
        ),
        verticalArrangement = Arrangement.spacedBy(SahyogSpacing.lg),
    ) {
        item(key = "categories") {
            Section(
                title = stringResource(R.string.worker_form_categories),
                error = stringResource(R.string.worker_form_categories_error).takeIf { state.showErrors && !state.categoriesValid },
            ) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(SahyogSpacing.sm)) {
                    state.categories.forEach { category ->
                        SahyogFilterChip(
                            label = categoryLabel(category),
                            selected = category.id in state.selectedCategoryIds,
                            onClick = { viewModel.onCategoryToggle(category.id) },
                        )
                    }
                }
            }
        }
        item(key = "services-header") {
            Section(
                title = stringResource(R.string.worker_form_services),
                error = stringResource(R.string.worker_form_service_error).takeIf { state.showErrors && !state.servicesValid },
            ) {
                Text(
                    text = stringResource(R.string.worker_form_services_hint),
                    style = SahyogTheme.typography.bodySmall,
                    color = SahyogTheme.colors.onSurfaceVariant,
                )
            }
        }
        itemsIndexed(state.services) { index, service ->
            ServiceEditor(
                service = service,
                canRemove = state.services.size > 1,
                onChange = { viewModel.onServiceChange(index, it) },
                onRemove = { viewModel.onRemoveService(index) },
            )
        }
        if (state.canAddService) {
            item(key = "add-service") {
                SahyogTextButton(text = stringResource(R.string.worker_form_add_service), onClick = viewModel::onAddService)
            }
        }
        item(key = "locality") {
            SahyogTextField(
                value = state.locality,
                onValueChange = viewModel::onLocalityChange,
                label = stringResource(R.string.worker_form_locality),
                placeholder = stringResource(R.string.worker_form_locality_placeholder),
                errorText = stringResource(R.string.worker_form_locality_error).takeIf { state.showErrors && !state.localityValid },
                leadingIcon = SahyogIcons.Location,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item(key = "work-location") {
            WorkLocationSection(state, viewModel)
        }
        item(key = "years") {
            SahyogTextField(
                value = state.yearsExperience,
                onValueChange = viewModel::onYearsChange,
                label = stringResource(R.string.worker_form_years),
                errorText = stringResource(R.string.worker_form_years_error).takeIf { state.showErrors && !state.yearsValid },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item(key = "languages") {
            Section(
                title = stringResource(R.string.worker_form_languages),
                error = stringResource(R.string.worker_form_languages_error).takeIf { state.showErrors && !state.languagesValid },
            ) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(SahyogSpacing.sm)) {
                    WorkerProfileInput.LANGUAGES.forEach { language ->
                        SahyogFilterChip(
                            label = language,
                            selected = language in state.languages,
                            onClick = { viewModel.onLanguageToggle(language) },
                        )
                    }
                }
            }
        }
        item(key = "about") {
            SahyogTextField(
                value = state.about,
                onValueChange = viewModel::onAboutChange,
                label = stringResource(R.string.worker_form_about),
                placeholder = stringResource(R.string.worker_form_about_placeholder),
                singleLine = false,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ServiceEditor(
    service: ServiceDraft,
    canRemove: Boolean,
    onChange: (ServiceDraft) -> Unit,
    onRemove: () -> Unit,
) {
    SahyogCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SahyogTextField(
                value = service.name,
                onValueChange = { onChange(service.copy(name = it.take(WorkerProfileInput.SERVICE_NAME_LENGTH.last))) },
                label = stringResource(R.string.worker_form_service_name),
                placeholder = stringResource(R.string.worker_form_service_name_placeholder),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.weight(1f),
            )
            if (canRemove) {
                IconButton(onClick = onRemove) {
                    Icon(SahyogIcons.Close, contentDescription = stringResource(R.string.worker_form_remove_service))
                }
            }
        }
        Spacer(Modifier.height(SahyogSpacing.sm))
        SahyogTextField(
            value = service.priceRupees,
            onValueChange = { onChange(service.copy(priceRupees = it)) },
            label = stringResource(R.string.worker_form_service_price),
            prefix = "₹",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(SahyogSpacing.sm))
        SahyogSegmentedControl(
            options = PriceUnit.entries,
            selected = service.unit,
            onSelect = { onChange(service.copy(unit = it)) },
            label = { unit ->
                stringResource(
                    when (unit) {
                        PriceUnit.PerVisit -> R.string.worker_form_unit_visit
                        PriceUnit.PerHour -> R.string.worker_form_unit_hour
                        PriceUnit.PerJob -> R.string.worker_form_unit_job
                    },
                )
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun Section(title: String, error: String?, content: @Composable () -> Unit) {
    Column {
        Text(
            text = title,
            style = SahyogTheme.typography.titleMedium,
            modifier = Modifier
                .padding(bottom = SahyogSpacing.sm)
                .semantics { heading() },
        )
        content()
        if (error != null) {
            Spacer(Modifier.height(SahyogSpacing.xs))
            Text(error, style = SahyogTheme.typography.bodySmall, color = SahyogTheme.colors.error)
        }
    }
}

@Composable
private fun WorkLocationSection(state: WorkerProfileFormUiState, viewModel: WorkerProfileFormViewModel) {
    val context = LocalContext.current
    val requestLocation = rememberLocationPermissionRequester(viewModel::onLocationPermissionResult)
    Section(title = stringResource(R.string.worker_form_location), error = null) {
        Text(
            text = stringResource(R.string.worker_form_location_body),
            style = SahyogTheme.typography.bodySmall,
            color = SahyogTheme.colors.onSurfaceVariant,
        )
        Spacer(Modifier.height(SahyogSpacing.sm))
        if (state.location != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(SahyogIcons.Verified, contentDescription = null, tint = SahyogTheme.colors.primary)
                Spacer(Modifier.width(SahyogSpacing.sm))
                Text(
                    text = stringResource(R.string.worker_form_location_set),
                    style = SahyogTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                SahyogTextButton(text = stringResource(R.string.worker_form_location_remove), onClick = viewModel::onClearLocation)
            }
        }
        state.locationProblem?.let { problem ->
            Text(
                text = stringResource(
                    when (problem) {
                        WorkLocationProblem.Denied -> R.string.worker_form_location_denied
                        WorkLocationProblem.PermanentlyDenied -> R.string.worker_form_location_blocked
                        WorkLocationProblem.Off -> R.string.worker_form_location_off
                        WorkLocationProblem.Unavailable -> R.string.worker_form_location_unavailable
                    },
                ),
                style = SahyogTheme.typography.bodySmall,
                color = SahyogTheme.colors.error,
            )
            Spacer(Modifier.height(SahyogSpacing.xs))
        }
        if (state.locationProblem == WorkLocationProblem.PermanentlyDenied) {
            SahyogOutlinedButton(
                text = stringResource(R.string.worker_form_location_settings),
                onClick = context::openAppSettings,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            SahyogOutlinedButton(
                text = stringResource(
                    if (state.location == null) R.string.worker_form_location_use else R.string.worker_form_location_update,
                ),
                onClick = requestLocation,
                loading = state.isLocating,
                leadingIcon = SahyogIcons.Location,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

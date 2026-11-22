package com.sahyog.feature.worker.verification

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sahyog.core.common.result.AppError
import com.sahyog.core.data.worker.WorkerErrorFields
import com.sahyog.core.designsystem.component.SahyogButton
import com.sahyog.core.designsystem.component.SahyogCard
import com.sahyog.core.designsystem.component.SahyogOutlinedButton
import com.sahyog.core.designsystem.component.SahyogTopAppBar
import com.sahyog.core.designsystem.icon.SahyogIcons
import com.sahyog.core.designsystem.theme.SahyogElevation
import com.sahyog.core.designsystem.theme.SahyogSpacing
import com.sahyog.core.designsystem.theme.SahyogTheme
import com.sahyog.core.model.IdDocumentType
import com.sahyog.core.ui.ObserveAsEvents
import com.sahyog.core.ui.messageRes
import com.sahyog.feature.worker.R
import kotlinx.coroutines.launch

@Composable
fun IdVerificationScreen(
    onNavigateBack: () -> Unit,
    onSubmitted: () -> Unit,
    viewModel: IdVerificationViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val resources = LocalResources.current
    ObserveAsEvents(viewModel.eventFlow) { event ->
        when (event) {
            IdVerificationEvent.Submitted -> onSubmitted()
            is IdVerificationEvent.Failed -> scope.launch {
                snackbarHostState.showSnackbar(resources.getString(event.error.idMessageRes()))
            }
        }
    }
    // System Photo Picker: the app sees only the one image the worker picks; no storage permission.
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        viewModel.onPhotoPicked(uri?.toString())
    }

    Scaffold(
        topBar = { SahyogTopAppBar(title = stringResource(R.string.worker_id_title), onNavigateBack = onNavigateBack) },
        bottomBar = {
            Surface(color = SahyogTheme.colors.surfaceContainer, tonalElevation = SahyogElevation.medium) {
                SahyogButton(
                    text = stringResource(R.string.worker_id_submit),
                    onClick = viewModel::onSubmit,
                    enabled = uiState.canSubmit,
                    loading = uiState.isSubmitting,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = SahyogSpacing.screenGutter, vertical = SahyogSpacing.md),
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = SahyogTheme.colors.surface,
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(
                start = SahyogSpacing.screenGutter,
                end = SahyogSpacing.screenGutter,
                top = SahyogSpacing.sm,
                bottom = SahyogSpacing.xxl,
            ),
            verticalArrangement = Arrangement.spacedBy(SahyogSpacing.lg),
        ) {
            item(key = "intro") {
                Text(stringResource(R.string.worker_id_intro), style = SahyogTheme.typography.bodyLarge)
            }
            item(key = "type") {
                Section(stringResource(R.string.worker_id_type)) {
                    SahyogCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectableGroup(),
                        contentPadding = PaddingValues(vertical = SahyogSpacing.xs),
                    ) {
                        IdDocumentType.entries.forEachIndexed { index, type ->
                            if (index > 0) HorizontalDivider(color = SahyogTheme.colors.outlineVariant)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .selectable(
                                        selected = type == uiState.documentType,
                                        onClick = { viewModel.onTypeSelect(type) },
                                        role = Role.RadioButton,
                                    )
                                    .padding(horizontal = SahyogSpacing.sm, vertical = SahyogSpacing.xs),
                            ) {
                                RadioButton(selected = type == uiState.documentType, onClick = null)
                                Spacer(Modifier.width(SahyogSpacing.sm))
                                Text(stringResource(type.labelRes()), style = SahyogTheme.typography.bodyLarge)
                            }
                        }
                    }
                }
            }
            item(key = "photo") {
                Section(stringResource(R.string.worker_id_photo)) {
                    if (uiState.hasPhoto) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(SahyogIcons.Verified, contentDescription = null, tint = SahyogTheme.colors.primary)
                            Spacer(Modifier.width(SahyogSpacing.sm))
                            Text(stringResource(R.string.worker_id_photo_added), style = SahyogTheme.typography.bodyLarge)
                        }
                        Spacer(Modifier.height(SahyogSpacing.sm))
                    }
                    SahyogOutlinedButton(
                        text = stringResource(
                            if (uiState.hasPhoto) R.string.worker_id_change_photo else R.string.worker_id_choose_photo,
                        ),
                        onClick = {
                            pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (uiState.documentType == IdDocumentType.Aadhaar) {
                        Spacer(Modifier.height(SahyogSpacing.sm))
                        Text(
                            text = stringResource(R.string.worker_id_aadhaar_tip),
                            style = SahyogTheme.typography.bodySmall,
                            color = SahyogTheme.colors.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

private fun IdDocumentType.labelRes(): Int = when (this) {
    IdDocumentType.Aadhaar -> R.string.worker_id_aadhaar
    IdDocumentType.VoterId -> R.string.worker_id_voter
    IdDocumentType.DrivingLicence -> R.string.worker_id_driving
    IdDocumentType.Pan -> R.string.worker_id_pan
}

private fun AppError.idMessageRes(): Int = when {
    this is AppError.Validation && field == WorkerErrorFields.DOCUMENT -> R.string.worker_id_error_photo
    this is AppError.Validation && field == WorkerErrorFields.DOCUMENT_TOO_LARGE -> R.string.worker_id_error_too_large
    this is AppError.Validation && field == WorkerErrorFields.NO_PROFILE -> R.string.worker_id_error_profile
    else -> messageRes()
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column {
        Text(
            text = title,
            style = SahyogTheme.typography.titleMedium,
            modifier = Modifier
                .padding(bottom = SahyogSpacing.sm)
                .semantics { heading() },
        )
        content()
    }
}

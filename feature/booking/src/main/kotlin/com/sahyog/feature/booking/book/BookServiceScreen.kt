package com.sahyog.feature.booking.book

import androidx.compose.material3.Icon
import com.sahyog.core.designsystem.icon.SahyogIcons
import com.sahyog.core.designsystem.component.SahyogTextButton
import com.sahyog.core.designsystem.component.SahyogOutlinedButton
import com.sahyog.feature.booking.map.hasMapsKey
import com.sahyog.core.model.GeoPoint
import androidx.compose.ui.platform.LocalContext
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sahyog.core.designsystem.component.SahyogAvatar
import com.sahyog.core.designsystem.component.SahyogButton
import com.sahyog.core.designsystem.component.SahyogCard
import com.sahyog.core.designsystem.component.SahyogEmptyView
import com.sahyog.core.designsystem.component.SahyogFilterChip
import com.sahyog.core.designsystem.component.SahyogLoadingView
import com.sahyog.core.designsystem.component.SahyogTextField
import com.sahyog.core.designsystem.component.SahyogTopAppBar
import com.sahyog.core.designsystem.theme.SahyogElevation
import com.sahyog.core.designsystem.theme.SahyogSpacing
import com.sahyog.core.designsystem.theme.SahyogTheme
import com.sahyog.core.model.TimeSlot
import com.sahyog.core.model.WorkerService
import com.sahyog.core.ui.ObserveAsEvents
import com.sahyog.core.ui.booking.formatBookingDate
import com.sahyog.core.ui.booking.formatSlotTime
import com.sahyog.core.ui.catalog.WorkerRating
import com.sahyog.core.ui.catalog.formatRupees
import com.sahyog.core.ui.catalog.priceUnitSuffix
import com.sahyog.feature.booking.R
import com.sahyog.feature.booking.component.bookingMessageRes
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun BookServiceScreen(
    viewModel: BookServiceViewModel,
    onNavigateBack: () -> Unit,
    onBooked: (bookingId: String) -> Unit,
    onPickOnMap: (current: GeoPoint?) -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val resources = LocalResources.current
    ObserveAsEvents(viewModel.eventFlow) { event ->
        when (event) {
            is BookServiceEvent.Booked -> onBooked(event.bookingId)
            is BookServiceEvent.Failed -> scope.launch {
                snackbarHostState.showSnackbar(resources.getString(event.error.bookingMessageRes()))
            }
        }
    }
    BookServiceContent(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onNavigateBack = onNavigateBack,
        actions = BookServiceActions(
            onServiceSelect = viewModel::onServiceSelect,
            onDateSelect = viewModel::onDateSelect,
            onSlotSelect = viewModel::onSlotSelect,
            onAddressChange = viewModel::onAddressChange,
            onNoteChange = viewModel::onNoteChange,
            onSubmit = viewModel::onSubmit,
            onPickOnMap = if (LocalContext.current.hasMapsKey()) onPickOnMap else null,
            onLocationClear = viewModel::onLocationClear,
        ),
    )
}

internal data class BookServiceActions(
    val onServiceSelect: (WorkerService) -> Unit,
    val onDateSelect: (LocalDate) -> Unit,
    val onSlotSelect: (TimeSlot) -> Unit,
    val onAddressChange: (String) -> Unit,
    val onNoteChange: (String) -> Unit,
    val onSubmit: () -> Unit,
    /** Null when this build has no Maps key: the map option is hidden. */
    val onPickOnMap: ((GeoPoint?) -> Unit)? = null,
    val onLocationClear: () -> Unit = {},
)

@Composable
internal fun BookServiceContent(
    uiState: BookServiceUiState,
    snackbarHostState: SnackbarHostState,
    onNavigateBack: () -> Unit,
    actions: BookServiceActions,
) {
    Scaffold(
        topBar = { SahyogTopAppBar(title = stringResource(R.string.booking_book_title), onNavigateBack = onNavigateBack) },
        bottomBar = {
            if (uiState is BookServiceUiState.Form) SubmitBar(uiState, actions.onSubmit)
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = SahyogTheme.colors.surface,
    ) { padding ->
        val modifier = Modifier
            .fillMaxSize()
            .padding(padding)
        when (uiState) {
            BookServiceUiState.Loading -> SahyogLoadingView(modifier)
            BookServiceUiState.NotFound -> SahyogEmptyView(
                title = stringResource(R.string.booking_error_worker_gone),
                modifier = modifier,
            )
            is BookServiceUiState.Form -> BookingForm(uiState, actions, modifier)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BookingForm(form: BookServiceUiState.Form, actions: BookServiceActions, modifier: Modifier) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(
            start = SahyogSpacing.screenGutter,
            end = SahyogSpacing.screenGutter,
            top = SahyogSpacing.sm,
            bottom = SahyogSpacing.xxl,
        ),
        verticalArrangement = Arrangement.spacedBy(SahyogSpacing.xl),
    ) {
        item(key = "worker") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SahyogAvatar(name = form.worker.name, size = AvatarSize)
                Spacer(Modifier.width(SahyogSpacing.md))
                Column {
                    Text(form.worker.name, style = SahyogTheme.typography.titleMedium)
                    WorkerRating(form.worker)
                }
            }
        }
        item(key = "service") {
            Section(stringResource(R.string.booking_book_service)) {
                SahyogCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectableGroup(),
                    contentPadding = PaddingValues(vertical = SahyogSpacing.xs),
                ) {
                    form.worker.services.forEachIndexed { index, service ->
                        if (index > 0) HorizontalDivider(color = SahyogTheme.colors.outlineVariant)
                        ServiceOption(
                            service = service,
                            selected = service == form.selectedService,
                            onSelect = { actions.onServiceSelect(service) },
                        )
                    }
                }
            }
        }
        item(key = "day") {
            Section(stringResource(R.string.booking_book_day)) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(SahyogSpacing.sm)) {
                    items(form.dates, key = { it.toEpochDay() }) { date ->
                        SahyogFilterChip(
                            label = formatBookingDate(date, form.today),
                            selected = date == form.selectedDate,
                            onClick = { actions.onDateSelect(date) },
                        )
                    }
                }
            }
        }
        item(key = "time") {
            Section(stringResource(R.string.booking_book_time)) {
                if (form.slots.none { it.enabled }) {
                    Text(
                        text = stringResource(R.string.booking_book_time_none_left),
                        style = SahyogTheme.typography.bodyMedium,
                        color = SahyogTheme.colors.onSurfaceVariant,
                    )
                } else {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(SahyogSpacing.sm),
                        verticalArrangement = Arrangement.spacedBy(SahyogSpacing.xs),
                    ) {
                        form.slots.forEach { option ->
                            SahyogFilterChip(
                                label = formatSlotTime(option.slot),
                                selected = option.slot == form.selectedSlot,
                                onClick = { actions.onSlotSelect(option.slot) },
                                enabled = option.enabled,
                            )
                        }
                    }
                }
            }
        }
        item(key = "address") {
            Section(stringResource(R.string.booking_book_address)) {
                actions.onPickOnMap?.let { pick ->
                    MapPinRow(form.location, onPick = { pick(form.location) }, onClear = actions.onLocationClear)
                    Spacer(Modifier.height(SahyogSpacing.md))
                }
                SahyogTextField(
                    value = form.address,
                    onValueChange = actions.onAddressChange,
                    label = stringResource(R.string.booking_book_address_label),
                    supportingText = stringResource(
                        if (form.location != null) R.string.booking_book_address_hint_pinned else R.string.booking_book_address_hint,
                    ),
                    singleLine = false,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(SahyogSpacing.md))
                SahyogTextField(
                    value = form.note,
                    onValueChange = actions.onNoteChange,
                    label = stringResource(R.string.booking_book_note_label),
                    placeholder = stringResource(R.string.booking_book_note_hint),
                    singleLine = false,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun ServiceOption(service: WorkerService, selected: Boolean, onSelect: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton)
            .padding(horizontal = SahyogSpacing.sm, vertical = SahyogSpacing.xs),
    ) {
        // The whole row is the click target; the radio button is decoration for it.
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(SahyogSpacing.sm))
        Text(service.name, style = SahyogTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(
            text = formatRupees(service.startingPrice) + priceUnitSuffix(service.unit),
            style = SahyogTheme.typography.labelLarge,
            color = SahyogTheme.colors.primary,
            modifier = Modifier.padding(start = SahyogSpacing.sm, end = SahyogSpacing.sm),
        )
    }
}

@Composable
private fun SubmitBar(form: BookServiceUiState.Form, onSubmit: () -> Unit) {
    Surface(
        color = SahyogTheme.colors.surfaceContainer,
        tonalElevation = SahyogElevation.medium,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = SahyogSpacing.screenGutter, vertical = SahyogSpacing.md),
        ) {
            form.selectedService?.let { service ->
                Column(Modifier.weight(1f)) {
                    Text(
                        text = formatRupees(service.startingPrice) + priceUnitSuffix(service.unit),
                        style = SahyogTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(R.string.booking_book_price_note),
                        style = SahyogTheme.typography.bodySmall,
                        color = SahyogTheme.colors.onSurfaceVariant,
                    )
                }
            }
            SahyogButton(
                text = stringResource(R.string.booking_book_confirm),
                onClick = onSubmit,
                enabled = form.canSubmit,
                loading = form.isSubmitting,
            )
        }
    }
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

private val AvatarSize = 48.dp

/** "Pick on map", or once a pin is set: "Pinned on map · Change · Remove". */
@Composable
private fun MapPinRow(location: GeoPoint?, onPick: () -> Unit, onClear: () -> Unit) {
    if (location == null) {
        SahyogOutlinedButton(
            text = stringResource(R.string.booking_book_pick_on_map),
            onClick = onPick,
            leadingIcon = SahyogIcons.Location,
            modifier = Modifier.fillMaxWidth(),
        )
    } else {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Icon(SahyogIcons.Location, contentDescription = null, tint = SahyogTheme.colors.primary)
            Spacer(Modifier.width(SahyogSpacing.sm))
            Text(
                text = stringResource(R.string.booking_book_pin_set),
                style = SahyogTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            SahyogTextButton(text = stringResource(R.string.booking_book_pin_change), onClick = onPick)
            SahyogTextButton(text = stringResource(R.string.booking_book_pin_remove), onClick = onClear)
        }
    }
}

package gomeng.dev.stashplayer.feature.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import gomeng.dev.stashplayer.core.debug.StashDebugLogBuffer
import gomeng.dev.stashplayer.core.network.StashGraphQlClient
import gomeng.dev.stashplayer.core.network.StashLoginClient
import gomeng.dev.stashplayer.core.network.StashServerAuthMode
import gomeng.dev.stashplayer.core.network.StashServerProfile
import gomeng.dev.stashplayer.core.network.StashSettingsRepository
import gomeng.dev.stashplayer.core.network.canAttemptStashCredentialTransport
import gomeng.dev.stashplayer.core.security.DeviceAuthenticationAvailability
import gomeng.dev.stashplayer.core.security.shouldShowBiometricOnboardingPrompt
import gomeng.dev.stashplayer.feature.security.deviceAuthenticationAvailabilityDescriptionRes
import gomeng.dev.stashplayer.feature.security.rememberDeviceAuthenticationAvailability
import gomeng.dev.stashplayer.feature.security.rememberDeviceAuthenticationLauncher
import kotlinx.coroutines.launch
import gomeng.dev.stashplayer.R
import gomeng.dev.stashplayer.core.ui.theme.StashBrandGradient
import gomeng.dev.stashplayer.core.ui.designsystem.StashSpace
import gomeng.dev.stashplayer.core.ui.designsystem.StashRadius
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.CardDefaults
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.ui.res.stringResource
import gomeng.dev.stashplayer.core.ui.i18n.stashString

@Composable
fun ServerSetupRoute(
    isFoldLikeLayout: Boolean,
    onContinue: () -> Unit,
) {
    val context = LocalContext.current
    val repository = remember(context) { StashSettingsRepository(context) }
    val savedProfile by repository.savedServerProfile.collectAsState(initial = null)
    val biometricAppLockEnabled by repository.biometricAppLockEnabled.collectAsState(
        initial = StashSettingsRepository.DEFAULT_BIOMETRIC_APP_LOCK_ENABLED,
    )
    val coroutineScope = rememberCoroutineScope()
    val deviceAuthenticationAvailability = rememberDeviceAuthenticationAvailability()

    var serverName by remember { mutableStateOf("Home") }
    var serverUrl by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var authMode by remember { mutableStateOf(SetupAuthMode.ApiKey) }
    var allowInsecureLocalApiKey by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf<String?>(null) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var showBiometricOffer by remember { mutableStateOf(false) }
    var biometricOfferDismissed by remember { mutableStateOf(false) }
    var biometricOfferStatus by remember { mutableStateOf<String?>(null) }
    val scrollState = rememberScrollState()
    val authenticateForBiometricOffer = rememberDeviceAuthenticationLauncher(
        onAuthenticated = {
            coroutineScope.launch {
                repository.setBiometricAppLockEnabled(true)
                onContinue()
            }
        },
        onAuthenticationError = { message ->
            biometricOfferStatus = message.toString()
        },
    )

    fun continueOrOfferBiometricAppLock() {
        val shouldOffer = !biometricOfferDismissed && shouldShowBiometricOnboardingPrompt(
            connectionSaved = true,
            appLockEnabled = biometricAppLockEnabled,
            deviceAuthenticationAvailability = deviceAuthenticationAvailability,
        )
        if (shouldOffer) {
            showBiometricOffer = true
        } else {
            onContinue()
        }
    }

    LaunchedEffect(savedProfile) {
        savedProfile?.let {
            serverName = it.name
            serverUrl = it.baseUrl
            apiKey = it.apiKey
            username = it.username
            password = it.password
            allowInsecureLocalApiKey = it.allowInsecureLocalApiKey
            authMode = when (it.authMode) {
                StashServerAuthMode.None -> SetupAuthMode.LinkOnly
                StashServerAuthMode.ApiKey -> SetupAuthMode.ApiKey
                StashServerAuthMode.SessionCookie -> SetupAuthMode.Password
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .then(if (shouldUseScrollableSetupContent(authMode)) Modifier.verticalScroll(scrollState) else Modifier)
            .imePadding()
            .statusBarsPadding()
            .padding(horizontal = if (isFoldLikeLayout) StashSpace.XXL else StashSpace.L, vertical = StashSpace.XL),
        verticalArrangement = Arrangement.spacedBy(StashSpace.L),
    ) {
        // Welcome: the mark in a brand ring, a name and one line on what happens next.
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = StashSpace.L, bottom = StashSpace.S),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(StashSpace.M),
        ) {
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .background(StashBrandGradient, CircleShape)
                    .padding(3.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Image(painter = painterResource(R.drawable.ic_stashy_mark), contentDescription = null, modifier = Modifier.size(44.dp))
            }
            Text(stashString(R.string.setup_welcome_title), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
            Text(
                stashString(R.string.setup_welcome_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(StashRadius.M),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = serverName,
                    onValueChange = { serverName = it },
                    label = { Text(stashString(R.string.auto_kr_0530)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = { serverUrl = it },
                    label = { Text(stashString(R.string.auto_kr_0531)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                SetupAuthModeRow(
                    selected = authMode == SetupAuthMode.LinkOnly,
                    label = R.string.setup_auth_mode_link_only_label,
                    description = R.string.setup_auth_mode_link_only_description,
                    onClick = { authMode = SetupAuthMode.LinkOnly },
                )
                SetupAuthModeRow(
                    selected = authMode == SetupAuthMode.ApiKey,
                    label = R.string.setup_auth_mode_api_key_label,
                    description = R.string.setup_auth_mode_api_key_description,
                    onClick = { authMode = SetupAuthMode.ApiKey },
                )
                SetupAuthModeRow(
                    selected = authMode == SetupAuthMode.Password,
                    label = R.string.setup_auth_mode_password_label,
                    description = R.string.setup_auth_mode_password_description,
                    onClick = { authMode = SetupAuthMode.Password },
                )
                if (authMode == SetupAuthMode.ApiKey) {
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        label = { Text(stashString(R.string.settings_api_key_label)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                    )
                } else {
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text(stashString(R.string.setup_username_label)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(stashString(R.string.setup_password_label)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                    )
                }
                if (statusText != null) {
                    Text(statusText!!, color = MaterialTheme.colorScheme.primary)
                }
                if (errorText != null) {
                    Text(errorText!!, color = MaterialTheme.colorScheme.error)
                }
                Button(
                    onClick = {
                        coroutineScope.launch {
                            val profile = buildSetupProfile(
                                serverName = serverName,
                                serverUrl = serverUrl,
                                apiKey = apiKey,
                                username = username,
                                password = password,
                                authMode = authMode,
                                allowInsecureLocalApiKey = allowInsecureLocalApiKey,
                            )
                            if (!profile.isConfigured()) {
                                errorText = stashString(R.string.auto_kr_0532)
                                return@launch
                            }
                            val requestedAuthMode = if (authMode == SetupAuthMode.Password) {
                                StashServerAuthMode.SessionCookie
                            } else if (authMode == SetupAuthMode.LinkOnly) {
                                StashServerAuthMode.None
                            } else {
                                StashServerAuthMode.ApiKey
                            }
                            if (!canAttemptStashCredentialTransport(profile.baseUrl, requestedAuthMode, profile.allowInsecureLocalApiKey)) {
                                errorText = stashString(R.string.settings_insecure_auth_blocked)
                                return@launch
                            }
                            isSaving = true
                            errorText = null
                            statusText = stashString(R.string.auto_kr_0533)
                            val connectResult = if (authMode == SetupAuthMode.Password) {
                                if (username.isBlank() || password.isBlank()) {
                                    Result.failure(IllegalArgumentException(stashString(R.string.setup_username_password_required)))
                                } else {
                                    runCatching {
                                        val login = StashLoginClient().loginWithPassword(serverUrl, username, password)
                                        profile.copy(
                                            apiKey = "",
                                            authMode = StashServerAuthMode.SessionCookie,
                                            sessionCookie = login.sessionCookie,
                                            username = username,
                                            password = password,
                                            allowInsecureLocalApiKey = profile.allowInsecureLocalApiKey,
                                        )
                                    }
                                }
                            } else {
                                Result.success(profile)
                            }
                            connectResult
                                .mapCatching { authenticatedProfile ->
                                    val version = StashGraphQlClient(authenticatedProfile).testConnection()
                                    repository.saveServerProfile(authenticatedProfile)
                                    version
                                }
                                .onSuccess { version ->
                                    statusText = stashString(R.string.auto_kr_0534, version)
                                    continueOrOfferBiometricAppLock()
                                }
                                .onFailure {
                                    StashDebugLogBuffer.record("Onboarding", "Connection failed", it)
                                    errorText = it.message ?: stashString(R.string.auto_kr_0535)
                                    statusText = null
                                }
                            isSaving = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    enabled = !isSaving,
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Text(stashString(R.string.auto_kr_0536))
                    }
                }
                FilledTonalButton(
                    onClick = {
                        coroutineScope.launch {
                            val profile = buildSetupProfile(
                                serverName = serverName,
                                serverUrl = serverUrl,
                                apiKey = apiKey,
                                username = username,
                                password = password,
                                allowInsecureLocalApiKey = allowInsecureLocalApiKey,
                                authMode = authMode,
                            )
                            if (!canAttemptStashCredentialTransport(profile.baseUrl, profile.authMode, profile.allowInsecureLocalApiKey)) {
                                errorText = stashString(R.string.settings_insecure_auth_blocked)
                                return@launch
                            }
                            repository.saveServerProfile(profile)
                            onContinue()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    enabled = !isSaving && serverUrl.isNotBlank() && shouldAllowUntestedSetupSave(authMode),
                ) {
                    Text(stashString(R.string.auto_kr_0537))
                }
            }
        }
        if (showBiometricOffer) {
            BiometricOnboardingOfferCard(
                availability = deviceAuthenticationAvailability,
                statusMessage = biometricOfferStatus,
                onEnable = {
                    if (deviceAuthenticationAvailability == DeviceAuthenticationAvailability.Available) {
                        authenticateForBiometricOffer?.invoke()
                    } else {
                        biometricOfferStatus = context.getString(
                            deviceAuthenticationAvailabilityDescriptionRes(deviceAuthenticationAvailability),
                        )
                    }
                },
                onLater = {
                    biometricOfferDismissed = true
                    showBiometricOffer = false
                    onContinue()
                },
            )
        }
    }
}

private fun buildSetupProfile(
    serverName: String,
    serverUrl: String,
    apiKey: String,
    username: String,
    password: String,
    allowInsecureLocalApiKey: Boolean,
    authMode: SetupAuthMode = SetupAuthMode.ApiKey,
): StashServerProfile = StashServerProfile(
    name = serverName,
    baseUrl = serverUrl,
    apiKey = if (authMode == SetupAuthMode.ApiKey) apiKey else "",
    username = if (authMode == SetupAuthMode.Password) username else "",
    password = if (authMode == SetupAuthMode.Password) password else "",
    authMode = when (authMode) {
        SetupAuthMode.LinkOnly -> StashServerAuthMode.None
        SetupAuthMode.ApiKey -> StashServerAuthMode.ApiKey
        SetupAuthMode.Password -> StashServerAuthMode.SessionCookie
    },
    allowInsecureLocalApiKey = allowInsecureLocalApiKey,
)

@Composable
private fun SetupAuthModeRow(
    selected: Boolean,
    label: Int,
    description: Int,
    onClick: () -> Unit,
) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stashString(label), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun BiometricOnboardingOfferCard(
    availability: DeviceAuthenticationAvailability,
    statusMessage: String?,
    onEnable: () -> Unit,
    onLater: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stashString(R.string.setup_biometric_offer_title), style = MaterialTheme.typography.titleMedium)
            Text(
                statusMessage ?: stashString(deviceAuthenticationAvailabilityDescriptionRes(availability)),
                style = MaterialTheme.typography.bodySmall,
                color = if (availability == DeviceAuthenticationAvailability.Available) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.error
                },
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = onEnable,
                    enabled = availability == DeviceAuthenticationAvailability.Available,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stashString(R.string.setup_biometric_offer_enable_button))
                }
                Button(
                    onClick = onLater,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stashString(R.string.setup_biometric_offer_later_button))
                }
            }
        }
    }
}

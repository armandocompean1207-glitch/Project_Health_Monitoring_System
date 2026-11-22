package com.cse3310.projecthealthmonitoringsystem.auth

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cse3310.projecthealthmonitoringsystem.PhmsApp
import com.cse3310.projecthealthmonitoringsystem.monitoring.CurrentUser
import com.cse3310.projecthealthmonitoringsystem.monitoring.phms
import com.cse3310.projecthealthmonitoringsystem.ui.components.PhmsTextField
import com.cse3310.projecthealthmonitoringsystem.ui.components.rememberGuardedDismiss
import com.cse3310.projecthealthmonitoringsystem.validation.HealthValidation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private val AuthYellow = Color(0xFFFFDF48)
private val AuthBackground = Color(0xFFFCFAF8)
private val AuthInk = Color(0xFF0F172A)
private val AuthSecondary = Color(0xFF64748B)
private val AuthBorder = Color(0xFFF0E6E0)

private val securityQuestions = listOf(
    "What was the name of your first pet?",
    "What was the name of your first school?",
    "What was your childhood nickname?",
    "What was the model of your first car?",
    "What was the name of your favorite childhood book?"
)

@Composable
fun AccountGate() {
    val id = CurrentUser.userId
    if (id.isBlank()) {
        Scaffold(containerColor = AuthBackground) { padding ->
            LoginScreen(Modifier.padding(padding).consumeWindowInsets(padding))
        }
    } else key(id) { PhmsApp() }
}

@Composable
private fun LoginScreen(modifier: Modifier) {
    val context = LocalContext.current
    val auth = remember { Authentication(context.phms().authRepository) }
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    var register by remember { mutableStateOf(false) }
    var identifier by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    // Credentials and answers stay in memory, never in saved state or preferences.
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var question by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var attempted by remember { mutableStateOf(false) }
    var recovery by remember { mutableStateOf<String?>(null) }

    val nameError = if (register) HealthValidation.name(name, "Full name") else null
    val emailError = if (register && (email.length > 254 || !android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()))
        "Enter a valid email address." else null
    val identifierError = if (register && !identifier.matches(Regex("[A-Za-z0-9]{8,32}")))
        "Use 8–32 letters or numbers." else if (!register && identifier.isBlank()) "Enter your user ID or email." else null
    val passwordError = if (register && password.length !in 12..128) "Use 12–128 characters."
        else if (!register && password.isEmpty()) "Enter your password." else null
    val confirmError = if (register && confirm != password) "Passwords do not match."
        else if (register && confirm.isEmpty()) "Confirm your password." else null
    val questionError = if (register && question !in securityQuestions) "Choose a security question." else null
    val answerError = if (register && answer.isBlank()) "Enter an answer to your selected question."
        else if (register && answer.length > 500) "Use no more than 500 characters." else null

    fun submit() {
        if (busy) return
        attempted = true
        if (listOfNotNull(nameError, emailError, identifierError, passwordError, confirmError, questionError, answerError).isNotEmpty()) return
        busy = true
        error = null
        notice = null
        keyboard?.hide()
        scope.launch {
            try {
                if (register) {
                    // Authentication hashes password and answer independently using random salts.
                    check(auth.register(identifier.trim(), email, name, password.toCharArray(), question, answer.toCharArray())) {
                        "That user ID or email is already registered."
                    }
                    register = false
                    attempted = false
                    password = ""; confirm = ""; answer = ""
                    notice = "Account created. Sign in to continue."
                } else {
                    val id = auth.login(identifier, password.toCharArray())
                    password = ""
                    AccountSession.start(context, id)
                }
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) { error = e.message ?: "Unable to continue. Please try again."
            } finally { busy = false }
        }
    }

    val registrationDirty = register && (name.isNotBlank() || email.isNotBlank() || identifier.isNotBlank() ||
        password.isNotEmpty() || confirm.isNotEmpty() || question.isNotBlank() || answer.isNotEmpty())
    val leaveRegistration = rememberGuardedDismiss(registrationDirty, busy) {
        register = false; attempted = false; error = null; notice = null
        password = ""; confirm = ""; answer = ""
    }
    BackHandler(register && !registrationDirty && !busy) { register = false }

    Column(
        modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        AuthBrandingPanel()
        if (register) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(enabled = !busy, onClick = leaveRegistration) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back to sign in", tint = AuthInk)
                }
                Text("Create your account", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = AuthInk, modifier = Modifier.weight(1f))
            }
        } else {
            Text("Welcome back", style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold), color = AuthInk)
        }
        Surface(color = Color.White, contentColor = AuthInk, shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, AuthBorder), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
                notice?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                if (register) {
                    AuthField(name, { name = it }, "Full name", Icons.Outlined.PersonOutline, !busy,
                        if (attempted) nameError else null, capitalization = KeyboardCapitalization.Words)
                    AuthField(email, { email = it }, "Email address", Icons.Outlined.AlternateEmail, !busy,
                        if (attempted) emailError else null, keyboardType = KeyboardType.Email)
                }
                AuthField(identifier, { identifier = it }, if (register) "User ID" else "User ID or email",
                    if (register) Icons.Outlined.Badge else Icons.Outlined.PersonOutline, !busy,
                    if (attempted) identifierError else null)
                PasswordField(password, { password = it }, "Password", !busy, if (attempted) passwordError else null,
                    imeAction = if (register) ImeAction.Next else ImeAction.Done, onDone = { submit() })
                if (register) {
                    if (!attempted || passwordError == null) Text("Use 12–128 characters.",
                        style = MaterialTheme.typography.bodyMedium, color = AuthSecondary)
                    PasswordField(confirm, { confirm = it }, "Confirm password", !busy, if (attempted) confirmError else null)
                    HorizontalDivider(Modifier.padding(vertical = 8.dp), color = AuthBorder)
                    Text("Account recovery", style = MaterialTheme.typography.titleLarge, color = AuthInk)
                    SecurityQuestionPicker(question, { question = it }, !busy, if (attempted) questionError else null)
                    PasswordField(answer, { answer = it }, "Your answer", !busy, if (attempted) answerError else null,
                        icon = Icons.Outlined.Key, imeAction = ImeAction.Done, onDone = { submit() })
                }
                Button(onClick = { submit() }, enabled = !busy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(containerColor = AuthYellow, contentColor = AuthInk)) {
                    if (busy) {
                        CircularProgressIndicator(Modifier.size(20.dp), color = AuthInk, strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (busy) "Please wait…" else if (register) "Create account" else "Sign in",
                        style = MaterialTheme.typography.titleMedium)
                }
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    AuthLink(if (register) "Already have an account? Sign in" else "New here? Create an account", !busy) {
                        if (register) leaveRegistration() else {
                            register = true; attempted = false; error = null; notice = null
                            password = ""; confirm = ""; answer = ""
                        }
                    }
                    if (!register) {
                        AuthLink("Forgot password?", !busy, secondary = true) { recovery = "password" }
                        AuthLink("Forgot email or user ID?", !busy, secondary = true) { recovery = "identifier" }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
    recovery?.let { kind ->
        AlertDialog(onDismissRequest = { recovery = null },
            title = { Text(if (kind == "password") "Password recovery" else "Find your sign-in details") },
            text = {
                Text(if (kind == "password")
                    "Password recovery is not available in this version. PHMS has no verified recovery service, and a security answer alone cannot reset your password. No recovery email has been sent."
                else "You can sign in with either your registered email address or user ID. PHMS cannot retrieve or email account details without verified account recovery.")
            },
            confirmButton = { TextButton(onClick = { recovery = null }) { Text("Back to sign in", color = AuthInk) } })
    }
}

@Composable
private fun AuthBrandingPanel() {
    Surface(color = AuthYellow, contentColor = AuthInk, shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Outlined.HealthAndSafety, null, Modifier.size(64.dp))
            Text("PHMS", style = MaterialTheme.typography.displayLarge.copy(
                fontSize = 48.sp, lineHeight = 60.sp, fontWeight = FontWeight.ExtraBold),
                textAlign = TextAlign.Center)
            Text("A little care, every day.", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun AuthLink(text: String, enabled: Boolean, secondary: Boolean = false, onClick: () -> Unit) {
    TextButton(onClick = onClick, enabled = enabled, modifier = Modifier.heightIn(min = 48.dp),
        colors = ButtonDefaults.textButtonColors(contentColor = if (secondary) AuthSecondary else AuthInk)) {
        Text(text, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
    }
}

@Composable
private fun AuthField(value: String, onChange: (String) -> Unit, label: String, icon: ImageVector,
    enabled: Boolean, error: String? = null, keyboardType: KeyboardType = KeyboardType.Text,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.None,
    trailingIcon: (@Composable () -> Unit)? = null, transformation: VisualTransformation = VisualTransformation.None,
    imeAction: ImeAction = ImeAction.Next, onDone: () -> Unit = {}) {
    val focus = LocalFocusManager.current
    PhmsTextField(value, onChange, placeholder = { Text(label, color = AuthSecondary) },
        leadingIcon = { Icon(icon, null, tint = AuthSecondary, modifier = Modifier.size(22.dp)) },
        trailingIcon = trailingIcon, enabled = enabled, singleLine = true,
        isError = error != null, supportingText = if (error != null) ({ Text(error) }) else null,
        modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp).semantics { contentDescription = label },
        shape = RoundedCornerShape(16.dp), visualTransformation = transformation,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = AuthInk),
        keyboardOptions = KeyboardOptions(capitalization = capitalization, keyboardType = keyboardType,
            imeAction = imeAction, autoCorrectEnabled = capitalization == KeyboardCapitalization.Words),
        keyboardActions = KeyboardActions(onNext = { focus.moveFocus(androidx.compose.ui.focus.FocusDirection.Next) },
            onDone = { onDone() }),
        colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = Color.White, unfocusedContainerColor = Color.White,
            disabledContainerColor = Color.White, focusedBorderColor = AuthYellow, unfocusedBorderColor = Color(0xFFD6D3D1),
            focusedTextColor = AuthInk, unfocusedTextColor = AuthInk, cursorColor = AuthInk))
}

@Composable
private fun SecurityQuestionPicker(question: String, onSelected: (String) -> Unit, enabled: Boolean, error: String?) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box {
            OutlinedButton(onClick = { expanded = true }, enabled = enabled,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                border = BorderStroke(1.dp, if (error == null) Color(0xFFD6D3D1) else MaterialTheme.colorScheme.error),
                shape = RoundedCornerShape(28.dp), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AuthInk)) {
                Text(question.ifBlank { "Choose a security question" }, modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium, color = if (question.isBlank()) AuthSecondary else AuthInk)
                Icon(Icons.Outlined.ExpandMore, "Security question options", Modifier.padding(start = 8.dp).size(20.dp))
            }
            DropdownMenu(expanded, onDismissRequest = { expanded = false },
                modifier = Modifier.widthIn(max = 320.dp)) {
                securityQuestions.forEach { item ->
                    DropdownMenuItem(text = { Text(item, style = MaterialTheme.typography.bodyMedium) },
                        onClick = { onSelected(item); expanded = false })
                }
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
fun PasswordField(value: String, onChange: (String) -> Unit, label: String, enabled: Boolean = true,
    error: String? = null, icon: ImageVector = Icons.Outlined.Lock,
    imeAction: ImeAction = ImeAction.Next, onDone: () -> Unit = {}) {
    var visible by remember { mutableStateOf(false) }
    AuthField(value, onChange, label, icon, enabled, error, keyboardType = KeyboardType.Password,
        transformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        imeAction = imeAction, onDone = onDone,
        trailingIcon = {
            IconButton(enabled = enabled, onClick = { visible = !visible }) {
                Icon(if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                    if (visible) "Hide $label" else "Show $label", tint = AuthSecondary)
            }
        })
}

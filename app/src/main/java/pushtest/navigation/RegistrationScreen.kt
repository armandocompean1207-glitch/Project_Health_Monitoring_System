package pushtest.navigation

import android.util.Patterns
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import java.time.LocalDate

@Composable
fun RegistrationScreen(
    onNext: () -> Unit = {},
    onCancel: () -> Unit = {},
    onExit: () -> Unit = {},
    onSubmit: (
        String, String, String, String, String,
        String, String, String, String, String
    ) -> Unit = { _, _, _, _, _, _, _, _, _, _ -> }
) {
    var firstName by remember { mutableStateOf("") }
    var middleInitial by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var userId by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf("M") }
    var birthday by remember { mutableStateOf("") }
    var weight by remember { mutableStateOf("") }
    var height by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }

    val passwordValid =
        password.any { it.isUpperCase() } &&
                password.any { it.isLowerCase() } &&
                password.any { it.isDigit() } &&
                password.any { !it.isLetterOrDigit() }

    val userIdValid =
        userId.length >= 8 &&
                userId.all { it.isLetterOrDigit() }

    val emailValid =
        Patterns.EMAIL_ADDRESS
            .matcher(email).matches()

    val phoneValid =
        phone.count { it.isDigit() } >= 10

    val formValid =
        firstName.isNotBlank() &&
                lastName.isNotBlank() &&
                emailValid &&
                phoneValid &&
                userIdValid &&
                passwordValid &&
                password == confirmPassword &&
                birthday.isNotBlank() &&
                weight.toDoubleOrNull()?.let { it > 0 } == true &&
                height.toDoubleOrNull()?.let { it > 0 } == true

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "Create Account",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Text("Personal Information",
            style = MaterialTheme.typography.titleLarge)

        OutlinedTextField(
            value = firstName,
            onValueChange = { firstName = it },
            label = { Text("First Name *") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        OutlinedTextField(
            value = middleInitial,
            onValueChange = {
                middleInitial = it.take(1).filter { c -> c.isLetter() }
            },
            label = { Text("Middle Initial (Optional)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        OutlinedTextField(
            value = lastName,
            onValueChange = { lastName = it },
            label = { Text("Last Name *") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email Address *") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        OutlinedTextField(
            value = phone,
            onValueChange = {
                if (it.all { c -> c.isDigit() || c == '+' || c == '-' || c == ' ' }) {
                    phone = it
                }
            },
            label = { Text("Phone Number *") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        HorizontalDivider()

        Text("Account Information",
            style = MaterialTheme.typography.titleLarge)

        OutlinedTextField(
            value = userId,
            onValueChange = {
                userId = it.filter { c -> c.isLetterOrDigit() }
            },
            label = { Text("User ID *") },
            supportingText = {
                Text("At least 8 characters. Letters and numbers only.")
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password *") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Text(
            "Password requirements:",
            fontWeight = FontWeight.Bold
        )

        Text("• At least one uppercase letter (A-Z)")
        Text("• At least one lowercase letter (a-z)")
        Text("• At least one number (0-9)")
        Text("• At least one special character (!@#\$%^&*)")

        Text(
            if (password.isEmpty()) "Password not entered"
            else if (passwordValid) "Password requirements met"
            else "Password does not meet all requirements",
            color = if (passwordValid)
                MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.error
        )

        OutlinedTextField(
            value = confirmPassword,
            onValueChange = { confirmPassword = it },
            label = { Text("Confirm Password *") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        if (confirmPassword.isNotEmpty() &&
            password != confirmPassword) {
            Text(
                "Passwords do not match.",
                color = MaterialTheme.colorScheme.error
            )
        }

        HorizontalDivider()

        Text("Personal Details",
            style = MaterialTheme.typography.titleLarge)

        Text("Gender *", fontWeight = FontWeight.Bold)

        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            listOf("M", "F", "Other").forEach { option ->
                Row {
                    RadioButton(
                        selected = gender == option,
                        onClick = { gender = option }
                    )
                    Text(
                        option,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
            }
        }

        OutlinedTextField(
            value = birthday,
            onValueChange = { birthday = it },
            label = { Text("Birthday * (YYYY-MM-DD)") },
            placeholder = { Text("2000-01-01") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        OutlinedTextField(
            value = weight,
            onValueChange = { weight = it },
            label = { Text("Weight (kg) *") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        OutlinedTextField(
            value = height,
            onValueChange = { height = it },
            label = { Text("Height (cm) *") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        HorizontalDivider()

        Text(
            "Security Questions",
            style = MaterialTheme.typography.titleLarge
        )

        Text(
            "Security questions will be configured later. " +
                    "This section is currently unavailable.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        OutlinedTextField(
            value = "",
            onValueChange = {},
            enabled = false,
            label = { Text("Security Question") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = "",
            onValueChange = {},
            enabled = false,
            label = { Text("Security Answer") },
            modifier = Modifier.fillMaxWidth()
        )

        HorizontalDivider()

        Button(
            onClick = {
                if (formValid) {
                    onNext()
                } else {
                    message = "Please complete all required fields correctly."
                }
            },
            enabled = formValid,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Next – Doctor Information")
        }

        Button(
            onClick = {
                if (formValid) {
                    onSubmit(
                        firstName,
                        middleInitial,
                        lastName,
                        email,
                        phone,
                        userId,
                        password,
                        gender,
                        birthday,
                        "$weight kg, $height cm"
                    )
                } else {
                    message = "Please complete all required fields correctly."
                }
            },
            enabled = formValid,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Submit")
        }

        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Cancel")
        }

        TextButton(
            onClick = onExit,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Exit to Home")
        }

        if (message.isNotEmpty()) {
            Text(
                message,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}
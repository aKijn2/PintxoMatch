package com.example.pintxomatch.ui.screens

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pintxomatch.ui.components.AppSnackbarHost
import com.google.firebase.auth.FirebaseAuth

private enum class PasswordResetStep {
    RequestEmail,
    ConfirmCode
}

private fun extractFirebaseActionCode(rawInput: String): String {
    val cleanedInput = rawInput.trim()
    if (cleanedInput.isBlank()) return ""

    return try {
        if (cleanedInput.contains("oobCode=")) {
            Uri.parse(cleanedInput).getQueryParameter("oobCode") ?: cleanedInput
        } else {
            cleanedInput
        }
    } catch (_: Exception) {
        cleanedInput
    }
}

@Composable
fun LoginScreen(onLoginSuccess: () -> Unit) {
    val auth = FirebaseAuth.getInstance()

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isRegistering by remember { mutableStateOf(false) } // Switch entre Login y Registro
    var isLoading by remember { mutableStateOf(false) }
    var alertMessage by remember { mutableStateOf<String?>(null) }
    var showResetDialog by remember { mutableStateOf(false) }
    var resetStep by remember { mutableStateOf(PasswordResetStep.RequestEmail) }
    var resetEmail by remember { mutableStateOf("") }
    var resetCodeInput by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmNewPassword by remember { mutableStateOf("") }
    var isResetLoading by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(alertMessage) {
        alertMessage?.let {
            snackbarHostState.showSnackbar(it)
            alertMessage = null
        }
    }

    fun submitAuth() {
        val cleanEmail = email.trim()
        val cleanPassword = password
            .replace("\n", "")
            .replace("\r", "")

        if (cleanEmail.isBlank() || cleanPassword.length < 6) {
            alertMessage = "Mínimo 6 caracteres"
            return
        }

        isLoading = true
        if (isRegistering) {
            auth.createUserWithEmailAndPassword(cleanEmail, cleanPassword)
                .addOnSuccessListener {
                    isLoading = false
                    onLoginSuccess()
                }
                .addOnFailureListener {
                    isLoading = false
                    alertMessage = "Error: ${it.message}"
                }
        } else {
            auth.signInWithEmailAndPassword(cleanEmail, cleanPassword)
                .addOnSuccessListener {
                    isLoading = false
                    onLoginSuccess()
                }
                .addOnFailureListener {
                    isLoading = false
                    alertMessage = "Correo o contraseña incorrectos"
                }
        }
    }

    fun sendPasswordResetEmail() {
        val cleanEmail = resetEmail.trim()
        if (cleanEmail.isBlank()) {
            alertMessage = "Introduce tu correo"
            return
        }

        isResetLoading = true
        auth.sendPasswordResetEmail(cleanEmail)
            .addOnSuccessListener {
                isResetLoading = false
                resetStep = PasswordResetStep.ConfirmCode
                alertMessage = "Te enviamos un correo con el codigo/enlace de recuperacion"
            }
            .addOnFailureListener {
                isResetLoading = false
                alertMessage = "No se pudo enviar el correo: ${it.message}"
            }
    }

    fun confirmPasswordReset() {
        val actionCode = extractFirebaseActionCode(resetCodeInput)
        val pwd1 = newPassword.replace("\n", "").replace("\r", "")
        val pwd2 = confirmNewPassword.replace("\n", "").replace("\r", "")

        if (actionCode.isBlank()) {
            alertMessage = "Pega el codigo o el enlace del correo"
            return
        }
        if (pwd1.length < 6) {
            alertMessage = "La nueva contrasena debe tener minimo 6 caracteres"
            return
        }
        if (pwd1 != pwd2) {
            alertMessage = "Las contrasenas no coinciden"
            return
        }

        isResetLoading = true
        auth.verifyPasswordResetCode(actionCode)
            .addOnSuccessListener {
                auth.confirmPasswordReset(actionCode, pwd1)
                    .addOnSuccessListener {
                        isResetLoading = false
                        showResetDialog = false
                        resetStep = PasswordResetStep.RequestEmail
                        resetCodeInput = ""
                        newPassword = ""
                        confirmNewPassword = ""
                        alertMessage = "Contrasena actualizada. Ya puedes iniciar sesion"
                    }
                    .addOnFailureListener {
                        isResetLoading = false
                        alertMessage = "No se pudo actualizar la contrasena: ${it.message}"
                    }
            }
            .addOnFailureListener {
                isResetLoading = false
                alertMessage = "Codigo invalido o expirado"
            }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
        ) { innerPadding ->
            Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
            Text(
                text = if (isRegistering) "Crear cuenta" else "Bienvenido",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(32.dp))

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Correo electrónico") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Contraseña") },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        if (!isLoading) submitAuth()
                    }
                )
            )

            Spacer(modifier = Modifier.height(32.dp))

            if (isLoading) {
                CircularProgressIndicator()
            } else {
                Button(
                    onClick = { submitAuth() },
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    Text(if (isRegistering) "Registrarse" else "Entrar")
                }

                if (!isRegistering) {
                    TextButton(
                        onClick = {
                            resetEmail = email.trim()
                            resetStep = PasswordResetStep.RequestEmail
                            showResetDialog = true
                        }
                    ) {
                        Text("Olvidaste tu contrasena?")
                    }
                }

                TextButton(onClick = { isRegistering = !isRegistering }) {
                    Text(if (isRegistering) "¿Ya tienes cuenta? Inicia sesión" else "¿No tienes cuenta? Regístrate")
                }
            }
        }

            if (showResetDialog) {
                AlertDialog(
                    onDismissRequest = {
                        if (!isResetLoading) {
                            showResetDialog = false
                        }
                    },
                    title = {
                        Text("Recuperar contrasena")
                    },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (resetStep == PasswordResetStep.RequestEmail) {
                                Text("Escribe tu correo y te enviaremos un codigo/enlace para cambiar la contrasena.")
                                OutlinedTextField(
                                    value = resetEmail,
                                    onValueChange = { resetEmail = it },
                                    label = { Text("Correo electronico") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Email,
                                        imeAction = ImeAction.Done
                                    ),
                                    keyboardActions = KeyboardActions(
                                        onDone = {
                                            if (!isResetLoading) sendPasswordResetEmail()
                                        }
                                    )
                                )
                            } else {
                                Text("Pega el codigo o el enlace recibido por correo y define tu nueva contrasena.")
                                OutlinedTextField(
                                    value = resetCodeInput,
                                    onValueChange = { resetCodeInput = it },
                                    label = { Text("Codigo o enlace") },
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = newPassword,
                                    onValueChange = { newPassword = it },
                                    label = { Text("Nueva contrasena") },
                                    visualTransformation = PasswordVisualTransformation(),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Password,
                                        imeAction = ImeAction.Next
                                    )
                                )
                                OutlinedTextField(
                                    value = confirmNewPassword,
                                    onValueChange = { confirmNewPassword = it },
                                    label = { Text("Repite la nueva contrasena") },
                                    visualTransformation = PasswordVisualTransformation(),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Password,
                                        imeAction = ImeAction.Done
                                    ),
                                    keyboardActions = KeyboardActions(
                                        onDone = {
                                            if (!isResetLoading) confirmPasswordReset()
                                        }
                                    )
                                )
                            }
                        }
                    },
                    confirmButton = {
                        if (isResetLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        } else {
                            TextButton(
                                onClick = {
                                    if (resetStep == PasswordResetStep.RequestEmail) {
                                        sendPasswordResetEmail()
                                    } else {
                                        confirmPasswordReset()
                                    }
                                }
                            ) {
                                Text(if (resetStep == PasswordResetStep.RequestEmail) "Enviar" else "Guardar")
                            }
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                if (resetStep == PasswordResetStep.ConfirmCode) {
                                    resetStep = PasswordResetStep.RequestEmail
                                } else {
                                    showResetDialog = false
                                }
                            },
                            enabled = !isResetLoading
                        ) {
                            Text(if (resetStep == PasswordResetStep.ConfirmCode) "Atras" else "Cancelar")
                        }
                    }
                )
            }

        }
    }
    AppSnackbarHost(
        hostState = snackbarHostState,
        modifier = Modifier.align(Alignment.TopCenter)
    )
    } // end outer Box
}

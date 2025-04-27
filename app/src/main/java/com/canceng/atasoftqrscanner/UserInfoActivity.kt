package com.canceng.atasoftqrscanner

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.canceng.atasoftqrscanner.ui.theme.QRCodeScannerTheme
import com.google.firebase.firestore.FirebaseFirestore

class UserInfoActivity : ComponentActivity() {
    private val TAG = "UserInfoActivity"

    // Initialize Firestore only if available
    private val firestore by lazy {
        try {
            FirebaseFirestore.getInstance()
        } catch (e: Exception) {
            Log.w(TAG, "Firebase not available: ${e.message}")
            null
        }
    }

    private val errorMessage = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val qrContent = intent.getStringExtra("UUID") ?: return

        setContent {
            QRCodeScannerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    UserInfoScreen(
                        qrContent = qrContent,
                        error = errorMessage.value,
                        onAcceptClicked = {
                            // Increment counter when Accept button is pressed
                            incrementCounter(qrContent)
                            finish() // Return to scanner screen
                        },
                        onRejectClicked = {
                            // Just return to scanner screen without incrementing counter
                            finish()
                        }
                    )
                }
            }
        }
    }

    private fun incrementCounter(qrContent: String) {
        if (firestore == null) {
            Log.i(TAG, "Firebase not available, skipping counter increment for: $qrContent")
            return
        }

        firestore?.collection("users")
            ?.document(qrContent)
            ?.update("Counter", com.google.firebase.firestore.FieldValue.increment(1))
            ?.addOnFailureListener { e ->
                errorMessage.value = getString(R.string.error_updating_counter, e.message)
            }
    }
}

@Composable
fun UserInfoScreen(
    qrContent: String,
    error: String?,
    onAcceptClicked: () -> Unit,
    onRejectClicked: () -> Unit
) {
    val context = LocalContext.current
    var userInfo by remember { mutableStateOf(context.getString(R.string.loading)) }
    var isLoading by remember { mutableStateOf(true) }
    var errorState by remember { mutableStateOf<String?>(null) }
    var userExists by remember { mutableStateOf(false) } // Track if user exists

    val isDarkTheme = isSystemInDarkTheme() // Check if dark theme is active

    // Try to get Firestore instance
    val firestore = try {
        FirebaseFirestore.getInstance()
    } catch (e: Exception) {
        null
    }

    LaunchedEffect(key1 = qrContent) {
        if (firestore == null) {
            userInfo = context.getString(R.string.firebase_unavailable, qrContent)
            isLoading = false
            userExists = false
            return@LaunchedEffect
        }

        firestore.collection("users")
            .document(qrContent)
            .get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    // Extract fields for display
                    val isim = document.getString("Ad-Soyad") ?: "N/A"
                    val posta = document.getString("Eposta") ?: "N/A"
                    val telefon = document.getString("Telefon numaranız") ?: "N/A"
                    val counter = document.getLong("Counter") ?: 0
                    userInfo = "$isim\n$posta\n0$telefon\nZiyaret sayısı: $counter"
                    userExists = true
                } else {
                    userInfo = context.getString(R.string.user_not_found_with_id, qrContent)
                    userExists = false
                }
                isLoading = false
            }
            .addOnFailureListener { e ->
                Log.e("UserInfoActivity", "Firestore error: ${e.message}", e)
                when {
                    e.message?.contains("permission_denied", ignoreCase = true) == true -> {
                        errorState = context.getString(R.string.firestore_permission_denied)
                    }
                    else -> {
                        errorState = e.message
                    }
                }
                userInfo = context.getString(R.string.error_loading_data)
                isLoading = false
                userExists = false
            }
    }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.Top, // Align content to the top
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Add Logo at the top - Conditionally select based on theme
            Image(
                painter = painterResource(id = if (isDarkTheme) R.drawable.logoblack else R.drawable.ailogo), // Select logo based on theme
                contentDescription = "App Logo",
                contentScale = ContentScale.Crop, // Ensure image fills the bounds
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp) // Apply padding first
                    .clip(RoundedCornerShape(percent = 30)) // Apply clipping before size
                    .size(120.dp) // Set the size after clipping
            )

            Text(
                text = stringResource(R.string.user_information),
                // Use a larger headline text style
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp,
                modifier = Modifier.padding(bottom = 24.dp, top = 16.dp) // Added top padding
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Text(
                    text = userInfo,
                    // Use a larger body text style with increased font size
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 18.sp,
                        lineHeight = 46.sp
                    ),
                    modifier = Modifier.padding(20.dp),
                    textAlign = TextAlign.Start // changed to left-align labels
                )
            }

            // Row with Accept and Reject buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Reject Button
                Button(
                    onClick = onRejectClicked,
                    enabled = !isLoading && userExists, // Disable if user not found
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) {
                    Text(
                        text = stringResource(R.string.reject),
                        fontSize = 20.sp,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }

                // Accept Button
                Button(
                    onClick = onAcceptClicked,
                    enabled = !isLoading && userExists, // Disable if user not found
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.accept),
                        fontSize = 20.sp,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }

            if (!error.isNullOrEmpty() || !errorState.isNullOrEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = error ?: errorState ?: "",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        // Use a larger text style for error messages
                        style = MaterialTheme.typography.titleLarge,
                        fontSize = 22.sp
                    )
                }
            }
        }
    }
}

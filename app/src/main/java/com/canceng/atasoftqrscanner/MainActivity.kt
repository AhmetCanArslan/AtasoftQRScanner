package com.canceng.atasoftqrscanner

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.ActivityResultLauncher
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
// Use full import path to avoid ambiguity
import androidx.camera.core.Preview as CameraPreview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
// Use full import path to avoid ambiguity
import androidx.compose.ui.tooling.preview.Preview as ComposePreview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.canceng.atasoftqrscanner.ui.theme.QRCodeScannerTheme
import com.google.common.util.concurrent.ListenableFuture
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    private val TAG = "QRScanner"
    private lateinit var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>
    private lateinit var barcodeScanner: BarcodeScanner

    // Initialize FireStore only if available
    private val firestore by lazy {
        try {
            com.google.firebase.firestore.FirebaseFirestore.getInstance()
        } catch (e: Exception) {
            Log.w(TAG, "Firebase not available: ${e.message}")
            null
        }
    }

    private var scannerPaused = false
    private var errorMessage = mutableStateOf<String?>(null)
    private var isLoading = mutableStateOf(false) // Add loading state

    // State for user not found dialog
    private var showUserNotFoundDialog = mutableStateOf(false)
    
    // State to track if the camera should be shown
    private var showCamera = mutableStateOf(false)

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // Initialize camera but don't show it yet
            startCamera()
            // Keep camera hidden until user clicks Continue
        } else {
            errorMessage.value = getString(R.string.camera_permission_required)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize barcode scanner with support for all formats
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_QR_CODE,
                Barcode.FORMAT_AZTEC,
                Barcode.FORMAT_DATA_MATRIX,
                Barcode.FORMAT_PDF417
            )
            .build()
        barcodeScanner = BarcodeScanning.getClient(options)

        setContent {
            QRCodeScannerTheme {
                QRScannerScreen(
                    errorMessage = errorMessage.value,
                    isLoading = isLoading.value, // Pass loading state
                    showUserNotFoundDialog = showUserNotFoundDialog.value,
                    showCamera = showCamera.value,
                    onStartCamera = {
                        showCamera.value = true
                        // Clear any error messages when starting camera
                        errorMessage.value = null
                        // Reset scanner state when camera is started
                        scannerPaused = false
                    },
                    // Changed: automatically restart camera scanning on dismiss
                    onDismissDialog = {
                        showUserNotFoundDialog.value = false
                        showCamera.value = true    // changed from false to true
                        scannerPaused = false
                        isLoading.value = false // Ensure loading is reset
                    },
                )
            }
        }

        // Check permissions after UI setup
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
            showCamera.value = true   // changed: automatically start scanning without button click
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    override fun onResume() {
        super.onResume()
        // Don't automatically show camera on resume
        // User needs to click Continue button explicitly
        scannerPaused = false
    }

    private fun startCamera() {
        cameraProviderFuture = ProcessCameraProvider.getInstance(this)
    }
    
    private fun bindPreview(lifecycleOwner: LifecycleOwner, previewView: androidx.camera.view.PreviewView) {
        try {
            val cameraProvider = cameraProviderFuture.get()
            
            // Create the Preview use case
            val preview = CameraPreview.Builder().build()
            
            // Create camera selector - default to back camera
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            
            // Set up the image analyzer
            val analyzer = QRCodeAnalyzer { barcodes ->
                // Skip processing if scanner is paused
                if (scannerPaused) return@QRCodeAnalyzer
                
                if (barcodes.isNotEmpty()) {
                    val barcode = barcodes[0]  // Process only the first detected barcode
                    val rawValue = barcode.rawValue
                    
                    if (!rawValue.isNullOrEmpty()) {
                        // Pause scanner to prevent multiple detections
                        scannerPaused = true
                        
                        // Process the QR code content
                        processQRCode(rawValue)
                    }
                }
            }
            
            // Set up image analysis use case
            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .apply {
                    setAnalyzer(Executors.newSingleThreadExecutor(), analyzer)
                }
            
            // Unbind all use cases before rebinding
            cameraProvider.unbindAll()
            
            // Bind use cases to camera
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageAnalysis
            )
            
            // Connect the preview to the PreviewView
            preview.setSurfaceProvider(previewView.surfaceProvider)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error binding camera: ${e.message}", e)
            errorMessage.value = getString(R.string.camera_binding_failed, e.message)
        }
    }
    
    private fun processQRCode(content: String) {
        if (content.isBlank()) {
            errorMessage.value = "Empty QR code detected"
            scannerPaused = false
            return
        }
        
        isLoading.value = true // Start loading
        errorMessage.value = null // Clear previous errors

        // Check if Firebase is available
        if (firestore == null) {
            // If Firebase not available, still allow viewing the result
            val intent = Intent(this, UserInfoActivity::class.java)
            intent.putExtra("UUID", content)
            startActivity(intent)
            // Reset state after starting activity
            scannerPaused = false
            isLoading.value = false // Stop loading
            return
        }
        
        // Use fixed collection "users"
        firestore?.collection("users")
            ?.document(content)
            ?.get()
            ?.addOnSuccessListener { document ->
                isLoading.value = false // Stop loading
                if (document != null && document.exists()) {
                    // Launch UserInfoActivity with the content if found
                    val intent = Intent(this, UserInfoActivity::class.java)
                    intent.putExtra("UUID", content)
                    startActivity(intent)
                    // scannerPaused will be reset in onResume
                } else {
                    // Show the user not found dialog and hide camera
                    showUserNotFoundDialog.value = true
                    showCamera.value = false
                    // scannerPaused will be reset when dialog is dismissed
                }
            }
            ?.addOnFailureListener { e ->
                isLoading.value = false // Stop loading
                Log.e(TAG, "Error checking FireStore: ${e.message}", e)
                errorMessage.value = getString(R.string.error_checking_database, e.message)
                scannerPaused = false // Allow retrying
            }
    }

    @Composable
    fun QRScannerScreen(
        errorMessage: String?,
        isLoading: Boolean, // Add isLoading parameter
        showUserNotFoundDialog: Boolean,
        showCamera: Boolean,
        onStartCamera: () -> Unit,
        onDismissDialog: () -> Unit,
    ) {
        val lifecycleOwner = LocalLifecycleOwner.current
        val context = LocalContext.current

        Scaffold { innerPadding ->
            Box(modifier = Modifier.fillMaxSize()) { // Use Box for potential overlay
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Top blank space
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .background(MaterialTheme.colorScheme.background)
                    )

                    // Adjusted: Camera preview area with reduced weight (was 4f, now 3f)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(3f)
                            .padding(horizontal = 32.dp),
                        contentAlignment = Alignment.Center // Keep content centered
                    ) {
                        // Camera preview card with rounded corners
                        Card(
                            modifier = Modifier
                                .fillMaxSize(),
                            elevation = CardDefaults.cardElevation(
                                defaultElevation = 4.dp
                            )
                        ) {
                            if (showCamera) {
                                AndroidView(
                                    factory = { ctx ->
                                        androidx.camera.view.PreviewView(ctx).apply {
                                            implementationMode =
                                                androidx.camera.view.PreviewView.ImplementationMode.COMPATIBLE
                                        }
                                    },
                                    modifier = Modifier.fillMaxSize(),
                                    update = { previewView ->
                                        if (::cameraProviderFuture.isInitialized) {
                                            cameraProviderFuture.addListener({
                                                try {
                                                    bindPreview(lifecycleOwner, previewView)
                                                } catch (e: Exception) {
                                                    Log.e(TAG, "Error binding camera: ${e.message}", e)
                                                }
                                            }, ContextCompat.getMainExecutor(context))
                                        }
                                    }
                                )
                            } else {
                                // Show a placeholder until user starts camera
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.DarkGray),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Text(
                                            text = stringResource(R.string.camera_permission_info),
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = Color.White,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.padding(bottom = 16.dp)
                                        )
                                        Button(
                                            onClick = onStartCamera,
                                            modifier = Modifier.padding(horizontal = 32.dp)
                                        ) {
                                            Text(
                                                text = stringResource(R.string.continue_button),
                                                style = MaterialTheme.typography.titleMedium
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        // Overlay Loading Indicator if loading and camera is shown
                        if (isLoading && showCamera) {
                             CircularProgressIndicator(
                                 modifier = Modifier.align(Alignment.Center)
                             )
                        }
                    }

                    // Bottom space for error messages or instructions
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .background(MaterialTheme.colorScheme.background)
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        // Show error message if not loading
                        if (!errorMessage.isNullOrEmpty() && !isLoading) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer
                                )
                            ) {
                                Text(
                                    text = errorMessage,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    // Use a larger text style
                                    style = MaterialTheme.typography.headlineSmall
                                )
                            }
                        } else if (!isLoading) { // Show instructions only if not loading and no error
                            if (showCamera) {
                                Text(
                                    text = stringResource(R.string.position_qr),
                                    textAlign = TextAlign.Center,
                                    // Use a larger text style
                                    style = MaterialTheme.typography.headlineSmall
                                )
                            }
                        }
                    }
                }

                // Show user not found dialog if needed (remains outside the Column)
                if (showUserNotFoundDialog) {
                    UserNotFoundDialog(onDismiss = onDismissDialog)
                }
            }
        }
    }

    @Composable
    fun UserNotFoundDialog(onDismiss: () -> Unit) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = null,
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Red X icon
                    Image(
                        painter = painterResource(id = R.drawable.ic_error_x),
                        contentDescription = "Error",
                        modifier = Modifier.size(80.dp)
                    )

                    // Error message
                    Text(
                        text = stringResource(R.string.user_not_found_message),
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.continue_button),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            },
            dismissButton = null
        )
    }
    
    // QRCodeAnalyzer class for image analysis
    @OptIn(ExperimentalGetImage::class)
    private inner class QRCodeAnalyzer(
        private val onBarcodesDetected: (barcodes: List<Barcode>) -> Unit
    ) : ImageAnalysis.Analyzer {
        override fun analyze(imageProxy: ImageProxy) {
            val mediaImage = imageProxy.image
            if (mediaImage != null) {
                val image = InputImage.fromMediaImage(
                    mediaImage,
                    imageProxy.imageInfo.rotationDegrees
                )
                
                // Process the image
                barcodeScanner.process(image)
                    .addOnSuccessListener { barcodes ->
                        onBarcodesDetected(barcodes)
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Barcode scanning failed: ${e.message}", e)
                        errorMessage.value = getString(R.string.scanning_failed, e.message)
                    }
                    .addOnCompleteListener {
                        // Close the image proxy to allow processing of next image
                        imageProxy.close()
                    }
            } else {
                imageProxy.close()
            }
        }
    }
}


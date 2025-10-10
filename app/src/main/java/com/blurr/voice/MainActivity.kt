package com.blurr.voice

import android.Manifest
import android.annotation.SuppressLint
import android.app.role.RoleManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Shader
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.provider.SyncStateContract
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.widget.VideoView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.graphics.toColorInt
import androidx.lifecycle.lifecycleScope
import com.android.billingclient.api.*
import com.blurr.voice.v2.AgentService
import com.blurr.voice.services.EnhancedWakeWordService
import com.blurr.voice.utilities.FreemiumManager
import com.blurr.voice.utilities.Logger
import com.blurr.voice.utilities.OnboardingManager
import com.blurr.voice.utilities.PermissionManager
import com.blurr.voice.utilities.UserIdManager
import com.blurr.voice.utilities.UserProfileManager
import com.blurr.voice.utilities.VideoAssetManager
import com.blurr.voice.utilities.WakeWordManager
import com.blurr.voice.api.PicovoiceKeyManager
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await
import java.io.File

class MainActivity : BaseNavigationActivity() {

    private lateinit var handler: Handler
    private lateinit var managePermissionsButton: TextView
    private lateinit var tvPermissionStatus: TextView
    private lateinit var settingsButton: ImageButton
    private lateinit var saveKeyButton: TextView
    private lateinit var userId: String
    private lateinit var runExampleButton: TextView
    private lateinit var permissionManager: PermissionManager
    private lateinit var wakeWordManager: WakeWordManager
    private lateinit var auth: FirebaseAuth
    private lateinit var tasksRemainingTextView: TextView
    private lateinit var freemiumManager: FreemiumManager
    private lateinit var wakeWordHelpLink: TextView
    private lateinit var increaseLimitsLink: TextView
    private lateinit var onboardingManager: OnboardingManager
    private lateinit var requestRoleLauncher: ActivityResultLauncher<Intent>
    private lateinit var billingStatusTextView: TextView
    private lateinit var loadingOverlay: View

    private lateinit var root: View
    companion object {
        const val ACTION_WAKE_WORD_FAILED = "com.blurr.voice.WAKE_WORD_FAILED"
        const val ACTION_PURCHASE_UPDATED = "com.blurr.voice.PURCHASE_UPDATED"
    }
    
    private val wakeWordFailureReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_WAKE_WORD_FAILED) {
                Logger.d("MainActivity", "Received wake word failure broadcast.")
                // The service stops itself, but we should refresh the UI state
                updateUI()
                showWakeWordFailureDialog()
            }
        }
    }
    
    private val purchaseUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_PURCHASE_UPDATED) {
                Logger.d("MainActivity", "Received purchase update broadcast.")
                // Refresh billing status
                showLoading(true)
                performBillingCheck()
            }
        }
    }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                Toast.makeText(this, "Permission granted!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Permission denied.", Toast.LENGTH_SHORT).show()
            }
        }




    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth = Firebase.auth
        val currentUser = auth.currentUser
        val profileManager = UserProfileManager(this)

        // --- UNIFIED AUTHENTICATION & PROFILE CHECK ---
        // We check both conditions at once. If the user is either not logged in
        // OR their profile is incomplete, we send them to the LoginActivity.
        if (currentUser == null || !profileManager.isProfileComplete()) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish() // Destroy MainActivity
            return   // Stop executing any more code in this method
        }
        onboardingManager = OnboardingManager(this)
        if (!onboardingManager.isOnboardingCompleted()) {
            Logger.d("MainActivity", "User is logged in but onboarding not completed. Relaunching permissions stepper.")
            startActivity(Intent(this, OnboardingPermissionsActivity::class.java))
            finish()
            return
        }

//        val roleManager = getSystemService(RoleManager::class.java)
//        if (roleManager?.isRoleAvailable(RoleManager.ROLE_ASSISTANT) == true &&
//            !roleManager.isRoleHeld(RoleManager.ROLE_ASSISTANT)) {
//            val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT)
//            startActivityForResult(intent, 1001)
//        } else {
//            // Fallbacks if the role UI isn’t available
//            val intents = listOf(
//                Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS),
//                Intent(Settings.ACTION_VOICE_INPUT_SETTINGS)
//            )
//            for (i in intents) if (i.resolveActivity(packageManager) != null) {
//                startActivity(i); break
//            }
//        }

        requestRoleLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                Toast.makeText(this, "Set as default assistant successfully!", Toast.LENGTH_SHORT).show()
            } else {
                // Explain and offer Settings
                Toast.makeText(this, "Couldn’t become default assistant. Opening settings…", Toast.LENGTH_SHORT).show()
                Logger.w("MainActivity", "Role request canceled or app not eligible.\n${explainAssistantEligibility()}")
                openAssistantPickerSettings()
            }
            showAssistantStatus(true)
        }


        setContentView(R.layout.activity_main_content)
        // existing click listener
        findViewById<TextView>(R.id.btn_set_default_assistant).setOnClickListener {
            startActivity(Intent(this, RoleRequestActivity::class.java))
        }

        // show/hide based on current status
        updateDefaultAssistantButtonVisibility()

        handleIntent(intent)
        managePermissionsButton = findViewById(R.id.btn_manage_permissions) // ADDED

        val userIdManager = UserIdManager(applicationContext)
        userId = userIdManager.getOrCreateUserId()
        increaseLimitsLink = findViewById(R.id.increase_limits_link) // ADDED

        permissionManager = PermissionManager(this)
        permissionManager.initializePermissionLauncher()

        // Initialize UI components
        managePermissionsButton = findViewById(R.id.btn_manage_permissions)
        runExampleButton = findViewById(R.id.run_example_button)

        tvPermissionStatus = findViewById(R.id.tv_permission_status)
        settingsButton = findViewById(R.id.settingsButton)
        wakeWordHelpLink = findViewById(R.id.wakeWordHelpLink)

        saveKeyButton = findViewById(R.id.saveKeyButton)
        tasksRemainingTextView = findViewById(R.id.tasks_remaining_textview)
        billingStatusTextView = findViewById(R.id.billing_status_textview)
        loadingOverlay = findViewById(R.id.loading_overlay)
        freemiumManager = FreemiumManager()
        // Initialize managers
        wakeWordManager = WakeWordManager(this, requestPermissionLauncher)
        handler = Handler(Looper.getMainLooper())


        // Setup UI and listeners
        setupClickListeners()
        setupSettingsButton()
        setupGradientText()

        
        // Show loading and perform initial billing check
        showLoading(true)
        performBillingCheck()
        
        lifecycleScope.launch {
            val videoUrl = "https://storage.googleapis.com/blurr-app-assets/wake_word_demo.mp4"
            VideoAssetManager.getVideoFile(this@MainActivity, videoUrl)
        }

    }

    private fun openAssistantPickerSettings() {
        // Try the dedicated assistant settings screen first
        val specifics = listOf(
            Intent("android.settings.VOICE_INPUT_SETTINGS"),
            Intent(Settings.ACTION_VOICE_INPUT_SETTINGS),
            Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
        )
        for (i in specifics) {
            if (i.resolveActivity(packageManager) != null) {
                startActivity(i); return
            }
        }
        Toast.makeText(this, "Assistant settings not available on this device.", Toast.LENGTH_SHORT).show()
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    fun showAssistantStatus(toast: Boolean = false) {
        val rm = getSystemService(RoleManager::class.java)
        val held = rm?.isRoleHeld(RoleManager.ROLE_ASSISTANT) == true
        val msg = if (held) "This app is the default assistant." else "This app is NOT the default assistant."
        if (toast) Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        Logger.d("MainActivity", msg)
    }

    private fun explainAssistantEligibility(): String {
        val pm = packageManager
        val pkg = packageName

        // Does my app have an activity that handles ACTION_ASSIST?
        val assistIntent = Intent(Intent.ACTION_ASSIST).setPackage(pkg)
        val assistActivities = pm.queryIntentActivities(assistIntent, 0)

        // Does my app declare a VoiceInteractionService? (Most third-party apps won't)
        val visIntent = Intent("android.service.voice.VoiceInteractionService").setPackage(pkg)
        val visServices = pm.queryIntentServices(visIntent, 0)

        return buildString {
            append("Assistant eligibility:\n")
            append("• ACTION_ASSIST activity: ${if (assistActivities.isNotEmpty()) "FOUND" else "NOT FOUND"}\n")
            append("• VoiceInteractionService: ${if (visServices.isNotEmpty()) "FOUND" else "NOT FOUND"}\n")
            append("Note: Many OEMs only list apps with a VoiceInteractionService as selectable assistants.\n")
        }
    }

    override fun onStart() {
        super.onStart()
        // It's good practice to re-check authentication in onStart as well.
        if (auth.currentUser == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }
        
        // Show loading and perform billing check when starting
        showLoading(true)
        performBillingCheck()
    }
//    private fun signOut() {
//        auth.signOut()
//        // Optional: Also sign out from the Google account on the device
//        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).build()
//        val googleSignInClient = GoogleSignIn.getClient(this, gso)
//        googleSignInClient.signOut().addOnCompleteListener {
//            // After signing out, redirect to LoginActivity
//            startActivity(Intent(this, LoginActivity::class.java))
//            finish()
//        }
//    }
    private fun handleIntent(intent: Intent?) {
        if (intent?.action == "com.blurr.voice.WAKE_UP_PANDA") {
            Logger.d("MainActivity", "Wake up Panda shortcut activated!")
            startConversationalAgent()
        }
    }

    private fun startConversationalAgent() {
        if (!ConversationalAgentService.isRunning) {
            val serviceIntent = Intent(this, ConversationalAgentService::class.java)
            ContextCompat.startForegroundService(this, serviceIntent)
            Toast.makeText(this, "Panda is waking up...", Toast.LENGTH_SHORT).show()
        } else {
            Logger.d("MainActivity", "ConversationalAgentService is already running.")
            Toast.makeText(this, "Panda is already awake!", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun getContentLayoutId(): Int = R.layout.activity_main_content
    
    override fun getCurrentNavItem(): BaseNavigationActivity.NavItem = BaseNavigationActivity.NavItem.HOME

    private fun setupClickListeners() {
        findViewById<TextView>(R.id.startConversationButton).setOnClickListener {
            startConversationalAgent()
        }
//        findViewById<TextView>(R.id.memoriesButton).setOnClickListener {
//            startActivity(Intent(this, MemoriesActivity::class.java))
//        }

        saveKeyButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        managePermissionsButton.setOnClickListener {
            startActivity(Intent(this, PermissionsActivity::class.java))
        }
        increaseLimitsLink.setOnClickListener {
            requestLimitIncrease()
        }

        wakeWordHelpLink.setOnClickListener {
            showWakeWordFailureDialog()
        }
        findViewById<TextView>(R.id.disclaimer_link).setOnClickListener {
            showDisclaimerDialog()
        }
        runExampleButton.setOnClickListener {
            val task = "open youtube and play never gonna give you up"
            AgentService.start(this, task)
        }
    }

    private fun setupSettingsButton() {
        settingsButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }
    private fun requestLimitIncrease() {
        val userEmail = auth.currentUser?.email
        if (userEmail.isNullOrEmpty()) {
            Toast.makeText(this, "Could not get your email. Please try again.", Toast.LENGTH_SHORT).show()
            return
        }

        val recipient = "ayush0000ayush@gmail.com"
        val subject = "I am facing issue in"
        val body = "Hello,\n\nI am facing issue for my account: $userEmail\n <issue-content>.... \n\nThank you."

        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:") // Only email apps should handle this
            putExtra(Intent.EXTRA_EMAIL, arrayOf(recipient))
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
        }

        // Verify that the intent will resolve to an activity
        if (intent.resolveActivity(packageManager) != null) {
            startActivity(intent)
        } else {
            Toast.makeText(this, "No email application found.", Toast.LENGTH_SHORT).show()
        }
    }
    private fun setupGradientText() {
        val karanTextView = findViewById<TextView>(R.id.karan_textview_gradient)
        karanTextView.measure(0, 0)
        val textShader: Shader = LinearGradient(
            0f, 0f, karanTextView.measuredWidth.toFloat(), 0f,
            intArrayOf("#BE63F3".toColorInt(), "#5880F7".toColorInt()),
            null, Shader.TileMode.CLAMP
        )
        karanTextView.paint.shader = textShader
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onResume() {
        super.onResume()
        
        // Show loading and perform billing check
        showLoading(true)
        performBillingCheck()
        displayDeveloperMessage()
        updateUI()
        
        // Register broadcast receivers
        val wakeWordFilter = IntentFilter(ACTION_WAKE_WORD_FAILED)
        val purchaseFilter = IntentFilter(ACTION_PURCHASE_UPDATED)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(wakeWordFailureReceiver, wakeWordFilter, RECEIVER_NOT_EXPORTED)
            registerReceiver(purchaseUpdateReceiver, purchaseFilter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(wakeWordFailureReceiver, wakeWordFilter)
            registerReceiver(purchaseUpdateReceiver, purchaseFilter)
        }
    }
    override fun onPause() {
        super.onPause()
        // Unregister the BroadcastReceivers to avoid leaks
        try {
            unregisterReceiver(wakeWordFailureReceiver)
            unregisterReceiver(purchaseUpdateReceiver)
        } catch (e: IllegalArgumentException) {
            // Receivers might not be registered, ignore
            Logger.d("MainActivity", "Receivers were not registered")
        }
    }

    private fun showDisclaimerDialog() {
        val dialog = AlertDialog.Builder(this)
            .setTitle("Disclaimer")
            .setMessage("Panda is an experimental AI assistant and is still in development. It may not always be accurate or perform as expected. It does small task better. Your understanding is appreciated!")
            .setPositiveButton("Okay") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
        
        // Set the button text color to white
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(
            ContextCompat.getColor(this, R.color.white)
        )
    }


    private fun showWakeWordFailureDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_wake_word_failure, null)
        val videoView = dialogView.findViewById<VideoView>(R.id.video_demo)
        val videoContainer = dialogView.findViewById<View>(R.id.video_container_card)

        val builder = AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton("Got it") { dialog, _ ->
                dialog.dismiss()
            }

        val alertDialog = builder.create()

        // Use a coroutine to get the file, as it might trigger a download
        lifecycleScope.launch {
            val videoUrl = "https://storage.googleapis.com/blurr-app-assets/wake_word_demo.mp4"
            val videoFile: File? = VideoAssetManager.getVideoFile(this@MainActivity, videoUrl)

            if (videoFile != null && videoFile.exists()) {
                videoContainer.visibility = View.VISIBLE
                videoView.setVideoURI(Uri.fromFile(videoFile))
                videoView.setOnPreparedListener { mp ->
                    mp.isLooping = true
                }
                alertDialog.setOnShowListener {
                    videoView.start()
                }
            } else {
                // If file doesn't exist (e.g., download failed), hide the video player
                Logger.e("MainActivity", "Video file not found, hiding video container.")
                videoContainer.visibility = View.GONE
            }
        }

        alertDialog.show()
        
        // Set the button text color to white
        alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(
            ContextCompat.getColor(this, R.color.white)
        )
    }
    private fun updateTaskCounter() {
        lifecycleScope.launch {
            val tasksLeft = freemiumManager.getTasksRemaining()

            if (tasksLeft == Long.MAX_VALUE) {
                tasksRemainingTextView.visibility = View.GONE
                increaseLimitsLink.visibility = View.GONE

            } else if (tasksLeft != null && tasksLeft >= 0) {
                if (tasksLeft > 0) {
                    tasksRemainingTextView.text = "You have $tasksLeft free tasks remaining today."
                } else {
                    tasksRemainingTextView.text = "You have 0 free tasks left for today."
                }
                tasksRemainingTextView.visibility = View.VISIBLE
                increaseLimitsLink.visibility = View.VISIBLE

            } else {
                tasksRemainingTextView.visibility = View.GONE
                increaseLimitsLink.visibility = View.VISIBLE
            }
        }
    }

    private fun updateBillingStatus() {
        lifecycleScope.launch {
            try {
                val isSubscribed = freemiumManager.isUserSubscribed()
                val billingClientReady = MyApplication.isBillingClientReady.value
                
                when {
                    !billingClientReady -> {
                        billingStatusTextView.text = "Billing: Connecting..."
                        billingStatusTextView.setTextColor(Color.parseColor("#FF9800")) // Orange
                        billingStatusTextView.visibility = View.VISIBLE
                    }
                    isSubscribed -> {
                        billingStatusTextView.text = "✓ Pro Subscription Active"
                        billingStatusTextView.setTextColor(Color.parseColor("#4CAF50")) // Green
                        billingStatusTextView.visibility = View.VISIBLE
                    }
                    else -> {
                        billingStatusTextView.text = "Free Plan"
                        billingStatusTextView.setTextColor(Color.parseColor("#757575")) // Gray
                        billingStatusTextView.visibility = View.VISIBLE
                    }
                }
            } catch (e: Exception) {
                Logger.e("MainActivity", "Error updating billing status", e)
                billingStatusTextView.text = "Billing: Error"
                billingStatusTextView.setTextColor(Color.parseColor("#F44336")) // Red
                billingStatusTextView.visibility = View.VISIBLE
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun updateUI() {
        val allPermissionsGranted = permissionManager.areAllPermissionsGranted()
        if (allPermissionsGranted) {
            tvPermissionStatus.text = "All required permissions are granted."
            managePermissionsButton.visibility = View.GONE
            runExampleButton.visibility = View.VISIBLE
            tvPermissionStatus.setTextColor(Color.parseColor("#4CAF50")) // Green
        } else {
            tvPermissionStatus.text = "Some permissions are missing. Tap below to manage."
            runExampleButton.visibility = View.GONE
            tvPermissionStatus.setTextColor(Color.parseColor("#F44336")) // Red
        }
    }

    private fun isThisAppDefaultAssistant(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val rm = getSystemService(RoleManager::class.java)
            rm?.isRoleHeld(RoleManager.ROLE_ASSISTANT) == true
        } else {
            // Pre-Q best-effort: check the current VoiceInteractionService owner
            val flat = Settings.Secure.getString(contentResolver, "voice_interaction_service")
            val currentPkg = flat?.substringBefore('/')
            currentPkg == packageName
        }
    }

    private fun updateDefaultAssistantButtonVisibility() {
        val btn = findViewById<TextView>(R.id.btn_set_default_assistant)
        btn.visibility = if (isThisAppDefaultAssistant()) View.GONE else View.VISIBLE
    }

    private fun showLoading(show: Boolean) {
        loadingOverlay.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun performBillingCheck() {
        lifecycleScope.launch {
            try {
                // Wait for billing client to be ready
                waitForBillingClientReady()
                
                // Query purchases and handle them
                queryAndHandlePurchases()
                
                // Update UI with current status
                updateTaskCounter()
                updateBillingStatus()
                
            } catch (e: Exception) {
                Logger.e("MainActivity", "Error during billing check", e)
                updateTaskCounter()
                updateBillingStatus()
            } finally {
                showLoading(false)
            }
        }
    }

    private suspend fun waitForBillingClientReady() {
        return withContext(Dispatchers.IO) {
            var attempts = 0
            val maxAttempts = 10
            
            while (!MyApplication.isBillingClientReady.value && attempts < maxAttempts) {
                kotlinx.coroutines.delay(500)
                attempts++
            }
            
            if (!MyApplication.isBillingClientReady.value) {
                Logger.w("MainActivity", "Billing client not ready after waiting")
            }
        }
    }

    private suspend fun queryAndHandlePurchases() {
        return withContext(Dispatchers.IO) {
            if (!MyApplication.isBillingClientReady.value) {
                Logger.e("MainActivity", "queryPurchases: BillingClient is not ready")
                //tvPermissionStatus.text = "queryPurchases: BillingClient is not ready"

                return@withContext
            }

            try {
                val params = QueryPurchasesParams.newBuilder()
                    .setProductType(BillingClient.ProductType.SUBS)
                    .build()
                
                Logger.d("MainActivity", "queryPurchases: BillingClient is ready")
                //tvPermissionStatus.text = "queryPurchases: BillingClient is ready"

                val purchasesResult = MyApplication.billingClient.queryPurchasesAsync(params)
                val billingResult = purchasesResult.billingResult
                
                Logger.d("MainActivity", "queryPurchases: Got billing result: ${billingResult.responseCode}")
                //tvPermissionStatus.text = "queryPurchases: Got billing result: ${billingResult.responseCode}"

                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    Logger.d("MainActivity", "queryPurchases: Found ${purchasesResult.purchasesList.size} purchases")
                    //tvPermissionStatus.text = "queryPurchases: Found ${purchasesResult.purchasesList.size} purchases"

                    purchasesResult.purchasesList.forEach { purchase ->
                        when (purchase.purchaseState) {
                            Purchase.PurchaseState.PURCHASED -> {
                                Logger.d("MainActivity", "Found purchased item: ${purchase.products}")
                                //tvPermissionStatus.text = "Found purchased item: ${purchase.products}"

                                handlePurchase(purchase)
                            }
                            Purchase.PurchaseState.PENDING -> {
                                Logger.d("MainActivity", "Purchase is pending")
                                //tvPermissionStatus.text = "Purchase is pending"
                            }
                            else -> {
                                Logger.d("MainActivity", "Purchase is not in a valid state: ${purchase.purchaseState}")
                                //tvPermissionStatus.text = "Purchase is not in a valid state: ${purchase.purchaseState}"
                            }
                        }
                    }
                } else {
                    Logger.e("MainActivity", "Failed to query purchases: ${billingResult.debugMessage}")
                }
            } catch (e: Exception) {
                Logger.e("MainActivity", "Exception during purchase query", e)
            }
        }
    }

    private suspend fun handlePurchase(purchase: Purchase) {
        return withContext(Dispatchers.IO) {
            try {
                if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                    if (!purchase.isAcknowledged) {
                        val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
                            .setPurchaseToken(purchase.purchaseToken)
                            .build()
                        
                        MyApplication.billingClient.acknowledgePurchase(acknowledgePurchaseParams) { billingResult ->
                            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                                Logger.d("MainActivity", "Purchase acknowledged: ${purchase.orderId}")
                                //tvPermissionStatus.text = "Purchase acknowledged: ${purchase.orderId}"

                                lifecycleScope.launch {
                                    updateUserToPro()
                                }
                            } else {
                                Logger.e("MainActivity", "Failed to acknowledge purchase: ${billingResult.debugMessage}")
                            }
                        }
                    } else {
                        // Purchase already acknowledged, ensure backend is updated.
                        updateUserToPro()
                    }
                }
            } catch (e: Exception) {
                Logger.e("MainActivity", "Error handling purchase", e)
            }
        }
    }


    private suspend fun updateUserToPro() { // The 'email' parameter is no longer needed
        // Get the current user's UID directly from Firebase Auth.
        val uid = Firebase.auth.currentUser?.uid

        // If the user isn't logged in, we can't proceed.
        if (uid == null) {
            Logger.e("MainActivity", "Cannot update user to pro: user is not authenticated.")
            // Switch to the Main thread to safely update the UI.
            withContext(Dispatchers.Main) {
                //tvPermissionStatus.text = "Error: You are not signed in."
            }
            return // Exit the function
        }

        // Perform the database operation on a background thread.
        withContext(Dispatchers.IO) {
            val db = Firebase.firestore
            try {
                // Create a direct reference to the user's document using their UID.
                val userDocRef = db.collection("users").document(uid)

                // Update the 'plan' field directly on that document.
                userDocRef.update("plan", "pro").await()

                Logger.d("MainActivity", "Successfully updated user $uid to 'pro' plan.")

                // Switch back to the Main thread to safely update the UI.
                withContext(Dispatchers.Main) {
                    //tvPermissionStatus.text = "Successfully upgraded to Pro!"
                }

            } catch (e: Exception) {
                Logger.e("MainActivity", "Error updating user to pro", e)

                // Switch back to the Main thread to show an error message.
                withContext(Dispatchers.Main) {
                    ////tvPermissionStatus.text = "Error: Could not upgrade plan."
                }
            }
        }
    }

    private fun displayDeveloperMessage() {
        lifecycleScope.launch {
            try {
                val db = Firebase.firestore
                val docRef = db.collection("settings").document("freemium")
                
                docRef.get().addOnSuccessListener { document ->
                    if (document != null && document.exists()) {
                        val message = document.getString("developerMessage")
                        if (!message.isNullOrEmpty()) {
                            val developerMessageTextView = findViewById<TextView>(R.id.developer_message_textview)
                            developerMessageTextView.text = message
                            developerMessageTextView.visibility = View.VISIBLE
                            Logger.d("MainActivity", "Developer message displayed: $message")
                        } else {
                            Logger.d("MainActivity", "Developer message is empty")
                        }
                    } else {
                        Logger.d("MainActivity", "Developer message document does not exist")
                    }
                }.addOnFailureListener { exception ->
                    Logger.e("MainActivity", "Error fetching developer message", exception)
                }
            } catch (e: Exception) {
                Logger.e("MainActivity", "Exception in displayDeveloperMessage", e)
            }
        }
    }

}
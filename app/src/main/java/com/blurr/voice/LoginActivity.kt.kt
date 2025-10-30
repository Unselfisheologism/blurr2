package com.blurr.voice

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button // Changed from SignInButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.EditText
import android.widget.Toast
import android.graphics.Color
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.blurr.voice.utilities.FreemiumManager
import com.blurr.voice.utilities.OnboardingManager
import com.blurr.voice.utilities.UserProfileManager
import androidx.appcompat.app.AppCompatActivity  
import androidx.lifecycle.lifecycleScope  
import com.blurr.voice.utilities.OnboardingManager  
import com.google.android.material.button.MaterialButton  
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var puterSignInButton: MaterialButton

    // New ActivityResultLauncher for the modern Identity API
    private lateinit var googleSignInLauncher: ActivityResultLauncher<IntentSenderRequest>

    override fun onCreate(savedInstanceState: Bundle?) {  
        super.onCreate(savedInstanceState)  
        setContentView(R.layout.activity_onboarding)  
  
        // Initialize only the Puter sign-in button  
        puterSignInButton = findViewById(R.id.puterSignInButton)  
        progressBar = findViewById(R.id.progressBar)  
        loadingText = findViewById(R.id.loadingText)  
      
        onboardingManager = OnboardingManager(this) 
      
        // Set up Puter sign-in button click listener  
        puterSignInButton.setOnClickListener {  
            // Launch PuterAuthActivity which handles Chrome Custom Tabs auth
            startActivity(Intent(this, PuterAuthActivity::class.java)) 
        }  
    }

    fun onPuterAuthSuccess() {  
        runOnUiThread {  
            progressBar.visibility = View.GONE  
            loadingText.visibility = View.GONE  
          
            // Check if onboarding is completed  
            if (onboardingManager.isOnboardingCompleted()) {  
                startActivity(Intent(this, MainActivity::class.java))  
            } else {  
                startActivity(Intent(this, OnboardingPermissionsActivity::class.java))  
            }  
            finish()  
        } 
    }  
}
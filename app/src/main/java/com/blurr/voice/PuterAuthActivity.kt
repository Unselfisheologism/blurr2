package com.blurr.voice  
  
import android.content.Intent  
import android.net.Uri  
import android.os.Bundle  
import android.util.Log  
import android.widget.Toast  
import androidx.appcompat.app.AppCompatActivity  
import androidx.browser.customtabs.CustomTabsIntent  
import com.blurr.voice.utilities.Logger  
  
/**  
 * Activity that handles Puter authentication via Chrome Custom Tabs.  
 *   
 * This activity launches Chrome Custom Tabs to display Puter's authentication page,  
 * then receives the OAuth callback via deep link when authentication completes.  
 */  
class PuterAuthActivity : AppCompatActivity() {  
  
    companion object {  
        private const val TAG = "PuterAuthActivity"  
          
        // Puter authentication URL - this will load the Puter.js auth page  
        private const val PUTER_AUTH_URL = "https://puter.com"  
          
        // Deep link callback URL that Puter will redirect to after auth  
        private const val REDIRECT_URI = "com.blurr.voice://puter-callback"  
          
        // SharedPreferences keys for storing auth token  
        private const val PREFS_NAME = "puter_auth"  
        private const val KEY_AUTH_TOKEN = "auth_token"  
        private const val KEY_USER_UUID = "user_uuid"  
        private const val KEY_USERNAME = "username"  
        private const val KEY_EMAIL_CONFIRMED = "email_confirmed"  
    }  
  
    override fun onCreate(savedInstanceState: Bundle?) {  
        super.onCreate(savedInstanceState)  
          
        // Check if this is a callback from Puter authentication  
        val data: Uri? = intent?.data  
          
        if (data != null && data.scheme == "com.blurr.voice" && data.host == "puter-callback") {  
            // This is the OAuth callback - handle it  
            handlePuterCallback(data)  
        } else {  
            // This is the initial launch - start authentication flow  
            launchPuterAuth()  
        }  
    }  
  
    override fun onNewIntent(intent: Intent) {  
        super.onNewIntent(intent)  
        setIntent(intent)  
          
        val data: Uri? = intent.data  
        if (data != null) {  
            handlePuterCallback(data)  
        }  
    }  
  
    /**  
     * Launches Chrome Custom Tabs with Puter authentication page.  
     * The WebView in MainActivity will handle the actual puter.auth.signIn() call.  
     */  
    private fun launchPuterAuth() {  
        try {  
            Logger.d(TAG, "Launching Puter auth via Chrome Custom Tabs")  
              
            // Get MainActivity instance to trigger WebView authentication  
            val mainActivity = (application as? MyApplication)?.mainActivityReference?.get()  
              
            if (mainActivity != null) {  
                // Trigger the WebView-based authentication in MainActivity  
                mainActivity.signInWithPuter()  
                  
                // Close this activity since authentication is handled by MainActivity's WebView  
                finish()  
            } else {  
                // MainActivity not available - show error  
                Toast.makeText(  
                    this,  
                    "Please wait for app to initialize",  
                    Toast.LENGTH_SHORT  
                ).show()  
                finish()  
            }  
        } catch (e: Exception) {  
            Logger.e(TAG, "Error launching Puter auth", e)  
            Toast.makeText(  
                this,  
                "Failed to start authentication: ${e.message}",  
                Toast.LENGTH_LONG  
            ).show()  
            finish()  
        }  
    }  
  
    /**  
     * Handles the OAuth callback from Puter after successful authentication.  
     * Extracts the auth token and user info from the callback URL.  
     */  
    private fun handlePuterCallback(data: Uri) {  
        try {  
            Logger.d(TAG, "Handling Puter callback: $data")  
              
            // Check for error parameter  
            val error = data.getQueryParameter("error")  
            if (error != null) {  
                val errorDescription = data.getQueryParameter("error_description") ?: "Unknown error"  
                Logger.e(TAG, "Puter auth error: $error - $errorDescription")  
                Toast.makeText(  
                    this,  
                    "Authentication failed: $errorDescription",  
                    Toast.LENGTH_LONG  
                ).show()  
                finish()  
                return  
            }  
              
            // Extract auth token from callback URL  
            // Note: The actual parameter names depend on Puter's OAuth implementation  
            // This might be "token", "access_token", or something else  
            val token = data.getQueryParameter("token")   
                ?: data.getQueryParameter("access_token")  
                ?: data.fragment?.let { fragment ->  
                    // Sometimes OAuth returns token in URL fragment  
                    fragment.split("&")  
                        .map { it.split("=") }  
                        .firstOrNull { it[0] == "token" || it[0] == "access_token" }  
                        ?.getOrNull(1)  
                }  
              
            if (token != null) {  
                Logger.d(TAG, "Successfully received Puter auth token")  
                  
                // Extract user info if available  
                val uuid = data.getQueryParameter("uuid") ?: ""  
                val username = data.getQueryParameter("username") ?: ""  
                val emailConfirmed = data.getQueryParameter("email_confirmed")?.toBoolean() ?: false  
                  
                // Store auth data  
                savePuterAuthData(token, uuid, username, emailConfirmed)  
                  
                // Notify MainActivity about successful authentication  
                val mainActivity = (application as? MyApplication)?.mainActivityReference?.get()  
                mainActivity?.runOnUiThread {  
                    Toast.makeText(  
                        mainActivity,  
                        "Signed in successfully",  
                        Toast.LENGTH_SHORT  
                    ).show()  
                }  
                  
                // Navigate to MainActivity  
                val intent = Intent(this, MainActivity::class.java)  
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK  
                startActivity(intent)  
                finish()  
            } else {  
                Logger.e(TAG, "No auth token found in callback URL")  
                Toast.makeText(  
                    this,  
                    "Authentication failed: No token received",  
                    Toast.LENGTH_LONG  
                ).show()  
                finish()  
            }  
        } catch (e: Exception) {  
            Logger.e(TAG, "Error handling Puter callback", e)  
            Toast.makeText(  
                this,  
                "Authentication error: ${e.message}",  
                Toast.LENGTH_LONG  
            ).show()  
            finish()  
        }  
    }  
  
    /**  
     * Saves Puter authentication data to SharedPreferences  
     */  
    private fun savePuterAuthData(token: String, uuid: String, username: String, emailConfirmed: Boolean) {  
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)  
        prefs.edit().apply {  
            putString(KEY_AUTH_TOKEN, token)  
            putString(KEY_USER_UUID, uuid)  
            putString(KEY_USERNAME, username)  
            putBoolean(KEY_EMAIL_CONFIRMED, emailConfirmed)  
            apply()  
        }  
        Logger.d(TAG, "Puter auth data saved to SharedPreferences")  
    }  
  
    companion object {  
        /**  
         * Retrieves the stored Puter auth token  
         */  
        fun getPuterToken(context: android.content.Context): String? {  
            val prefs = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)  
            return prefs.getString(KEY_AUTH_TOKEN, null)  
        }  
          
        /**  
         * Retrieves stored Puter user data  
         */  
        fun getPuterUserData(context: android.content.Context): Map<String, Any>? {  
            val prefs = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)  
            val token = prefs.getString(KEY_AUTH_TOKEN, null) ?: return null  
              
            return mapOf(  
                "token" to token,  
                "uuid" to (prefs.getString(KEY_USER_UUID, "") ?: ""),  
                "username" to (prefs.getString(KEY_USERNAME, "") ?: ""),  
                "email_confirmed" to prefs.getBoolean(KEY_EMAIL_CONFIRMED, false)  
            )  
        }  
          
        /**  
         * Checks if user is authenticated with Puter  
         */  
        fun isPuterAuthenticated(context: android.content.Context): Boolean {  
            return getPuterToken(context) != null  
        }  
          
        /**  
         * Clears Puter authentication data (for logout)  
         */  
        fun clearPuterAuth(context: android.content.Context) {  
            val prefs = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)  
            prefs.edit().clear().apply()  
            Logger.d(TAG, "Puter auth data cleared")  
        }  
    }  
}
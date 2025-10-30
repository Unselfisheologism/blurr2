package com.blurr.voice  
  
import android.content.Context  
import android.util.Log  
import android.webkit.JavascriptInterface  
import android.widget.Toast  
import kotlinx.coroutines.CoroutineScope  
import kotlinx.coroutines.Dispatchers  
import kotlinx.coroutines.launch  
import org.json.JSONObject  
  
/**  
 * JavaScript bridge for communication between WebView (Puter.js) and native Android code.  
 *   
 * This class exposes methods to JavaScript that can be called from the puter_interface.html  
 * file. All methods must be annotated with @JavascriptInterface to be accessible from JavaScript.  
 */  
class PuterWebViewBridge(  
    private val context: Context,  
    private val callback: PuterBridgeCallback  
) {  
    companion object {  
        private const val TAG = "PuterWebViewBridge"  
        private const val PREFS_NAME = "puter_auth"  
        private const val KEY_USER_DATA = "user_data"  
        private const val KEY_IS_AUTHENTICATED = "is_authenticated"  
    }  
  
    /**  
     * Called when the Puter.js interface is fully loaded and ready  
     */  
    @JavascriptInterface  
    fun onPuterInterfaceReady() {  
        Log.d(TAG, "Puter.js interface is ready")  
        CoroutineScope(Dispatchers.Main).launch {  
            callback.onPuterReady()  
        }  
    }  
  
    /**  
     * Called when Puter authentication succeeds  
     * @param userJson JSON string containing user information (uuid, username, email_confirmed)  
     */  
    @JavascriptInterface  
    fun onPuterAuthSuccess(userJson: String) {  
        Log.d(TAG, "Puter auth success: $userJson")  
          
        try {  
            val userObject = JSONObject(userJson)  
            val uuid = userObject.getString("uuid")  
            val username = userObject.getString("username")  
            val emailConfirmed = userObject.getBoolean("email_confirmed")  
              
            // Store user data in SharedPreferences  
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)  
            prefs.edit().apply {  
                putString(KEY_USER_DATA, userJson)  
                putBoolean(KEY_IS_AUTHENTICATED, true)  
                apply()  
            }  
              
            Log.d(TAG, "Stored Puter user: $username (UUID: $uuid)")  
              
            CoroutineScope(Dispatchers.Main).launch {  
                callback.onAuthSuccess(uuid, username, emailConfirmed)  
                Toast.makeText(context, "Signed in as $username", Toast.LENGTH_SHORT).show()  
            }  
        } catch (e: Exception) {  
            Log.e(TAG, "Error parsing user JSON", e)  
            onPuterAuthError("Failed to parse user data: ${e.message}")  
        }  
    }  
  
    /**  
     * Called when Puter authentication fails  
     * @param errorMessage Error message from Puter.js  
     */  
    @JavascriptInterface  
    fun onPuterAuthError(errorMessage: String) {  
        Log.e(TAG, "Puter auth error: $errorMessage")  
          
        CoroutineScope(Dispatchers.Main).launch {  
            callback.onAuthError(errorMessage)  
            Toast.makeText(context, "Authentication failed: $errorMessage", Toast.LENGTH_LONG).show()  
        }  
    }  
  
    /**  
     * Called when user signs out from Puter  
     */  
    @JavascriptInterface  
    fun onPuterSignedOut() {  
        Log.d(TAG, "User signed out from Puter")  
          
        // Clear stored user data  
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)  
        prefs.edit().clear().apply()  
          
        CoroutineScope(Dispatchers.Main).launch {  
            callback.onSignedOut()  
            Toast.makeText(context, "Signed out from Puter", Toast.LENGTH_SHORT).show()  
        }  
    }  
  
    /**  
     * Called when auth status check completes  
     * @param isSignedIn Whether user is currently signed in  
     * @param userJson JSON string containing user info (null if not signed in)  
     */  
    @JavascriptInterface  
    fun onAuthStatusChecked(isSignedIn: Boolean, userJson: String?) {  
        Log.d(TAG, "Auth status checked: isSignedIn=$isSignedIn")  
          
        if (isSignedIn && userJson != null) {  
            try {  
                val userObject = JSONObject(userJson)  
                val uuid = userObject.getString("uuid")  
                val username = userObject.getString("username")  
                val emailConfirmed = userObject.getBoolean("email_confirmed")  
                  
                // Update stored user data  
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)  
                prefs.edit().apply {  
                    putString(KEY_USER_DATA, userJson)  
                    putBoolean(KEY_IS_AUTHENTICATED, true)  
                    apply()  
                }  
                  
                CoroutineScope(Dispatchers.Main).launch {  
                    callback.onAuthStatusChecked(true, uuid, username, emailConfirmed)  
                }  
            } catch (e: Exception) {  
                Log.e(TAG, "Error parsing user JSON in status check", e)  
                CoroutineScope(Dispatchers.Main).launch {  
                    callback.onAuthStatusChecked(false, null, null, false)  
                }  
            }  
        } else {  
            // Clear stored data if not signed in  
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)  
            prefs.edit().clear().apply()  
              
            CoroutineScope(Dispatchers.Main).launch {  
                callback.onAuthStatusChecked(false, null, null, false)  
            }  
        }  
    }  
  
    /**  
     * Called when AI chat response is received  
     * @param response The AI's response text  
     */  
    @JavascriptInterface  
    fun onAIChatResponse(response: String) {  
        Log.d(TAG, "AI chat response received: ${response.take(100)}...")  
          
        CoroutineScope(Dispatchers.Main).launch {  
            callback.onAIChatResponse(response)  
        }  
    }  
  
    /**  
     * Called when AI chat request fails  
     * @param errorMessage Error message from Puter.js  
     */  
    @JavascriptInterface  
    fun onAIChatError(errorMessage: String) {  
        Log.e(TAG, "AI chat error: $errorMessage")  
          
        CoroutineScope(Dispatchers.Main).launch {  
            callback.onAIChatError(errorMessage)  
            Toast.makeText(context, "AI request failed: $errorMessage", Toast.LENGTH_LONG).show()  
        }  
    }  
  
    /**  
     * Called when image generation completes  
     * @param imageDataUrl Base64-encoded image data URL  
     */  
    @JavascriptInterface  
    fun onImageGenerated(imageDataUrl: String) {  
        Log.d(TAG, "Image generated, data URL length: ${imageDataUrl.length}")  
          
        CoroutineScope(Dispatchers.Main).launch {  
            callback.onImageGenerated(imageDataUrl)  
        }  
    }  
  
    /**  
     * Callback interface for Puter bridge events  
     * Implement this interface in your Activity/Service to handle Puter.js events  
     */  
    interface PuterBridgeCallback {  
        fun onPuterReady()  
        fun onAuthSuccess(uuid: String, username: String, emailConfirmed: Boolean)  
        fun onAuthError(errorMessage: String)  
        fun onSignedOut()  
        fun onAuthStatusChecked(isSignedIn: Boolean, uuid: String?, username: String?, emailConfirmed: Boolean)  
        fun onAIChatResponse(response: String)  
        fun onAIChatError(errorMessage: String)  
        fun onImageGenerated(imageDataUrl: String)  
    }  
  
    /**  
     * Helper methods to retrieve stored Puter user data  
     */  
    companion object {  
        fun getPuterUserData(context: Context): JSONObject? {  
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)  
            val userJson = prefs.getString(KEY_USER_DATA, null) ?: return null  
            return try {  
                JSONObject(userJson)  
            } catch (e: Exception) {  
                Log.e(TAG, "Error parsing stored user data", e)  
                null  
            }  
        }  
  
        fun isPuterAuthenticated(context: Context): Boolean {  
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)  
            return prefs.getBoolean(KEY_IS_AUTHENTICATED, false)  
        }  
  
        fun clearPuterAuth(context: Context) {  
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)  
            prefs.edit().clear().apply()  
        }  
    }  
}
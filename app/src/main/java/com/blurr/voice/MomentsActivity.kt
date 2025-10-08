package com.blurr.voice

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MomentsActivity : BaseNavigationActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_moments_content)
        
        // Setup back button
        findViewById<TextView>(R.id.back_button).setOnClickListener {
            finish()
        }
    }
    
    override fun getContentLayoutId(): Int = R.layout.activity_moments_content
    
    override fun getCurrentNavItem(): BaseNavigationActivity.NavItem = BaseNavigationActivity.NavItem.MOMENTS
}
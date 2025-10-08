package com.blurr.voice

import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity

abstract class BaseNavigationActivity : AppCompatActivity() {

    protected abstract fun getContentLayoutId(): Int
    protected abstract fun getCurrentNavItem(): NavItem

    enum class NavItem {
        HOME, TRIGGERS, MOMENTS, UPGRADE, SETTINGS
    }

    override fun setContentView(layoutResID: Int) {
        super.setContentView(R.layout.activity_base_navigation)
        
        // Inflate the child activity's content into the content container
        val contentContainer = findViewById<LinearLayout>(R.id.content_container)
        layoutInflater.inflate(layoutResID, contentContainer, true)
        
        setupBottomNavigation()
    }

    private fun setupBottomNavigation() {
        val currentItem = getCurrentNavItem()
        
        findViewById<LinearLayout>(R.id.nav_triggers).apply {
            setOnClickListener {
                if (currentItem != NavItem.TRIGGERS) {
                    startActivity(Intent(this@BaseNavigationActivity, com.blurr.voice.triggers.ui.TriggersActivity::class.java))
                    if (currentItem != NavItem.HOME) finish()
                }
            }
            alpha = if (currentItem == NavItem.TRIGGERS) 1.0f else 0.7f
        }
        
        findViewById<LinearLayout>(R.id.nav_moments).apply {
            setOnClickListener {
                if (currentItem != NavItem.MOMENTS) {
                    startActivity(Intent(this@BaseNavigationActivity, MomentsActivity::class.java))
                    if (currentItem != NavItem.HOME) finish()
                }
            }
            alpha = if (currentItem == NavItem.MOMENTS) 1.0f else 0.7f
        }
        
        findViewById<LinearLayout>(R.id.nav_home).apply {
            setOnClickListener {
                if (currentItem != NavItem.HOME) {
                    startActivity(Intent(this@BaseNavigationActivity, MainActivity::class.java))
                    finish()
                }
            }
            alpha = if (currentItem == NavItem.HOME) 1.0f else 0.7f
        }
        
        findViewById<LinearLayout>(R.id.nav_upgrade).apply {
            setOnClickListener {
                if (currentItem != NavItem.UPGRADE) {
                    startActivity(Intent(this@BaseNavigationActivity, ProPurchaseActivity::class.java))
                    if (currentItem != NavItem.HOME) finish()
                }
            }
            alpha = if (currentItem == NavItem.UPGRADE) 1.0f else 0.7f
        }
        
        findViewById<LinearLayout>(R.id.nav_settings).apply {
            setOnClickListener {
                if (currentItem != NavItem.SETTINGS) {
                    startActivity(Intent(this@BaseNavigationActivity, SettingsActivity::class.java))
                    if (currentItem != NavItem.HOME) finish()
                }
            }
            alpha = if (currentItem == NavItem.SETTINGS) 1.0f else 0.7f
        }
    }
}
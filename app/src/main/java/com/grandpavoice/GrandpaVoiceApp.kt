package com.grandpavoice

import android.app.Application
import com.grandpavoice.service.Notifications
import com.grandpavoice.settings.SettingsRepository

class GrandpaVoiceApp : Application() {
    lateinit var settings: SettingsRepository
        private set

    override fun onCreate() {
        super.onCreate()
        settings = SettingsRepository(this)
        Notifications.createChannel(this)
    }
}

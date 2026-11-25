package io.github.priyansx01.stayline

import android.app.Application
import android.content.Context
import io.github.priyansx01.stayline.data.ProfileStore
import io.github.priyansx01.stayline.data.SecretStore
import io.github.priyansx01.stayline.vpn.Notifications

class StaylineApp : Application() {
    lateinit var profiles: ProfileStore
        private set
    lateinit var secrets: SecretStore
        private set

    override fun onCreate() {
        super.onCreate()
        profiles = ProfileStore(this)
        secrets = SecretStore(this)
        Notifications.createChannels(this)
    }
}

val Context.app: StaylineApp get() = applicationContext as StaylineApp

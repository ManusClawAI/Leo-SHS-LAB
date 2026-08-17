package com.shslab.leo

import android.app.Application
import android.content.Intent
import android.os.Build
import android.provider.Settings
import com.shslab.leo.automation.SocialMediaWorker
import com.shslab.leo.connectors.ConnectorManager
import com.shslab.leo.core.Logger
import com.shslab.leo.memory.MemoryManager
import com.shslab.leo.security.SecurityManager
import com.shslab.leo.voice.PiperModelDownloader
import com.shslab.leo.voice.SherpaTtsManager
import com.shslab.leo.voice.VoskSttManager

class LeoApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // 1. Encrypted vault
        SecurityManager.init(this)

        // 2. Memory
        MemoryManager.init(this)

        // 3. Connectors
        ConnectorManager.init(this)

        // 4. Notifications
        NotificationHelper.init(this)

        // 5. Voice subsystems
        try {
            SherpaTtsManager.init(this)
            PiperModelDownloader.ensureDownloadedAsync(this)
            VoskSttManager.ensureDownloadedAsync(this)
        } catch (t: Throwable) {
            Logger.warn("[App] voice init: ${t.message}")
        }

        // 6. Background worker
        try { SocialMediaWorker.schedule(this) } catch (t: Throwable) {
            Logger.warn("[App] worker schedule: ${t.message}")
        }

        Logger.system("[App] SHS Leo online — ${MemoryManager.stats()}")
    }

    override fun onLowMemory() {
        super.onLowMemory()
        Logger.warn("[SYS] Low memory — clearing caches")
        System.gc()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_MODERATE) {
            Logger.warn("[SYS] Trim level $level — releasing")
            System.gc()
        }
    }
}

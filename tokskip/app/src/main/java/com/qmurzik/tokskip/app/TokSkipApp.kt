package com.qmurzik.tokskip.app

import android.app.Application
import com.qmurzik.tokskip.core.TokSkip

class TokSkipApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val app = TokSkip.get(this)
        XposedSync.start(this, app)
    }
}

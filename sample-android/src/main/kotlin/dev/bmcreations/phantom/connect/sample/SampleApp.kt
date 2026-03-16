package dev.bmcreations.phantom.connect.sample

import android.app.Application
import dev.bmcreations.phantom.connect.PhantomSdk

class SampleApp : Application() {
    override fun onCreate() {
        super.onCreate()
        PhantomSdk.init(this)
    }
}

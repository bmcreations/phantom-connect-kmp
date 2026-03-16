package dev.bmcreations.phantom.connect.sample

import android.app.Activity
import dev.bmcreations.phantom.connect.OAuthLauncher

fun createOAuthLauncher(activity: Activity): OAuthLauncher = MockOAuthLauncher()

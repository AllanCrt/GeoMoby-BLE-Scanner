package com.geomoby.blescanner

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application class annotated with [@HiltAndroidApp] to trigger Hilt's
 * code generation and serve as the parent component for dependency injection.
 */
@HiltAndroidApp
class BleScannerApplication : Application()

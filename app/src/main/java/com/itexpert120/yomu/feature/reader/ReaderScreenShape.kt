package com.itexpert120.yomu.feature.reader

import android.content.ComponentCallbacks
import android.content.Context
import android.content.res.Configuration
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Whether the screen is a phone held upright, which the reader's Auto layout reads in scroll mode
 * (paged everywhere else). Follows rotation and folding through the app's configuration.
 */
@Singleton
class ReaderScreenShape @Inject constructor(@ApplicationContext context: Context) {
    private val _phonePortrait = MutableStateFlow(isPhonePortrait(context.resources.configuration))
    val phonePortrait: StateFlow<Boolean> = _phonePortrait.asStateFlow()

    init {
        context.applicationContext.registerComponentCallbacks(
            object : ComponentCallbacks {
                override fun onConfigurationChanged(newConfig: Configuration) {
                    _phonePortrait.value = isPhonePortrait(newConfig)
                }

                @Deprecated("Deprecated in Java")
                override fun onLowMemory() = Unit
            },
        )
    }

    /** Called with the reader window's own configuration (it can differ in split screen). */
    fun update(configuration: Configuration) {
        _phonePortrait.value = isPhonePortrait(configuration)
    }

    internal companion object {
        /** A phone (the smaller side under 600dp, as Material's compact width) in portrait. */
        fun isPhonePortrait(configuration: Configuration): Boolean = configuration.smallestScreenWidthDp in 1 until 600 &&
            configuration.orientation == Configuration.ORIENTATION_PORTRAIT
    }
}

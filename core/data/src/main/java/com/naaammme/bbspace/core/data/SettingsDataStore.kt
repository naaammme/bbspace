package com.naaammme.bbspace.core.data

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore

internal val Context.appSettingsDataStore by preferencesDataStore("app_settings")

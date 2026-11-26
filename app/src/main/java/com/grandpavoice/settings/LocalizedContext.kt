package com.grandpavoice.settings

import android.content.Context
import android.content.res.Configuration

fun Context.forLanguage(language: AppLanguage): Context =
    createConfigurationContext(Configuration(resources.configuration).apply {
        setLocale(language.locale)
    })

package uz.robutpit.znatoki.core

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/** Язык приложения ru/uz. На API 33+ — системный LocaleManager, ниже — обёртка контекста. */
object LocaleHelper {
    private const val PREFS = "locale_prefs"

    fun saved(context: Context): String = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("lang", "") ?: ""

    fun current(context: Context): String = saved(context).ifBlank {
        val sys = context.resources.configuration.locales[0].language
        if (sys == "uz") "uz" else "ru"
    }

    fun set(context: Context, lang: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("lang", lang).commit()
        if (Build.VERSION.SDK_INT >= 33) {
            context.getSystemService(LocaleManager::class.java).applicationLocales =
                if (lang.isBlank()) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(lang)
        }
    }

    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= 33) return base
        val lang = saved(base).ifBlank { return base }
        val locale = Locale.forLanguageTag(lang)
        Locale.setDefault(locale)
        val cfg = Configuration(base.resources.configuration)
        cfg.setLocales(LocaleList(locale))
        return base.createConfigurationContext(cfg)
    }
}

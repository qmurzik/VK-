package com.qmurzik.vkenhancer.ui

import android.content.Context
import androidx.appcompat.app.AlertDialog
import com.qmurzik.vkenhancer.BuildConfig

object AboutDialog {
    fun show(context: Context) {
        val text = """
            VK Enhancer v${BuildConfig.VERSION_NAME}

            LSPosed-модуль для ВКонтакте (libxposed API 102).

            • Скрытие рекламы и рекомендаций
            • Приватность: онлайн, «прочитано», аналитика
            • Кастомные темы и цвета
            • Шрифты и интерфейс

            Работает без привязки к версии ВК: хуки опираются на
            признаки кода и контракт API, а не на имена классов.

            Автор: qmurzik
        """.trimIndent()
        AlertDialog.Builder(context)
            .setTitle("О модуле")
            .setMessage(text)
            .setPositiveButton("Закрыть", null)
            .show()
    }
}

package com.qmurzik.tokskip.core.detect

import com.qmurzik.tokskip.core.detect.Text.L
import com.qmurzik.tokskip.core.detect.Text.R

/** «Реклама», erid, ИНН, #ad, sponsored … The legal marking required for ads in Russia and elsewhere. */
class DisclosureSignal : RegexSignal(
    "disclosure", HitKind.DISCLOSURE, listOf(
        // "реклама" but not "не реклама" / "без рекламы".
        "(?<!не )(?<!без )${L}#?реклама$R" to 0.9f,
        "${L}erid$R" to 0.95f,
        "${L}инн$R ?\\d{0,12}" to 0.7f,
        "${L}#ad$R" to 0.9f,
        "${L}#ads$R" to 0.9f,
        "${L}#?sponsored$R" to 0.9f,
        "${L}#spon$R" to 0.8f,
        "${L}#партнерство$R" to 0.8f,
        "${L}#промо$R" to 0.6f,
        "платное партнерство" to 0.9f,
        "paid partnership" to 0.9f,
        "${L}рекламодатель" to 0.8f,
    )
)

/** Built-in sales phrases in Russian and English. */
class TriggerPhraseSignal : RegexSignal(
    "trigger", HitKind.TRIGGER, listOf(
        "${L}промо ?-? ?код" to 0.8f,
        "${L}по (моему |нашему )?коду$R" to 0.6f,
        "${L}use (my )?code$R" to 0.8f,
        "${L}promo ?code$R" to 0.8f,
        "${L}скидк\\p{L}* (по|в) (моей |этой )?ссылк" to 0.75f,
        "${L}по (моей |этой )?ссылке$R" to 0.6f,
        "${L}скидк\\p{L}*" to 0.3f,
        "${L}ссылк\\p{L}* (будет |оставлю |оставил )?(в|во) (шапк|профил|описани|био|комментари|закреп)" to 0.75f,
        "${L}link (is )?in (my )?(bio|description|profile)" to 0.75f,
        "${L}спасибо$R.{0,50}${L}за поддержку$R" to 0.6f,
        "${L}спонсор\\p{L}*" to 0.75f,
        "${L}при поддержке$R" to 0.55f,
        "${L}(this )?video is sponsored" to 0.9f,
        "${L}sponsor\\p{L}*" to 0.6f,
        "${L}партнер\\p{L}* (этого |сегодняшнего |нашего )?(видео|ролика|выпуска)" to 0.8f,
        "${L}переход\\p{L}*" to 0.3f,
        "${L}(скачива|скачай|установи|загружа)\\p{L}* (приложени|прилу)" to 0.5f,
        "${L}перв\\p{L}* заказ" to 0.5f,
        "${L}(кэшбэк|кешбэк|кэшбек|кешбек|cashback)" to 0.4f,
        "${L}(бесплатн\\p{L}*) (пробн|месяц|период|доставк)" to 0.4f,
        "${L}(регистрир|зарегистрир)\\p{L}*" to 0.3f,
    )
)

/** Weak ad vocabulary used to extend a segment until the speaker returns to the topic. */
class ContextSignal : RegexSignal(
    "context", HitKind.CONTEXT, listOf(
        "$L(приложени|сервис|заказ|доставк|подписк|курс|бонус|акци|цен[аеуы]|руб|бесплатн|установ|ссылк|скидк|выгодн|тариф|кешбэк|кэшбэк)" to 0.2f,
        "$L(app|order|delivery|subscription|discount|free trial|offer|sign up)" to 0.2f,
    )
)

/** "Back to the video" phrases. */
class ReturnToTopicSignal : RegexSignal(
    "return", HitKind.RETURN, listOf(
        "${L}(а |ну )?теперь (вернемся|давайте вернемся|к делу|продолжим)" to 1f,
        "${L}вернемся к" to 1f,
        "${L}(ну |а )?так вот$R" to 1f,
        "${L}итак$R" to 1f,
        "${L}продолжим$R" to 1f,
        "${L}(а )?теперь к (делу|теме|видео|ролику)" to 1f,
        "${L}anyway$R" to 1f,
        "${L}back to (the )?(video|topic)" to 1f,
    )
)

/**
 * User-editable phrases and brands, matched from a word start on normalized text. Cyrillic words
 * longer than three letters match any ending, so «лавка ромашка» also finds «в лавке ромашка».
 */
class ListSignal(
    override val id: String,
    private val kind: HitKind,
    entries: Collection<String>,
    private val weight: Float,
) : AdSignal {
    private val compiled = entries.map(Text::normalize).filter { it.isNotBlank() }.distinct()
        .map { it to Regex(L + it.split(' ').joinToString(" ", transform = ::stem)) }

    override fun match(normalized: String): List<SignalHit> = compiled.mapNotNull { (raw, re) ->
        if (re.containsMatchIn(normalized)) SignalHit(id, kind, weight, raw) else null
    }

    private companion object {
        val cyrillic = Regex("\\p{IsCyrillic}")

        fun stem(word: String): String =
            if (word.length > 3 && cyrillic.containsMatchIn(word)) Regex.escape(word.dropLast(1)) + "\\p{L}{0,3}"
            else Regex.escape(word)
    }
}

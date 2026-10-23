package com.qmurzik.vkenhancer.ui

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.card.MaterialCardView
import com.google.android.material.materialswitch.MaterialSwitch
import com.qmurzik.vkenhancer.common.Defaults
import com.qmurzik.vkenhancer.common.Keys
import com.qmurzik.vkenhancer.common.Sections

/**
 * The module's home screen: a card dashboard rather than a flat preference list.
 *
 * It shows module status, a master switch and one card per [Sections.Section] with a live
 * "active / total" badge, tapping into [SettingsActivity] scoped to that section. Built in code so
 * it needs no XML and stays in step with the [Sections] catalogue.
 */
class HomeActivity : AppCompatActivity() {

    private lateinit var conn: ServiceConn
    private val store get() = conn.store

    private val badges = HashMap<String, TextView>()
    private lateinit var masterSwitch: MaterialSwitch
    private lateinit var statusText: TextView
    private lateinit var heroCount: TextView

    private val accent by lazy { themeColor(com.google.android.material.R.attr.colorPrimary, 0xFF2787F5.toInt()) }
    private val onAccent by lazy { themeColor(com.google.android.material.R.attr.colorOnPrimary, Color.WHITE) }
    private val secondaryText by lazy { themeColor(android.R.attr.textColorSecondary, 0xFF888888.toInt()) }

    override fun onCreate(savedInstanceState: Bundle?) {
        conn = ServiceConn(this)
        super.onCreate(savedInstanceState)
        title = getString(com.qmurzik.vkenhancer.R.string.app_name)

        val scroll = ScrollView(this).apply { isFillViewport = true }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(24))
        }
        scroll.addView(root, lp(MATCH_PARENT, WRAP_CONTENT))
        setContentView(scroll)

        root.addView(buildHero())
        root.addView(sectionHeading("Разделы"))
        for (s in Sections.ALL) root.addView(buildCard(s))
        root.addView(buildAllSettingsCard())

        conn.observe { bound ->
            statusText.text = if (bound) "✓ Модуль активен — изменения применяются сразу"
            else "⚠ Не активирован в LSPosed. Включите модуль и выберите ВКонтакте, затем перезапустите приложение."
            statusText.setTextColor(onAccent)
            statusText.alpha = if (bound) 0.95f else 1f
            masterSwitch.isEnabled = bound
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    // --- views -------------------------------------------------------------------------------

    private fun buildHero(): View {
        val card = card().apply {
            setCardBackgroundColor(accent)
            setContentPadding(dp(20), dp(20), dp(20), dp(20))
        }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        col.addView(TextView(this).apply {
            text = "VK Enhancer"
            setTextColor(onAccent)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
            setTypeface(typeface, Typeface.BOLD)
        })
        heroCount = TextView(this).apply {
            setTextColor(onAccent)
            alpha = 0.95f
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setPadding(0, dp(2), 0, 0)
        }
        col.addView(heroCount)

        val switchRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(14), 0, 0)
        }
        switchRow.addView(TextView(this).apply {
            text = "Модуль включён"
            setTextColor(onAccent)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        }, lp(0, WRAP_CONTENT, 1f))
        masterSwitch = MaterialSwitch(this).apply {
            setOnCheckedChangeListener { _, checked ->
                if (isPressed) {
                    store.putBoolean(Keys.MODULE_ENABLED, checked)
                    refresh()
                }
            }
        }
        switchRow.addView(masterSwitch)
        col.addView(switchRow)

        statusText = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(0, dp(12), 0, 0)
        }
        col.addView(statusText)

        card.addView(col)
        return card
    }

    private fun buildCard(s: Sections.Section): View {
        val card = card().apply {
            isClickable = true
            isFocusable = true
            setContentPadding(dp(16), dp(16), dp(16), dp(16))
            setOnClickListener { openSection(s.id) }
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(TextView(this).apply {
            text = s.emoji
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
            setPadding(0, 0, dp(16), 0)
        })
        val textCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        textCol.addView(TextView(this).apply {
            text = s.title
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTypeface(typeface, Typeface.BOLD)
        })
        textCol.addView(TextView(this).apply {
            text = s.subtitle
            setTextColor(secondaryText)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        })
        row.addView(textCol, lp(0, WRAP_CONTENT, 1f))

        if (s.boolKeys.isNotEmpty()) {
            val badge = badgePill()
            badges[s.id] = badge
            row.addView(badge)
        } else {
            row.addView(TextView(this).apply {
                text = "›"
                setTextColor(secondaryText)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            })
        }

        card.addView(row)
        return card
    }

    private fun buildAllSettingsCard(): View {
        val card = card().apply {
            isClickable = true
            isFocusable = true
            setContentPadding(dp(16), dp(16), dp(16), dp(16))
            setOnClickListener {
                startActivity(Intent(this@HomeActivity, SettingsActivity::class.java))
            }
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(TextView(this).apply {
            text = "🔎"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            setPadding(0, 0, dp(16), 0)
        })
        row.addView(TextView(this).apply {
            text = "Все настройки и поиск"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTypeface(typeface, Typeface.BOLD)
        }, lp(0, WRAP_CONTENT, 1f))
        row.addView(TextView(this).apply {
            text = "›"
            setTextColor(secondaryText)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
        })
        card.addView(row)
        return card
    }

    private fun sectionHeading(label: String) = TextView(this).apply {
        text = label
        setTextColor(accent)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        setTypeface(typeface, Typeface.BOLD)
        isAllCaps = true
        setPadding(dp(4), dp(18), 0, dp(6))
    }

    private fun badgePill() = TextView(this).apply {
        gravity = Gravity.CENTER
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        setTypeface(typeface, Typeface.BOLD)
        minWidth = dp(44)
        setPadding(dp(10), dp(4), dp(10), dp(4))
    }

    private fun card(): MaterialCardView {
        val c = MaterialCardView(this)
        c.layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
            topMargin = dp(10)
        }
        c.radius = dp(18).toFloat()
        c.cardElevation = dp(1).toFloat()
        return c
    }

    // --- state -------------------------------------------------------------------------------

    private fun openSection(id: String) {
        startActivity(Intent(this, SettingsActivity::class.java).putExtra(SettingsActivity.EXTRA_SECTION, id))
    }

    private fun refresh() {
        masterSwitch.isChecked = store.getBoolean(Keys.MODULE_ENABLED, true)
        val all = LinkedHashSet<String>()
        var total = 0
        for (s in Sections.ALL) {
            var n = 0
            for (k in s.boolKeys) if (isOn(k)) { n++; all.add(k) }
            badges[s.id]?.let { styleBadge(it, n, s.boolKeys.size) }
            total += n
        }
        heroCount.text = "${all.size} функций активно"
    }

    private fun isOn(key: String) = store.getBoolean(key, Defaults.bool(key))

    private fun styleBadge(badge: TextView, active: Int, total: Int) {
        badge.text = "$active"
        val on = active > 0
        badge.setTextColor(if (on) onAccent else secondaryText)
        val bg = GradientDrawable().apply {
            cornerRadius = dp(14).toFloat()
            setColor(if (on) accent else ((0x22 shl 24) or (secondaryText and 0xFFFFFF)))
        }
        badge.background = bg
    }

    // --- helpers -----------------------------------------------------------------------------

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun lp(w: Int, h: Int, weight: Float = 0f) =
        LinearLayout.LayoutParams(w, h).apply { if (weight != 0f) this.weight = weight }

    private fun themeColor(attr: Int, fallback: Int): Int {
        val tv = TypedValue()
        return if (theme.resolveAttribute(attr, tv, true)) {
            if (tv.resourceId != 0) getColor(tv.resourceId) else tv.data
        } else fallback
    }
}

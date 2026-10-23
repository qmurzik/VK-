package com.qmurzik.vkenhancer.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.qmurzik.vkenhancer.common.ColorSlot
import com.qmurzik.vkenhancer.common.Keys
import java.util.Locale

/** A preference row for one [ColorSlot]: shows a swatch and opens an ARGB picker on click. */
class ColorPreference(
    context: Context,
    private val slot: ColorSlot,
    private val store: RemoteStore,
) : Preference(context) {

    private var swatch: View? = null

    init {
        key = Keys.colorKey(slot)
        title = slot.title
        isIconSpaceReserved = false
        widgetLayoutResource = 0
        updateSummary()
    }

    private val current: Int? get() = if (store.contains(key)) store.getInt(key, 0) else null

    private fun updateSummary() {
        summary = current?.let { String.format(Locale.US, "#%08X", it) } ?: "По умолчанию (тема ВК)"
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        // A coloured dot on the right edge, created in code to avoid a custom layout resource.
        val parent = holder.itemView as? LinearLayout ?: holder.itemView.findViewById(android.R.id.widget_frame)
        val dot = View(context).apply {
            val size = (28 * resources.displayMetrics.density).toInt()
            layoutParams = LinearLayout.LayoutParams(size, size).apply { gravity = Gravity.CENTER_VERTICAL }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                // Qualify: Drawable.getCurrent() would otherwise shadow our `current` property here.
                val fill: Int = this@ColorPreference.current ?: Color.TRANSPARENT
                setColor(fill)
                setStroke((2 * resources.displayMetrics.density).toInt(), 0x66888888.toInt())
            }
        }
        swatch = dot
        (holder.findViewById(android.R.id.widget_frame) as? LinearLayout)?.apply {
            removeAllViews(); visibility = View.VISIBLE; addView(dot)
        }
    }

    override fun onClick() = showPicker()

    private fun showPicker() {
        val start = current ?: 0xFF2787F5.toInt()
        val density = context.resources.displayMetrics.density
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (20 * density).toInt(); setPadding(pad, pad, pad, pad)
        }
        val preview = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (48 * density).toInt())
            background = GradientDrawable().apply { cornerRadius = 8 * density; setColor(start) }
        }
        root.addView(preview)

        val hex = EditText(context).apply {
            setText(String.format(Locale.US, "%08X", start)); hint = "AARRGGBB"
        }
        root.addView(hex)

        val channels = listOf("A", "R", "G", "B")
        val bars = ArrayList<SeekBar>()
        var guard = false
        fun compose() = (bars[0].progress shl 24) or (bars[1].progress shl 16) or (bars[2].progress shl 8) or bars[3].progress
        fun refresh(color: Int, fromHex: Boolean) {
            (preview.background as GradientDrawable).setColor(color)
            if (!fromHex) { guard = true; hex.setText(String.format(Locale.US, "%08X", color)); guard = false }
        }
        for (i in channels.indices) {
            val label = TextView(context).apply { text = channels[i] }
            val bar = SeekBar(context).apply {
                max = 255
                progress = (start shr (24 - i * 8)) and 0xFF
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(sb: SeekBar, p: Int, u: Boolean) { if (u) refresh(compose(), false) }
                    override fun onStartTrackingTouch(sb: SeekBar) {}
                    override fun onStopTrackingTouch(sb: SeekBar) {}
                })
            }
            bars.add(bar)
            root.addView(label); root.addView(bar)
        }
        hex.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                if (guard) return
                val parsed = runCatching { (java.lang.Long.parseLong(s.toString().trim(), 16) and 0xFFFFFFFFL).toInt() }.getOrNull() ?: return
                guard = true
                for (i in channels.indices) bars[i].progress = (parsed shr (24 - i * 8)) and 0xFF
                guard = false
                refresh(parsed, true)
            }
            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
        })

        AlertDialog.Builder(context)
            .setTitle(slot.title)
            .setView(root)
            .setPositiveButton("ОК") { _, _ ->
                store.putInt(key, compose())
                updateSummary(); notifyChanged()
            }
            .setNeutralButton("Сброс") { _, _ ->
                store.remove(key); updateSummary(); notifyChanged()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }
}

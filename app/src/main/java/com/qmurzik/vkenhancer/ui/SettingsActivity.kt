package com.qmurzik.vkenhancer.ui

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.qmurzik.vkenhancer.R

/** Launcher + host for the module settings. LSPosed opens this activity from the module list. */
class SettingsActivity : AppCompatActivity() {

    lateinit var conn: ServiceConn
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must exist before super.onCreate(): on recreation the FragmentManager restores
        // SettingsFragment inside super.onCreate() and immediately reads store() -> conn.
        conn = ServiceConn(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        setSupportActionBar(findViewById(R.id.toolbar))

        val status = findViewById<TextView>(R.id.status_banner)
        conn.observe { bound ->
            status.visibility = if (bound) TextView.GONE else TextView.VISIBLE
            status.text = getString(R.string.status_inactive)
        }

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.settings_container, SettingsFragment())
                .commit()
        }

        findViewById<android.widget.EditText>(R.id.search).addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                (supportFragmentManager.findFragmentById(R.id.settings_container) as? SettingsFragment)
                    ?.filter(s?.toString().orEmpty())
            }
            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
        })
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.settings, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_about) {
            AboutDialog.show(this)
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onSupportNavigateUp(): Boolean {
        if (supportFragmentManager.popBackStackImmediate()) return true
        return super.onSupportNavigateUp()
    }

    /** Lets child fragments reach the shared preference store. */
    fun store(): RemoteStore = conn.store
}

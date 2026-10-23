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
        super.onCreate(savedInstanceState)
        conn = ServiceConn(this)
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

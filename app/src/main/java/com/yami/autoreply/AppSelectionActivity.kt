package com.yami.autoreply

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

data class AppEntry(
    val packageName: String,
    val label: String,
    val icon: Drawable?
)

class AppSelectionActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: AppsAdapter
    private val selected = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_selection)

        selected.addAll(SecurePrefs.getSelectedApps(this))

        recyclerView = findViewById(R.id.appsRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        val apps = loadInstalledApps()
        adapter = AppsAdapter(apps, selected)
        recyclerView.adapter = adapter

        findViewById<View>(R.id.saveAppsButton).setOnClickListener {
            SecurePrefs.saveSelectedApps(this, selected)
            Toast.makeText(this, "Selección guardada", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    /** Lista las apps instaladas que un usuario abriría normalmente (con ícono en el launcher). */
    private fun loadInstalledApps(): List<AppEntry> {
        val pm = packageManager
        val launcherApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { appInfo ->
                appInfo.packageName != packageName &&
                    (pm.getLaunchIntentForPackage(appInfo.packageName) != null)
            }
            .map { appInfo ->
                AppEntry(
                    packageName = appInfo.packageName,
                    label = pm.getApplicationLabel(appInfo).toString(),
                    icon = try { pm.getApplicationIcon(appInfo.packageName) } catch (e: Exception) { null }
                )
            }
            .sortedBy { it.label.lowercase() }

        return launcherApps
    }
}

class AppsAdapter(
    private val apps: List<AppEntry>,
    private val selected: MutableSet<String>
) : RecyclerView.Adapter<AppsAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.appIcon)
        val name: TextView = view.findViewById(R.id.appName)
        val checkbox: CheckBox = view.findViewById(R.id.appCheckbox)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_app, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val app = apps[position]
        holder.icon.setImageDrawable(app.icon)
        holder.name.text = app.label
        holder.checkbox.setOnCheckedChangeListener(null)
        holder.checkbox.isChecked = selected.contains(app.packageName)
        holder.checkbox.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) selected.add(app.packageName) else selected.remove(app.packageName)
        }
        holder.itemView.setOnClickListener {
            holder.checkbox.isChecked = !holder.checkbox.isChecked
        }
    }

    override fun getItemCount() = apps.size
}

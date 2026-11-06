package com.dk.tvlauncher

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.palette.graphics.Palette
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var mainRootLayout: RelativeLayout
    private lateinit var tvGreeting: TextView
    private lateinit var rvApps: RecyclerView
    private lateinit var btnSettings: TextView
    private lateinit var btnTvSettings: TextView
    private lateinit var inputsContainer: LinearLayout
    private lateinit var btnHdmi1: TextView
    private lateinit var btnHdmi2: TextView
    private lateinit var btnAv: TextView
    private lateinit var btnHdmi3: TextView
    private lateinit var btnBoostRam: TextView
    private lateinit var btnRecentApps: TextView

    private val PICK_IMAGE_REQUEST = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences("tv_launcher_prefs", Context.MODE_PRIVATE)

        mainRootLayout = findViewById(R.id.mainRootLayout)
        tvGreeting = findViewById(R.id.tvGreeting)
        rvApps = findViewById(R.id.rvApps)
        btnSettings = findViewById(R.id.btnSettings)
        btnTvSettings = findViewById(R.id.btnTvSettings)
        inputsContainer = findViewById(R.id.inputsContainer)
        btnHdmi1 = findViewById(R.id.btnHdmi1)
        btnHdmi2 = findViewById(R.id.btnHdmi2)
        btnAv = findViewById(R.id.btnAv)
        btnHdmi3 = findViewById(R.id.btnHdmi3)
        btnBoostRam = findViewById(R.id.btnBoostRam)
        btnRecentApps = findViewById(R.id.btnRecentApps)

        setupButtons()
        loadPreferences()
        setupAppsList()
    }

    private fun setupButtons() {
        btnSettings.setOnClickListener {
            showSettingsDialog()
        }

        btnTvSettings.setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            } catch (e: Exception) {
                Toast.makeText(this, "Opening Settings...", Toast.LENGTH_SHORT).show()
            }
        }

        btnHdmi1.setOnClickListener { openInput("HDMI 1") }
        btnHdmi2.setOnClickListener { openInput("HDMI 2") }
        btnAv.setOnClickListener { openInput("AV") }
        btnHdmi3.setOnClickListener { openInput("HDMI 3") }

        // Boost RAM / Memory Optimizer
        btnBoostRam.setOnClickListener {
            performRamOptimization()
        }

        // Recent Apps / Task Manager
        btnRecentApps.setOnClickListener {
            openRecentApps()
        }
    }

    private fun openInput(source: String) {
        Toast.makeText(this, "Switching to $source...", Toast.LENGTH_SHORT).show()
    }

    private fun performRamOptimization() {
        try {
            val actManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memBefore = ActivityManager.MemoryInfo()
            actManager.getMemoryInfo(memBefore)

            // Kill background processes of other apps
            val runningApps = actManager.runningAppProcesses ?: emptyList()
            for (app in runningApps) {
                if (app.pkgList != null) {
                    for (pkg in app.pkgList) {
                        if (pkg != packageName) {
                            actManager.killBackgroundProcesses(pkg)
                        }
                    }
                }
            }

            System.gc()

            val memAfter = ActivityManager.MemoryInfo()
            actManager.getMemoryInfo(memAfter)
            val freedMb = (memAfter.availMem - memBefore.availMem) / (1024 * 1024)

            btnBoostRam.animate().scaleX(1.15f).scaleY(1.15f).setDuration(120).withEndAction {
                btnBoostRam.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start()
            }.start()

            if (freedMb > 0) {
                Toast.makeText(this, "⚡ Optimized! Freed ${freedMb} MB RAM", Toast.LENGTH_SHORT).show()
            } else {
                val freeTotal = memAfter.availMem / (1024 * 1024)
                Toast.makeText(this, "⚡ Cleaned! ${freeTotal} MB RAM Free", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "⚡ RAM Optimized!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openRecentApps() {
        // Try multiple methods to invoke recent apps screen on Android TV
        try {
            // Method 1: System intent for Recents
            val intent = Intent("android.intent.action.MAIN")
            intent.setClassName("com.android.systemui", "com.android.systemui.recents.RecentsActivity")
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
            return
        } catch (_: Exception) {}

        try {
            // Method 2: Leanback recents / Quick settings
            val intent = Intent("com.android.systemui.action.RECENTS")
            startActivity(intent)
            return
        } catch (_: Exception) {}

        try {
            // Method 3: Accessibility / Global Action via Task Switcher intent
            val intent = Intent(Intent.ACTION_ALL_APPS)
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Tip: Long-press HOME button on remote for Recent Apps", Toast.LENGTH_LONG).show()
        }
    }

    private fun applyDynamicColors(accentColor: Int, dominantBgColor: Int) {
        val density = resources.displayMetrics.density

        // Helper for interactive button state list
        fun createThemedSelector(radiusDp: Float, normalAlpha: Int, isAccentFocus: Boolean = false): StateListDrawable {
            val radius = radiusDp * density
            val focused = GradientDrawable().apply {
                cornerRadius = radius
                setColor(if (isAccentFocus) accentColor else Color.WHITE)
                setStroke((2.5f * density).toInt(), if (isAccentFocus) Color.WHITE else accentColor)
            }
            val normal = GradientDrawable().apply {
                cornerRadius = radius
                val tinted = Color.argb(
                    normalAlpha,
                    Color.red(dominantBgColor),
                    Color.green(dominantBgColor),
                    Color.blue(dominantBgColor)
                )
                setColor(tinted)
                setStroke((1 * density).toInt(), Color.argb(80, 255, 255, 255))
            }
            return StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_focused), focused)
                addState(intArrayOf(), normal)
            }
        }

        // Header buttons
        btnSettings.background = createThemedSelector(10f, 180, true)
        btnTvSettings.background = createThemedSelector(10f, 180, true)

        // Inputs container bar (clean seamless tint matching wallpaper, no dirty glass border)
        val barBg = GradientDrawable().apply {
            cornerRadius = 14 * density
            val barColor = Color.argb(160, Color.red(dominantBgColor), Color.green(dominantBgColor), Color.blue(dominantBgColor))
            setColor(barColor)
            setStroke((1 * density).toInt(), Color.argb(60, 255, 255, 255))
        }
        inputsContainer.background = barBg

        // Input individual buttons
        val inputBtnBg = createThemedSelector(8f, 220, true)
        btnHdmi1.background = createThemedSelector(8f, 200, true)
        btnHdmi2.background = createThemedSelector(8f, 200, true)
        btnAv.background = createThemedSelector(8f, 200, true)
        btnHdmi3.background = createThemedSelector(8f, 200, true)

        // Right Action Buttons (Boost RAM & Recent Apps)
        btnBoostRam.background = createThemedSelector(12f, 210, true)
        btnRecentApps.background = createThemedSelector(12f, 210, true)
    }

    private fun loadPreferences() {
        val userName = prefs.getString("user_name", "Deep") ?: "Deep"
        val bgColor = prefs.getString("bg_color", "#14110F") ?: "#14110F"
        val accentColorHex = prefs.getString("accent_color", "#00E5FF") ?: "#00E5FF"
        val wallpaperPath = prefs.getString("wallpaper_path", null)

        tvGreeting.text = "Good Night, $userName"

        var accentColor = try { Color.parseColor(accentColorHex) } catch (_: Exception) { Color.parseColor("#00E5FF") }
        var dominantColor = try { Color.parseColor(bgColor) } catch (_: Exception) { Color.parseColor("#1E282D") }

        if (wallpaperPath != null && File(wallpaperPath).exists()) {
            val bitmap = BitmapFactory.decodeFile(wallpaperPath)
            mainRootLayout.background = BitmapDrawable(resources, bitmap)

            if (prefs.getBoolean("auto_match", true)) {
                Palette.from(bitmap).generate { palette ->
                    val vib = palette?.getVibrantColor(accentColor) ?: accentColor
                    val dom = palette?.getDarkVibrantColor(dominantColor) ?: dominantColor
                    applyDynamicColors(vib, dom)
                }
                return
            }
        } else {
            try {
                mainRootLayout.setBackgroundColor(Color.parseColor(bgColor))
            } catch (e: Exception) {
                mainRootLayout.setBackgroundColor(Color.parseColor("#14110F"))
            }
        }

        applyDynamicColors(accentColor, dominantColor)
    }

    private fun setupAppsList() {
        val pm = packageManager
        val intent = Intent(Intent.ACTION_MAIN, null)
        intent.addCategory(Intent.CATEGORY_LAUNCHER)

        val allApps = pm.queryIntentActivities(intent, 0)
        val filteredList = allApps.filter { it.activityInfo.packageName != packageName }.toMutableList()

        val columns = prefs.getInt("grid_columns", 5)
        val cornerRadiusDp = prefs.getInt("corner_radius", 20)

        rvApps.layoutManager = GridLayoutManager(this, columns)
        rvApps.adapter = AppsAdapter(this, filteredList, pm, cornerRadiusDp) {
            openAddAppFlow()
        }
    }

    private fun openAddAppFlow() {
        val storeIntent = packageManager.getLaunchIntentForPackage("com.android.vending")
        if (storeIntent != null) {
            startActivity(storeIntent)
        } else {
            try {
                val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=apps"))
                startActivity(marketIntent)
            } catch (e: Exception) {
                Toast.makeText(this, "Opening App Store...", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showSettingsDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_settings, null)
        val etName = dialogView.findViewById<EditText>(R.id.etName)
        val etAccent = dialogView.findViewById<EditText>(R.id.etAccentColor)
        val previewAccent = dialogView.findViewById<View>(R.id.previewAccentColor)
        val etBg = dialogView.findViewById<EditText>(R.id.etBgColor)
        val previewBg = dialogView.findViewById<View>(R.id.previewBgColor)
        val cbAuto = dialogView.findViewById<CheckBox>(R.id.cbAutoMatch)
        val btnChoose = dialogView.findViewById<Button>(R.id.btnChooseWallpaper)
        val btnRemove = dialogView.findViewById<Button>(R.id.btnRemoveWallpaper)
        val rgCorners = dialogView.findViewById<RadioGroup>(R.id.rgCornerStyle)
        val rgCols = dialogView.findViewById<RadioGroup>(R.id.rgColumns)

        etName.setText(prefs.getString("user_name", "Deep"))
        etAccent.setText(prefs.getString("accent_color", "#00E5FF"))
        etBg.setText(prefs.getString("bg_color", "#14110F"))
        cbAuto.isChecked = prefs.getBoolean("auto_match", true)

        val curRadius = prefs.getInt("corner_radius", 20)
        when (curRadius) {
            0 -> dialogView.findViewById<RadioButton>(R.id.rbCornerNone).isChecked = true
            10 -> dialogView.findViewById<RadioButton>(R.id.rbCornerSharp).isChecked = true
            30 -> dialogView.findViewById<RadioButton>(R.id.rbCornerOval).isChecked = true
            else -> dialogView.findViewById<RadioButton>(R.id.rbCornerRounded).isChecked = true
        }

        val curCols = prefs.getInt("grid_columns", 5)
        when (curCols) {
            4 -> dialogView.findViewById<RadioButton>(R.id.rbCol4).isChecked = true
            6 -> dialogView.findViewById<RadioButton>(R.id.rbCol6).isChecked = true
            else -> dialogView.findViewById<RadioButton>(R.id.rbCol5).isChecked = true
        }

        try {
            previewAccent.setBackgroundColor(Color.parseColor(etAccent.text.toString()))
            previewBg.setBackgroundColor(Color.parseColor(etBg.text.toString()))
        } catch (_: Exception) {}

        setupWatcher(etAccent, previewAccent)
        setupWatcher(etBg, previewBg)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        btnChoose.setOnClickListener {
            val pickIntent = Intent(Intent.ACTION_GET_CONTENT).apply { type = "image/*" }
            startActivityForResult(pickIntent, PICK_IMAGE_REQUEST)
        }

        btnRemove.setOnClickListener {
            prefs.edit().remove("wallpaper_path").apply()
            loadPreferences()
            Toast.makeText(this, "Wallpaper removed", Toast.LENGTH_SHORT).show()
        }

        dialogView.findViewById<Button>(R.id.btnCancel).setOnClickListener {
            dialog.dismiss()
        }

        dialogView.findViewById<Button>(R.id.btnSave).setOnClickListener {
            val selectedRadius = when (rgCorners.checkedRadioButtonId) {
                R.id.rbCornerNone -> 0
                R.id.rbCornerSharp -> 10
                R.id.rbCornerOval -> 30
                else -> 20
            }
            val selectedCols = when (rgCols.checkedRadioButtonId) {
                R.id.rbCol4 -> 4
                R.id.rbCol6 -> 6
                else -> 5
            }

            prefs.edit()
                .putString("user_name", etName.text.toString().trim())
                .putString("accent_color", etAccent.text.toString().trim())
                .putString("bg_color", etBg.text.toString().trim())
                .putBoolean("auto_match", cbAuto.isChecked)
                .putInt("corner_radius", selectedRadius)
                .putInt("grid_columns", selectedCols)
                .apply()

            loadPreferences()
            setupAppsList()
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun setupWatcher(editText: EditText, preview: View) {
        editText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val str = s?.toString()?.trim() ?: ""
                if (str.matches(Regex("^#([A-Fa-f0-9]{6}|[A-Fa-f0-9]{8})$"))) {
                    try {
                        preview.setBackgroundColor(Color.parseColor(str))
                    } catch (_: Exception) {}
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == Activity.RESULT_OK && data?.data != null) {
            val uri: Uri = data.data!!
            val file = File(filesDir, "custom_wallpaper.jpg")
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(file).use { output -> input.copyTo(output) }
            }
            prefs.edit().putString("wallpaper_path", file.absolutePath).apply()

            if (prefs.getBoolean("auto_match", true)) {
                val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                Palette.from(bitmap).generate { palette ->
                    val domColor = palette?.getDarkVibrantColor(Color.parseColor("#14110F")) ?: Color.parseColor("#14110F")
                    val hexColor = String.format("#%06X", (0xFFFFFF and domColor))
                    val vibColor = palette?.getVibrantColor(Color.parseColor("#00E5FF")) ?: Color.parseColor("#00E5FF")
                    val vibHex = String.format("#%06X", (0xFFFFFF and vibColor))
                    prefs.edit().putString("bg_color", hexColor).putString("accent_color", vibHex).apply()
                    loadPreferences()
                }
            } else {
                loadPreferences()
            }
        }
    }

    class AppsAdapter(
        private val context: Context,
        private val list: List<ResolveInfo>,
        private val pm: PackageManager,
        private val cornerRadiusDp: Int,
        private val onAddClick: () -> Unit
    ) : RecyclerView.Adapter<AppsAdapter.ViewHolder>() {

        private val TYPE_APP = 0
        private val TYPE_ADD = 1

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val container: FrameLayout = view.findViewById(R.id.rootCardContainer)
            val cardApp: CardView = view.findViewById(R.id.cardApp)
            val ivBanner: ImageView = view.findViewById(R.id.ivAppBanner)
            val focusBorderView: View = view.findViewById(R.id.focusBorderView)
            val layoutAdd: LinearLayout = view.findViewById(R.id.layoutAddCard)
        }

        override fun getItemViewType(position: Int): Int {
            return if (position < list.size) TYPE_APP else TYPE_ADD
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(context).inflate(R.layout.item_app, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val density = context.resources.displayMetrics.density
            val radiusPx = (cornerRadiusDp * density)

            holder.cardApp.radius = radiusPx
            holder.cardApp.cardElevation = 0f
            holder.cardApp.maxCardElevation = 0f

            if (getItemViewType(position) == TYPE_ADD) {
                holder.ivBanner.visibility = View.GONE
                holder.layoutAdd.visibility = View.VISIBLE

                val addBg = GradientDrawable().apply {
                    cornerRadius = radiusPx
                    setColor(Color.parseColor("#33FFFFFF"))
                    setStroke((1.5f * density).toInt(), Color.parseColor("#66FFFFFF"))
                }
                holder.cardApp.background = addBg

                holder.container.setOnClickListener {
                    onAddClick()
                }
            } else {
                holder.layoutAdd.visibility = View.GONE
                holder.ivBanner.visibility = View.VISIBLE
                holder.cardApp.background = null

                val item = list[position]
                val banner = item.activityInfo.loadBanner(pm)
                if (banner != null) {
                    holder.ivBanner.setImageDrawable(banner)
                } else {
                    holder.ivBanner.setImageDrawable(item.loadIcon(pm))
                }

                holder.container.setOnClickListener {
                    val launchIntent = pm.getLaunchIntentForPackage(item.activityInfo.packageName)
                    if (launchIntent != null) {
                        context.startActivity(launchIntent)
                    }
                }
            }

            holder.container.setOnFocusChangeListener { v, hasFocus ->
                if (hasFocus) {
                    v.animate().scaleX(1.07f).scaleY(1.07f).setDuration(120).start()
                    val border = GradientDrawable().apply {
                        cornerRadius = radiusPx
                        setStroke((3.5f * density).toInt(), Color.WHITE)
                        setColor(Color.TRANSPARENT)
                    }
                    holder.focusBorderView.background = border
                    holder.focusBorderView.visibility = View.VISIBLE
                } else {
                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start()
                    holder.focusBorderView.visibility = View.GONE
                }
            }
        }

        override fun getItemCount(): Int = list.size + 1
    }
}

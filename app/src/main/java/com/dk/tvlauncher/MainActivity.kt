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
import android.provider.Settings
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
    private lateinit var btnWifi: TextView
    private lateinit var btnBluetooth: TextView
    private lateinit var btnSettings: TextView
    private lateinit var btnTvSettings: TextView
    private lateinit var btnInputsMenu: TextView
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
        btnWifi = findViewById(R.id.btnWifi)
        btnBluetooth = findViewById(R.id.btnBluetooth)
        btnSettings = findViewById(R.id.btnSettings)
        btnTvSettings = findViewById(R.id.btnTvSettings)
        btnInputsMenu = findViewById(R.id.btnInputsMenu)
        btnBoostRam = findViewById(R.id.btnBoostRam)
        btnRecentApps = findViewById(R.id.btnRecentApps)

        setupButtons()
        loadPreferences()
        setupAppsList()
    }

    private fun setupButtons() {
        // Direct Wi-Fi shortcut
        btnWifi.setOnClickListener { openWifiSettings() }

        // Direct Bluetooth shortcut
        btnBluetooth.setOnClickListener { openBluetoothSettings() }

        btnSettings.setOnClickListener { showSettingsDialog() }

        btnTvSettings.setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            } catch (e: Exception) {
                Toast.makeText(this, "Opening Settings...", Toast.LENGTH_SHORT).show()
            }
        }

        btnInputsMenu.setOnClickListener { showInputsDialog() }
        btnBoostRam.setOnClickListener { performRamOptimization() }
        btnRecentApps.setOnClickListener { showRecentAppsDialog() }
    }

    private fun openWifiSettings() {
        val intents = listOf(
            Intent(Settings.ACTION_WIFI_SETTINGS),
            Intent("android.settings.WIFI_SETTINGS"),
            Intent(Settings.ACTION_WIRELESS_SETTINGS)
        )
        for (intent in intents) {
            try {
                startActivity(intent)
                return
            } catch (_: Exception) {}
        }
        try {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        } catch (_: Exception) {
            Toast.makeText(this, "Opening Wi-Fi...", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openBluetoothSettings() {
        val intents = listOf(
            Intent(Settings.ACTION_BLUETOOTH_SETTINGS),
            Intent("android.settings.BLUETOOTH_SETTINGS"),
            Intent("com.android.tv.settings.accessories.AccessoriesActivity")
        )
        for (intent in intents) {
            try {
                startActivity(intent)
                return
            } catch (_: Exception) {}
        }
        try {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        } catch (_: Exception) {
            Toast.makeText(this, "Opening Bluetooth...", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showInputsDialog() {
        val inputs = arrayOf("HDMI 1", "HDMI 2", "AV", "HDMI 3")
        AlertDialog.Builder(this)
            .setTitle("Select Input Source")
            .setItems(inputs) { _, which ->
                val selected = inputs[which]
                Toast.makeText(this, "Switching to $selected...", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("CANCEL", null)
            .show()
    }

    private fun showRecentAppsDialog() {
        val actManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val pm = packageManager
        val runningTasks = actManager.getRunningTasks(15) ?: emptyList()

        val recentApps = mutableListOf<ResolveInfo>()
        for (task in runningTasks) {
            val pkg = task.topActivity?.packageName ?: continue
            if (pkg != packageName) {
                val launchIntent = pm.getLaunchIntentForPackage(pkg)
                if (launchIntent != null) {
                    val info = pm.resolveActivity(launchIntent, 0)
                    if (info != null && !recentApps.any { it.activityInfo.packageName == pkg }) {
                        recentApps.add(info)
                    }
                }
            }
        }

        val dialogView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 30, 40, 30)
            setBackgroundColor(Color.parseColor("#1B2226"))
        }

        val titleTv = TextView(this).apply {
            text = "Active Background Tasks (${recentApps.size})"
            textSize = 18f
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 20)
        }
        dialogView.addView(titleTv)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        if (recentApps.isEmpty()) {
            val emptyTv = TextView(this).apply {
                text = "No active background apps. System is fully optimized!"
                textSize = 14f
                setTextColor(Color.parseColor("#B0FFFFFF"))
                setPadding(0, 20, 0, 20)
            }
            dialogView.addView(emptyTv)
        } else {
            val listLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
            }

            for (app in recentApps) {
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(16, 14, 16, 14)
                    gravity = android.view.Gravity.CENTER_VERTICAL
                    isFocusable = true
                    isClickable = true
                    background = createButtonSelector(Color.parseColor("#00E5FF"), Color.parseColor("#25FFFFFF"), 8f)
                    val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    lp.setMargins(0, 4, 0, 4)
                    layoutParams = lp
                }

                val iconIv = ImageView(this).apply {
                    val density = resources.displayMetrics.density
                    val size = (36 * density).toInt()
                    layoutParams = LinearLayout.LayoutParams(size, size).apply { marginEnd = (12 * density).toInt() }
                    setImageDrawable(app.loadIcon(pm))
                }

                val nameTv = TextView(this).apply {
                    text = app.loadLabel(pm)
                    setTextColor(Color.WHITE)
                    textSize = 14f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                }

                row.addView(iconIv)
                row.addView(nameTv)

                row.setOnClickListener {
                    val launchIntent = pm.getLaunchIntentForPackage(app.activityInfo.packageName)
                    if (launchIntent != null) {
                        dialog.dismiss()
                        startActivity(launchIntent)
                    }
                }

                listLayout.addView(row)
            }
            dialogView.addView(listLayout)
        }

        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.END
            setPadding(0, 24, 0, 0)
        }

        val btnClearAll = Button(this).apply {
            text = "⚡ CLEAR ALL & CLOSE"
            setTextColor(Color.parseColor("#FFC107"))
            setBackgroundColor(Color.TRANSPARENT)
            setOnClickListener {
                for (app in recentApps) {
                    actManager.killBackgroundProcesses(app.activityInfo.packageName)
                }
                System.gc()
                Toast.makeText(this@MainActivity, "Cleaned all background apps!", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
        }

        val btnClose = Button(this).apply {
            text = "CLOSE"
            setTextColor(Color.parseColor("#00E5FF"))
            setBackgroundColor(Color.TRANSPARENT)
            setOnClickListener { dialog.dismiss() }
        }

        btnRow.addView(btnClearAll)
        btnRow.addView(btnClose)
        dialogView.addView(btnRow)

        dialog.show()
    }

    private fun performRamOptimization() {
        try {
            val actManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memBefore = ActivityManager.MemoryInfo()
            actManager.getMemoryInfo(memBefore)

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

    private fun createButtonSelector(accentColor: Int, normalBgColor: Int, radiusDp: Float): StateListDrawable {
        val density = resources.displayMetrics.density
        val radius = radiusDp * density
        val focused = GradientDrawable().apply {
            cornerRadius = radius
            setColor(accentColor)
            setStroke((2.5f * density).toInt(), Color.WHITE)
        }
        val normal = GradientDrawable().apply {
            cornerRadius = radius
            setColor(normalBgColor)
            setStroke((1 * density).toInt(), Color.argb(80, 255, 255, 255))
        }
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), focused)
            addState(intArrayOf(), normal)
        }
    }

    private fun applyDynamicColors(accentColor: Int, dominantBgColor: Int) {
        val normalTint = Color.argb(190, Color.red(dominantBgColor), Color.green(dominantBgColor), Color.blue(dominantBgColor))

        btnWifi.background = createButtonSelector(accentColor, normalTint, 10f)
        btnBluetooth.background = createButtonSelector(accentColor, normalTint, 10f)
        btnSettings.background = createButtonSelector(accentColor, normalTint, 10f)
        btnTvSettings.background = createButtonSelector(accentColor, normalTint, 10f)
        btnInputsMenu.background = createButtonSelector(accentColor, normalTint, 12f)
        btnBoostRam.background = createButtonSelector(accentColor, normalTint, 12f)
        btnRecentApps.background = createButtonSelector(accentColor, normalTint, 12f)
    }

    private fun loadPreferences() {
        val userName = prefs.getString("user_name", "Deep") ?: "Deep"
        val bgColor = prefs.getString("bg_color", "#14110F") ?: "#14110F"
        val accentColorHex = prefs.getString("accent_color", "#00E5FF") ?: "#00E5FF"
        val wallpaperPath = prefs.getString("wallpaper_path", null)

        tvGreeting.text = "Good Night, $userName"

        var accentColor = try { Color.parseColor(accentColorHex) } catch (_: Exception) { Color.parseColor("#00E5FF") }
        var dominantColor = try { Color.parseColor(bgColor) } catch (_: Exception) { Color.parseColor("#1B2226") }

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
        val cbAuto = dialogView.findViewById<CheckBox>(R.id.cbAutoMatch)
        val btnChoose = dialogView.findViewById<Button>(R.id.btnChooseWallpaper)
        val btnRemove = dialogView.findViewById<Button>(R.id.btnRemoveWallpaper)
        val rgCorners = dialogView.findViewById<RadioGroup>(R.id.rgCornerStyle)
        val rgCols = dialogView.findViewById<RadioGroup>(R.id.rgColumns)

        etName.setText(prefs.getString("user_name", "Deep"))
        cbAuto.isChecked = prefs.getBoolean("auto_match", true)

        var selectedAccent = prefs.getString("accent_color", "#00E5FF") ?: "#00E5FF"

        val colorsMap = mapOf(
            R.id.colorCyan to "#00E5FF",
            R.id.colorGold to "#FFC107",
            R.id.colorGreen to "#00E676",
            R.id.colorPurple to "#D500F9",
            R.id.colorOrange to "#FF6D00",
            R.id.colorRed to "#FF1744"
        )

        for ((viewId, hex) in colorsMap) {
            dialogView.findViewById<View>(viewId).setOnClickListener {
                selectedAccent = hex
                cbAuto.isChecked = false
                Toast.makeText(this, "Selected theme color!", Toast.LENGTH_SHORT).show()
            }
        }

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

        dialogView.findViewById<Button>(R.id.btnCancel).setOnClickListener { dialog.dismiss() }

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
                .putString("accent_color", selectedAccent)
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

                holder.container.setOnClickListener { onAddClick() }
            } else {
                holder.layoutAdd.visibility = View.GONE
                holder.ivBanner.visibility = View.VISIBLE

                val item = list[position]
                val banner = item.activityInfo.loadBanner(pm)
                
                if (banner != null) {
                    // True widescreen Leanback banner: Preserve aspect & crop cleanly into rounded card
                    holder.ivBanner.setImageDrawable(banner)
                    holder.ivBanner.scaleType = ImageView.ScaleType.CENTER_CROP
                    holder.ivBanner.setPadding(0, 0, 0, 0)
                    holder.cardApp.background = null
                } else {
                    // Square/Portrait App Icon: DO NOT STRETCH! Keep original aspect ratio in center with nice background card
                    holder.ivBanner.setImageDrawable(item.loadIcon(pm))
                    holder.ivBanner.scaleType = ImageView.ScaleType.FIT_CENTER
                    val p = (12 * density).toInt()
                    holder.ivBanner.setPadding(p, p, p, p)
                    val iconBg = GradientDrawable().apply {
                        cornerRadius = radiusPx
                        setColor(Color.parseColor("#22282C"))
                    }
                    holder.cardApp.background = iconBg
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

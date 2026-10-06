package net.wault.autofill

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import net.wault.BuildConfig
import net.wault.R
import net.wault.item.ItemContent
import net.wault.ui.UiSettings
import net.wault.ui.i18n.AppStrings
import net.wault.ui.i18n.localeToStrings

private const val HIDE_DELAY_MS = 6_000L
private const val FILL_RETRY_DELAY_MS = 220L
private const val FILL_RETRIES = 6
private const val TILE_OFFSET_DP = 4
private const val SUPPRESS_AFTER_FILL_MS = 2_500L
private const val TAG = "WaultFill"

class WaultAccessibilityService : AccessibilityService() {

    private val main = Handler(Looper.getMainLooper())
    private var windows: WindowManager? = null
    private var tile: View? = null
    private var tileSubtitle: TextView? = null
    private var tileAttached = false
    private var anchoredPackage: String? = null
    private var suppressUntilMillis = 0L
    private val hideRunnable = Runnable { hideTile() }

    override fun onServiceConnected() {
        super.onServiceConnected()
        windows = getSystemService(WINDOW_SERVICE) as? WindowManager
        AccessibilityFillBridge.attach(this)
    }

    override fun onDestroy() {
        AccessibilityFillBridge.detach(this)
        hideTile()
        super.onDestroy()
    }

    override fun onInterrupt() {
        hideTile()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val type = event?.eventType ?: return
        when (type) {
            AccessibilityEvent.TYPE_VIEW_FOCUSED,
            AccessibilityEvent.TYPE_VIEW_CLICKED,
            AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED -> onFieldTouched(event)

            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> onWindowChanged()

            else -> Unit
        }
    }

    private fun onWindowChanged() {
        val anchored = anchoredPackage ?: return
        val active = runCatching { rootInActiveWindow?.packageName?.toString() }.getOrNull()
        if (active != null && active != anchored) hideTile()
    }

    private fun onFieldTouched(event: AccessibilityEvent) {
        if (packageName == event.packageName) {
            hideTile()
            return
        }

        if (SystemClock.elapsedRealtime() < suppressUntilMillis) return

        val source = runCatching { event.source }.getOrNull()
        if (source == null || !source.isEditable) {
            hideTile()
            return
        }

        val kind = AccessibilityFormScanner.classify(source)
        if (BuildConfig.DEBUG) {
            Log.d(
                TAG,
                "field pkg=${event.packageName} editable=${source.isEditable} " +
                    "password=${source.isPassword} hint=${source.hintText} " +
                    "id=${source.viewIdResourceName} kind=$kind"
            )
        }

        if (kind == null) {
            hideTile()
            return
        }

        val bounds = Rect().also { source.getBoundsInScreen(it) }
        if (bounds.isEmpty) {
            hideTile()
            return
        }

        showTile(bounds, event.packageName?.toString())
    }

    private fun strings(): AppStrings = localeToStrings(UiSettings.locale.value)

    private fun matchCount(target: String?): Int? {
        val container = AutofillBridge.unlocked() ?: return null
        val logins = container.items.items.value.filter { it.content is ItemContent.Login }
        if (logins.isEmpty() || target == null) return logins.size

        val matches = AutofillMatcher.candidates(
            items = logins,
            webDomain = null,
            packageName = target
        )
        return if (matches.isEmpty()) logins.size else matches.size
    }

    private fun showTile(anchor: Rect, targetPackage: String?) {
        val manager = windows ?: return
        if (!canOverlay()) return

        val strings = strings()
        val count = matchCount(targetPackage)
        val subtitle = when {
            count == null -> strings.autofillTileLocked
            count == 0 -> strings.autofillNoMatches
            else -> strings.autofillTileMatches(count)
        }

        val view = tile ?: buildTile(targetPackage).also { tile = it }
        tileSubtitle?.text = subtitle
        view.setTag(R.id.autofill_tile_package, targetPackage)
        anchoredPackage = targetPackage

        val params = layoutParams(anchor)
        runCatching {
            if (tileAttached) {
                manager.updateViewLayout(view, params)
            } else {
                manager.addView(view, params)
                tileAttached = true
            }
        }.onFailure { if (BuildConfig.DEBUG) Log.w(TAG, "tile attach failed: ${it.message}") }

        main.removeCallbacks(hideRunnable)
        main.postDelayed(hideRunnable, HIDE_DELAY_MS)
    }

    private fun layoutParams(anchor: Rect): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = anchor.left
            y = anchor.bottom + dp(TILE_OFFSET_DP)
        }
    }

    private fun buildTile(targetPackage: String?): View {
        val strings = strings()

        val background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(14).toFloat()
            setColor(Color.parseColor("#17181C"))
            setStroke(dp(1), Color.parseColor("#33FFFFFF"))
        }

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(10), dp(16), dp(10))
            setBackground(background)
            elevation = dp(6).toFloat()
        }

        val badge = ImageView(this).apply {
            setImageResource(R.mipmap.ic_launcher)
            layoutParams = LinearLayout.LayoutParams(dp(28), dp(28))
        }

        val labels = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), 0, 0, 0)
        }

        val title = TextView(this)
        title.text = getString(R.string.app_name)
        title.setTextColor(Color.WHITE)
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)

        val subtitle = TextView(this)
        subtitle.text = strings.autofillTileLocked
        subtitle.setTextColor(Color.parseColor("#B8BCC4"))
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        tileSubtitle = subtitle

        labels.addView(title)
        labels.addView(subtitle)
        row.addView(badge)
        row.addView(labels)

        row.setOnClickListener { view ->
            val chosen = view.getTag(R.id.autofill_tile_package) as? String ?: targetPackage
            hideTile()
            launchPicker(chosen)
        }

        return row
    }

    private fun launchPicker(targetPackage: String?) {
        val intent = Intent(this, AccessibilityFillActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            putExtra(AccessibilityFillActivity.EXTRA_TARGET_PACKAGE, targetPackage)
        }
        runCatching { startActivity(intent) }
    }

    private fun canOverlay(): Boolean =
        runCatching { Settings.canDrawOverlays(this) }.getOrDefault(false)

    fun hideTile() {
        main.removeCallbacks(hideRunnable)
        anchoredPackage = null
        val view = tile ?: return
        if (tileAttached) runCatching { windows?.removeView(view) }
        tileAttached = false
        tile = null
        tileSubtitle = null
    }

    fun deliver(username: String?, password: String?) {
        suppressUntilMillis = SystemClock.elapsedRealtime() + SUPPRESS_AFTER_FILL_MS
        hideTile()
        attemptFill(username, password, FILL_RETRIES)
    }

    private fun attemptFill(username: String?, password: String?, remaining: Int) {
        main.postDelayed({
            val form = AccessibilityFormScanner.scan(rootInActiveWindow, findFocus(AccessibilityNodeInfo.FOCUS_INPUT))
            val filledPassword = password?.let { form?.password?.node?.let { node -> setText(node, it) } } ?: false
            val filledUsername = username?.let { form?.username?.node?.let { node -> setText(node, it) } } ?: false

            if (!filledPassword && !filledUsername && remaining > 0) {
                attemptFill(username, password, remaining - 1)
            }
        }, FILL_RETRY_DELAY_MS)
    }

    private fun setText(node: AccessibilityNodeInfo, value: String): Boolean {
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value)
        }
        return runCatching {
            node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        }.getOrDefault(false)
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}

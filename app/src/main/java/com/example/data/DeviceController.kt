package com.example.data

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class DeviceController(private val context: Context) {

    companion object {
        private const val TAG = "DeviceCtrl"
        private const val MAX_RETRIES = 3
        private const val RETRY_DELAY_MS = 800L
        private const val UI_TIMEOUT_MS = 3000L
        private const val SCREEN_READ_MAX_DEPTH = 10
        private const val MAX_CONSECUTIVE_FAILURES = 5
    }

    data class DeviceAction(
        val type: ActionType,
        val target: String? = null,
        val x: Int? = null,
        val y: Int? = null,
        val text: String? = null,
        val packageName: String? = null,
        val description: String = ""
    )

    enum class ActionType {
        TAP, LONG_PRESS, SWIPE_UP, SWIPE_DOWN, SWIPE_LEFT, SWIPE_RIGHT,
        TYPE_TEXT, OPEN_APP, BACK, HOME, RECENTS,
        SCREENSHOT, SCROLL_UP, SCROLL_DOWN, FIND_AND_TAP
    }

    data class ActionResult(
        val success: Boolean,
        val message: String,
        val screenContent: String? = null
    )

    private var accessibilityService: ZarpAccessibilityService? = null

    fun setAccessibilityService(service: ZarpAccessibilityService?) {
        accessibilityService = service
        Log.d(TAG, "♿ Accessibility ${if (service != null) "connected" else "disconnected"}")
    }

    private val isServiceAvailable: Boolean get() = accessibilityService != null

    // ═══════════════════════════════════════════
    // Public API
    // ═══════════════════════════════════════════

    suspend fun executeAction(
        action: DeviceAction,
        retryCount: Int = MAX_RETRIES
    ): ActionResult = withContext(Dispatchers.IO) {
        if (!isServiceAvailable) {
            return@withContext ActionResult(false, "Accessibility not connected. Enable in Settings → Accessibility → Zarp.")
        }
        Log.d(TAG, "🎮 ${action.type}: ${action.description}")
        var lastResult = ActionResult(false, "Not executed")
        repeat(retryCount) { attempt ->
            lastResult = executeOnce(action)
            if (lastResult.success) return@withContext lastResult
            if (attempt < retryCount - 1) delay(RETRY_DELAY_MS)
        }
        Log.w(TAG, "  Failed after $retryCount attempts")
        lastResult
    }

    suspend fun executeSequence(
        actions: List<DeviceAction>,
        retryCount: Int = MAX_RETRIES
    ): List<ActionResult> = withContext(Dispatchers.IO) {
        val results = mutableListOf<ActionResult>()
        var consecutiveFailures = 0
        for ((index, action) in actions.withIndex()) {
            Log.d(TAG, "▶️ ${index + 1}/${actions.size}: ${action.description}")
            val result = executeAction(action, retryCount)
            results.add(result)
            if (result.success) consecutiveFailures = 0
            else {
                consecutiveFailures++
                if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                    Log.w(TAG, "⚠️ Stopping after $consecutiveFailures failures")
                    break
                }
            }
            delay(500)
        }
        results
    }

    // ═══════════════════════════════════════════
    // Action Dispatcher
    // ═══════════════════════════════════════════

    private suspend fun executeOnce(action: DeviceAction): ActionResult = withContext(Dispatchers.IO) {
        when (action.type) {
            ActionType.TAP -> executeTap(action)
            ActionType.LONG_PRESS -> executeLongPress(action)
            ActionType.SWIPE_UP -> executeSwipe(540, 1500, 540, 800, 300)
            ActionType.SWIPE_DOWN -> executeSwipe(540, 800, 540, 1500, 300)
            ActionType.SWIPE_LEFT -> executeSwipe(900, 1200, 500, 1200, 300)
            ActionType.SWIPE_RIGHT -> executeSwipe(200, 1200, 600, 1200, 300)
            ActionType.TYPE_TEXT -> executeTypeText(action.text ?: "")
            ActionType.OPEN_APP -> executeOpenApp(action.packageName ?: "")
            ActionType.BACK -> executeGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK, "Back")
            ActionType.HOME -> executeGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME, "Home")
            ActionType.RECENTS -> executeGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS, "Recents")
            ActionType.SCREENSHOT -> executeScreenshot()
            ActionType.SCROLL_UP -> executeScroll(true)
            ActionType.SCROLL_DOWN -> executeScroll(false)
            ActionType.FIND_AND_TAP -> executeFindAndTap(action.target ?: "")
        }
    }

    // ═══════════════════════════════════════════
    // Action Implementations (ALL NODE-SAFE)
    // ═══════════════════════════════════════════

    private suspend fun executeTap(action: DeviceAction): ActionResult {
        if (action.target != null) return executeFindAndTap(action.target)
        if (action.x == null || action.y == null) return ActionResult(false, "No target or coordinates")
        val root = getRootNode() ?: return ActionResult(false, "Cannot access screen")
        val node = findNodeAtCoordinates(root, action.x, action.y)
        val result = if (node != null && node.isClickable) {
            val r = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            node.recycle()
            r
        } else false
        root.recycle()
        return if (result) ActionResult(true, "Tapped (${action.x},${action.y})") else ActionResult(false, "Nothing clickable at coordinates")
    }

    private suspend fun executeLongPress(action: DeviceAction): ActionResult {
        val root = getRootNode() ?: return ActionResult(false, "Cannot access screen")
        val node = if (action.target != null) findNodeByText(root, action.target) else findNodeAtCoordinates(root, action.x ?: 540, action.y ?: 960)
        val result = if (node != null) { val r = node.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK); node.recycle(); r } else false
        root.recycle()
        return if (result) ActionResult(true, "Long pressed") else ActionResult(false, "Long press failed")
    }

    private suspend fun executeSwipe(x1: Int, y1: Int, x2: Int, y2: Int, duration: Int): ActionResult {
        val service = accessibilityService ?: return ActionResult(false, "No service")
        val path = Path().apply { moveTo(x1.toFloat(), y1.toFloat()); lineTo(x2.toFloat(), y2.toFloat()) }
        val gesture = GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, duration.toLong())).build()
        return if (service.dispatchGesture(gesture, null, null)) ActionResult(true, "Swiped") else ActionResult(false, "Swipe failed")
    }

    private suspend fun executeTypeText(text: String): ActionResult {
        if (text.isBlank()) return ActionResult(false, "No text")
        val root = getRootNode() ?: return ActionResult(false, "Cannot access screen")
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        val result = if (focused != null) {
            val args = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text) }
            val r = focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            focused.recycle()
            r
        } else false
        root.recycle()
        return if (result) ActionResult(true, "Typed text") else ActionResult(false, "Tap an input field first, then try again")
    }

    private fun executeOpenApp(packageName: String): ActionResult {
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                context.startActivity(intent)
                ActionResult(true, "Opened $packageName")
            } else ActionResult(false, "App not installed: $packageName")
        } catch (e: Exception) { ActionResult(false, "Open failed: ${e.localizedMessage}") }
    }

    private fun executeGlobalAction(action: Int, label: String): ActionResult {
        val service = accessibilityService ?: return ActionResult(false, "No service")
        return if (service.performGlobalAction(action)) ActionResult(true, label) else ActionResult(false, "$label failed")
    }

    private fun executeScreenshot(): ActionResult {
        val service = accessibilityService ?: return ActionResult(false, "No service")
        return try {
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT)
            ActionResult(true, "Screenshot captured (check gallery)")
        } catch (e: Exception) { ActionResult(false, "Screenshot failed: ${e.localizedMessage}") }
    }

    private suspend fun executeScroll(up: Boolean): ActionResult {
        val root = getRootNode() ?: return ActionResult(false, "Cannot access screen")
        val action = if (up) AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD else AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        val scrollable = findScrollableNode(root)
        val result = scrollable?.performAction(action) ?: root.performAction(action)
        root.recycle()
        return if (result) ActionResult(true, "Scrolled") else ActionResult(false, "No scrollable area found")
    }

    private suspend fun executeFindAndTap(target: String): ActionResult {
        if (target.isBlank()) return ActionResult(false, "No target specified")
        val root = getRootNode() ?: return ActionResult(false, "Cannot access screen")
        val node = findNodeByText(root, target)
        val result = if (node != null) {
            val clicked = if (node.isClickable) node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            else {
                var parent = node.parent; var c = false
                while (parent != null && !c) { c = parent.isClickable && parent.performAction(AccessibilityNodeInfo.ACTION_CLICK); parent = parent.parent }
                c
            }
            node.recycle()
            clicked
        } else false
        root.recycle()
        return if (result) ActionResult(true, "Tapped '$target'") else ActionResult(false, "Could not find: '$target'")
    }

    // ═══════════════════════════════════════════
    // Screen Reading (NODE-SAFE)
    // ═══════════════════════════════════════════

    suspend fun readScreen(): String? = withContext(Dispatchers.IO) {
        val root = getRootNode() ?: return@withContext null
        val content = extractNodeText(root)
        root.recycle()
        content.take(3000)
    }

    private fun extractNodeText(node: AccessibilityNodeInfo, depth: Int = 0): String {
        if (depth > SCREEN_READ_MAX_DEPTH) return ""
        val sb = StringBuilder()
        val text = node.text?.toString()?.trim()
        val desc = node.contentDescription?.toString()?.trim()
        val prefix = when { node.isEditable -> "[✏️] "; node.isClickable -> "[👆] "; else -> "" }
        if (!text.isNullOrBlank()) sb.appendLine("${"  ".repeat(depth)}$prefix$text")
        if (!desc.isNullOrBlank() && desc != text) sb.appendLine("${"  ".repeat(depth)}($desc)")
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                sb.append(extractNodeText(child, depth + 1))
                child.recycle()
            }
        }
        return sb.toString()
    }

    // ═══════════════════════════════════════════
    // Node Helpers (ALL NON-BLOCKING)
    // ═══════════════════════════════════════════

    private suspend fun getRootNode(): AccessibilityNodeInfo? {
        val service = accessibilityService ?: return null
        var root: AccessibilityNodeInfo? = null
        var waited = 0L
        while (root == null && waited < UI_TIMEOUT_MS) {
            root = service.rootInActiveWindow
            if (root == null) { delay(150); waited += 150 }
        }
        if (root == null) Log.w(TAG, "⚠️ Root node timeout after ${UI_TIMEOUT_MS}ms")
        return root
    }

    private fun findNodeByText(root: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        val visited = mutableSetOf<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            if (node in visited) continue
            visited.add(node)
            val nodeText = node.text?.toString()?.contains(text, true) == true
            val nodeDesc = node.contentDescription?.toString()?.contains(text, true) == true
            if (nodeText || nodeDesc) return node
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { if (it !in visited) queue.add(it) }
            }
        }
        return null
    }

    private fun findScrollableNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            if (node.isScrollable) return node
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return null
    }

    private fun findNodeAtCoordinates(root: AccessibilityNodeInfo, x: Int, y: Int): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            val rect = Rect(); node.getBoundsInScreen(rect)
            if (rect.contains(x, y) && node.isVisibleToUser) return node
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return null
    }

    fun cleanup() { accessibilityService = null; Log.d(TAG, "🧹 Cleaned up") }
}

// ═══════════════════════════════════════════
// Accessibility Service
// ═══════════════════════════════════════════

class ZarpAccessibilityService : AccessibilityService() {
    companion object {
        var instance: ZarpAccessibilityService? = null
            private set
    }
    override fun onServiceConnected() { super.onServiceConnected(); instance = this }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
    override fun onUnbind(intent: Intent?): Boolean { instance = null; return super.onUnbind(intent) }
}

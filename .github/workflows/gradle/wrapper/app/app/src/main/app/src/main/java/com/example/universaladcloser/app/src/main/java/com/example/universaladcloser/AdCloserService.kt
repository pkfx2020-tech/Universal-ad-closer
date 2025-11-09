package com.example.universaladcloser

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast

class AdCloserService : AccessibilityService() {
    
    private val TAG = "AdCloserService"
    private val handler = Handler(Looper.getMainLooper())
    private var lastToastTime = 0L
    private val TOAST_COOLDOWN = 3000L // 3 segundos entre toasts
    
    // Padrões de texto de botões para fechar anúncios
    private val closeButtonTexts = listOf(
        "close", "fechar", "skip", "pular", "dismiss", "x", "×",
        "close ad", "skip ad", "fecha", "sair", "exit", "voltar",
        "continue", "continuar", "ok", "got it", "entendi"
    )
    
    // Padrões de IDs e classes de anúncios
    private val adPatterns = listOf(
        "ad", "ads", "advertisement", "banner", "interstitial",
        "rewarded", "video_ad", "ad_container", "ad_frame",
        "AdView", "AdMob", "publicity", "publicidade", "anuncio"
    )
    
    override fun onServiceConnected() {
        super.onServiceConnected()
        
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                        AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                        AccessibilityEvent.TYPE_VIEW_CLICKED
            
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                   AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            
            notificationTimeout = 100
        }
        
        serviceInfo = info
        Log.d(TAG, "Serviço de fechamento de anúncios iniciado")
        showToast("🛡️ Proteção contra anúncios ativada!")
    }
    
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                // Aguarda um pouco para o anúncio renderizar completamente
                handler.postDelayed({
                    scanAndCloseAds()
                }, 500)
            }
        }
    }
    
    private fun scanAndCloseAds() {
        val rootNode = rootInActiveWindow ?: return
        
        try {
            // Estratégia 1: Procura por botões de fechar explícitos
            if (findAndClickCloseButton(rootNode)) {
                incrementAdCounter()
                return
            }
            
            // Estratégia 2: Procura por containers de anúncios e botões dentro deles
            if (findAdContainerAndClose(rootNode)) {
                incrementAdCounter()
                return
            }
            
            // Estratégia 3: Procura por botões pequenos no canto superior (típico de anúncios)
            if (findCornerCloseButton(rootNode)) {
                incrementAdCounter()
                return
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao escanear anúncios: ${e.message}")
        } finally {
            rootNode.recycle()
        }
    }
    
    private fun findAndClickCloseButton(node: AccessibilityNodeInfo): Boolean {
        // Verifica se o próprio nó é um botão de fechar
        if (isCloseButton(node)) {
            return performClick(node, "Botão de fechar encontrado")
        }
        
        // Procura recursivamente nos filhos
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            try {
                if (findAndClickCloseButton(child)) {
                    return true
                }
            } finally {
                child.recycle()
            }
        }
        
        return false
    }
    
    private fun findAdContainerAndClose(node: AccessibilityNodeInfo): Boolean {
        // Verifica se é um container de anúncio
        if (isAdContainer(node)) {
            // Procura botão de fechar dentro do container
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                try {
                    if (isCloseButton(child)) {
                        return performClick(child, "Anúncio detectado e fechado")
                    }
                } finally {
                    child.recycle()
                }
            }
        }
        
        // Procura recursivamente
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            try {
                if (findAdContainerAndClose(child)) {
                    return true
                }
            } finally {
                child.recycle()
            }
        }
        
        return false
    }
    
    private fun findCornerCloseButton(node: AccessibilityNodeInfo): Boolean {
        val screenBounds = Rect()
        node.getBoundsInScreen(screenBounds)
        val screenWidth = resources.displayMetrics.widthPixels
        val screenHeight = resources.displayMetrics.heightPixels
        
        return findSmallCornerButton(node, screenWidth, screenHeight)
    }
    
    private fun findSmallCornerButton(
        node: AccessibilityNodeInfo,
        screenWidth: Int,
        screenHeight: Int
    ): Boolean {
        if (node.isClickable) {
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            
            val buttonSize = 150 // pixels
            val cornerMargin = 100
            
            // Verifica se está em um dos cantos superiores
            val isTopRight = bounds.right >= screenWidth - cornerMargin &&
                            bounds.top <= cornerMargin
            val isTopLeft = bounds.left <= cornerMargin &&
                           bounds.top <= cornerMargin
            
            // Verifica se é pequeno (típico de botões de fechar)
            val isSmall = bounds.width() <= buttonSize && bounds.height() <= buttonSize
            
            if ((isTopRight || isTopLeft) && isSmall) {
                return performClick(node, "Botão de canto detectado")
            }
        }
        
        // Procura recursivamente
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            try {
                if (findSmallCornerButton(child, screenWidth, screenHeight)) {
                    return true
                }
            } finally {
                child.recycle()
            }
        }
        
        return false
    }
    
    private fun isCloseButton(node: AccessibilityNodeInfo): Boolean {
        if (!node.isClickable && !node.isEnabled) return false
        
        val text = node.text?.toString()?.lowercase() ?: ""
        val contentDesc = node.contentDescription?.toString()?.lowercase() ?: ""
        val viewId = node.viewIdResourceName?.lowercase() ?: ""
        val className = node.className?.toString()?.lowercase() ?: ""
        
        val combinedText = "$text $contentDesc $viewId $className"
        
        return closeButtonTexts.any { pattern ->
            combinedText.contains(pattern)
        }
    }
    
    private fun isAdContainer(node: AccessibilityNodeInfo): Boolean {
        val viewId = node.viewIdResourceName?.lowercase() ?: ""
        val className = node.className?.toString()?.lowercase() ?: ""
        val packageName = node.packageName?.toString()?.lowercase() ?: ""
        
        val combinedInfo = "$viewId $className $packageName"
        
        return adPatterns.any { pattern ->
            combinedInfo.contains(pattern)
        }
    }
    
    private fun performClick(node: AccessibilityNodeInfo, reason: String): Boolean {
        val success = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        
        if (success) {
            Log.d(TAG, "$reason - Clique executado")
            showThrottledToast("✓ Anúncio fechado automaticamente")
        }
        
        return success
    }
    
    private fun incrementAdCounter() {
        val prefs = getSharedPreferences("AdCloserStats", Context.MODE_PRIVATE)
        val current = prefs.getInt("ads_blocked", 0)
        prefs.edit().putInt("ads_blocked", current + 1).apply()
    }
    
    private fun showThrottledToast(message: String) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastToastTime > TOAST_COOLDOWN) {
            showToast(message)
            lastToastTime = currentTime
        }
    }
    
    private fun showToast(message: String) {
        handler.post {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }
    
    override fun onInterrupt() {
        Log.d(TAG, "Serviço interrompido")
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Serviço destruído")
    }
}

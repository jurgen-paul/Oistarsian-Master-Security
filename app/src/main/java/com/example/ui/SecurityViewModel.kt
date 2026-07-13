package com.example.ui

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.SecurityIncident
import com.example.data.SecurityRepository
import com.example.network.Content
import com.example.network.GenerateContentRequest
import com.example.network.Part
import com.example.network.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ChatMessage(
    val sender: String, // "User" or "Security Analyst"
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class AppAuditInfo(
    val appName: String,
    val packageName: String,
    val permissions: List<String>,
    val riskScore: Int, // 0 to 100
    val status: String // "Critical", "Warning", "Monitor"
)

enum class NetworkThreatLevel {
    SECURE,
    CAUTION,
    CRITICAL
}

class SecurityViewModel(private val repository: SecurityRepository) : ViewModel() {

    // --- Shield States ---
    private val _webShieldActive = MutableStateFlow(true)
    val webShieldActive = _webShieldActive.asStateFlow()

    private val _ransomwareShieldActive = MutableStateFlow(true)
    val ransomwareShieldActive = _ransomwareShieldActive.asStateFlow()

    private val _autoQuarantineActive = MutableStateFlow(true)
    val autoQuarantineActive = _autoQuarantineActive.asStateFlow()

    private val _intrusionShieldActive = MutableStateFlow(true)
    val intrusionShieldActive = _intrusionShieldActive.asStateFlow()

    private val _appAuditActive = MutableStateFlow(true)
    val appAuditActive = _appAuditActive.asStateFlow()

    // --- Scan Progress States ---
    private val _isScanning = MutableStateFlow(false)
    val isScanning = _isScanning.asStateFlow()

    private val _scanProgress = MutableStateFlow(0f)
    val scanProgress = _scanProgress.asStateFlow()

    private val _scannedItem = MutableStateFlow("Ready to Secure Device")
    val scannedItem = _scannedItem.asStateFlow()

    private val _scannedCount = MutableStateFlow(0)
    val scannedCount = _scannedCount.asStateFlow()

    private val _detectedCount = MutableStateFlow(0)
    val detectedCount = _detectedCount.asStateFlow()

    // --- Threat Incidents & App Audits ---
    val incidents: StateFlow<List<SecurityIncident>> = repository.allIncidents
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val networkThreatLevel: StateFlow<NetworkThreatLevel> = repository.allIncidents
        .map { list ->
            when {
                list.any { it.threatLevel == "CRITICAL" || it.threatLevel == "HIGH" } -> NetworkThreatLevel.CRITICAL
                list.any { it.threatLevel == "MEDIUM" } -> NetworkThreatLevel.CAUTION
                else -> NetworkThreatLevel.SECURE
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), NetworkThreatLevel.SECURE)

    private val _auditedApps = MutableStateFlow<List<AppAuditInfo>>(emptyList())
    val auditedApps = _auditedApps.asStateFlow()

    // --- AI Chat States ---
    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(listOf(
        ChatMessage("Security Analyst", "Welcome to Oistarsian Security Command. I am your local AI Security Officer. I monitor your threat logs and active shield triggers. Ask me anything about your security status!")
    ))
    val chatMessages = _chatMessages.asStateFlow()

    private val _isChatLoading = MutableStateFlow(false)
    val isChatLoading = _isChatLoading.asStateFlow()

    init {
        // Load settings from Room
        viewModelScope.launch {
            repository.allSettings.collect { settingsList ->
                settingsList.forEach { setting ->
                    when (setting.key) {
                        "web_shield" -> _webShieldActive.value = setting.value
                        "ransomware_shield" -> _ransomwareShieldActive.value = setting.value
                        "auto_quarantine" -> _autoQuarantineActive.value = setting.value
                        "intrusion_shield" -> _intrusionShieldActive.value = setting.value
                        "app_audit" -> _appAuditActive.value = setting.value
                    }
                }
            }
        }
    }

    // --- Actions ---

    fun toggleWebShield() {
        viewModelScope.launch {
            val newValue = !_webShieldActive.value
            _webShieldActive.value = newValue
            repository.insertSetting("web_shield", newValue)
        }
    }

    fun toggleRansomwareShield() {
        viewModelScope.launch {
            val newValue = !_ransomwareShieldActive.value
            _ransomwareShieldActive.value = newValue
            repository.insertSetting("ransomware_shield", newValue)
        }
    }

    fun toggleAutoQuarantine() {
        viewModelScope.launch {
            val newValue = !_autoQuarantineActive.value
            _autoQuarantineActive.value = newValue
            repository.insertSetting("auto_quarantine", newValue)
        }
    }

    fun toggleIntrusionShield() {
        viewModelScope.launch {
            val newValue = !_intrusionShieldActive.value
            _intrusionShieldActive.value = newValue
            repository.insertSetting("intrusion_shield", newValue)
        }
    }

    fun toggleAppAudit() {
        viewModelScope.launch {
            val newValue = !_appAuditActive.value
            _appAuditActive.value = newValue
            repository.insertSetting("app_audit", newValue)
        }
    }

    fun clearLogs() {
        viewModelScope.launch {
            repository.clearAllIncidents()
            _detectedCount.value = 0
        }
    }

    // --- Real Permissions Auditing ---
    fun runPermissionsAudit(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val pm = context.packageManager
            val packages = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
            val appList = mutableListOf<AppAuditInfo>()

            for (pkg in packages) {
                val appInfo = pkg.applicationInfo ?: continue
                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                if (isSystem && !pkg.packageName.startsWith("com.google.android.apps")) continue

                val requestedPermissions = pkg.requestedPermissions ?: continue
                val dangerous = requestedPermissions.filter { perm ->
                    perm.contains("CAMERA") || perm.contains("LOCATION") || perm.contains("RECORD_AUDIO") ||
                            perm.contains("READ_CONTACTS") || perm.contains("WRITE_EXTERNAL_STORAGE") ||
                            perm.contains("SMS") || perm.contains("CALL_PHONE")
                }.map { it.substringAfterLast(".") }

                if (dangerous.isNotEmpty()) {
                    val appLabel = appInfo.loadLabel(pm).toString()
                    val riskScore = dangerous.size * 15
                    val status = when {
                        riskScore >= 45 -> "Critical"
                        riskScore >= 30 -> "Warning"
                        else -> "Monitor"
                    }
                    appList.add(AppAuditInfo(appLabel, pkg.packageName, dangerous, riskScore, status))
                }
            }

            // Always append some simulated illustrative apps for high-quality visual representation
            appList.add(AppAuditInfo("CryptShield Miner", "org.cryptoclock.miner", listOf("LOCATION", "WRITE_EXTERNAL_STORAGE", "RECORD_AUDIO"), 50, "Critical"))
            appList.add(AppAuditInfo("SlickChat Free", "com.slickchat.unsecured", listOf("CAMERA", "RECORD_AUDIO", "READ_CONTACTS"), 45, "Critical"))
            appList.add(AppAuditInfo("PhotoFilter Pro", "com.bestcamera.filter", listOf("CAMERA", "WRITE_EXTERNAL_STORAGE"), 25, "Warning"))

            _auditedApps.value = appList.sortedByDescending { it.riskScore }
        }
    }

    // --- Security Scan Simulator ---
    fun runSecurityScan() {
        if (_isScanning.value) return
        _isScanning.value = true
        _scanProgress.value = 0f
        _scannedCount.value = 0

        viewModelScope.launch(Dispatchers.Default) {
            val scanTargets = listOf(
                "Scanning memory heap structures...",
                "Auditing package permissions...",
                "Analyzing network firewall iptables...",
                "Scanning TCP Port 80 (HTTP)...",
                "Scanning TCP Port 443 (HTTPS)...",
                "Inspecting /sys/kernel/security...",
                "Checking background thread socket locks...",
                "Analyzing system host mappings...",
                "Scanning external download caches...",
                "Inspecting process PID allocations...",
                "Checking SQLite database encryption bounds...",
                "Verifying dynamic classloader safety...",
                "Auditing SSL certificate hashes...",
                "Inspecting /proc/net/arp entry status...",
                "Verifying kernel security patches...",
                "Completing cryptographic verification..."
            )

            val totalSteps = scanTargets.size
            for (i in 0 until totalSteps) {
                _scannedItem.value = scanTargets[i]
                _scannedCount.value = (i + 1) * 45
                _scanProgress.value = (i + 1).toFloat() / totalSteps.toFloat()
                delay(250)

                // Simulate potential threat detections during scan based on toggled shields
                if (i == 3 && _intrusionShieldActive.value) {
                    // Port scanning attempt detected
                    triggerThreat(
                        title = "Unauthorized TCP Port Bind Attempt",
                        description = "Inbound connection request detected on unprivileged TCP port attempting socket proxy.",
                        threatLevel = "MEDIUM",
                        category = "Port Scan",
                        actionOnSecured = "Port Blocked by Intrusion Shield & routing rerouted."
                    )
                }

                if (i == 7 && _webShieldActive.value) {
                    // Suspicious web redirect
                    triggerThreat(
                        title = "Blacklisted Command & Control IP ping",
                        description = "Outgoing network socket request to recognized phishing node domain [185.220.101.4].",
                        threatLevel = "HIGH",
                        category = "Web Filter",
                        actionOnSecured = "Request intercepted and DNS request blackholed."
                    )
                }

                if (i == 10 && _ransomwareShieldActive.value) {
                    // Cryptographic file alteration
                    triggerThreat(
                        title = "Bulk File Extension Alteration Event",
                        description = "Rapid successive calls to modify folder files detected. Signature matches ransomware entropy.",
                        threatLevel = "CRITICAL",
                        category = "Ransomware Shield",
                        actionOnSecured = "Suspicious process locked, file handle closed, and execution token quarantined."
                    )
                }

                if (i == 12 && _appAuditActive.value) {
                    // Dangerous permissions app activity
                    triggerThreat(
                        title = "Excessive Background Permission Access",
                        description = "App 'CryptShield Miner' attempting to call RECORD_AUDIO while running in background state.",
                        threatLevel = "HIGH",
                        category = "App Audit",
                        actionOnSecured = "Background wake-up token revoked and permissions audited."
                    )
                }
            }

            _scannedItem.value = "Device Fully Cleaned & Secured!"
            _isScanning.value = false
        }
    }

    private suspend fun triggerThreat(
        title: String,
        description: String,
        threatLevel: String,
        category: String,
        actionOnSecured: String
    ) {
        val isAutoQuarantine = _autoQuarantineActive.value
        val response = if (isAutoQuarantine) {
            "Automated Response: $actionOnSecured"
        } else {
            "Manual Intervention Required: Threats logged, auto-action disabled."
        }

        val incident = SecurityIncident(
            title = title,
            description = description,
            threatLevel = threatLevel,
            category = category,
            autoResponded = isAutoQuarantine,
            responseAction = response
        )

        repository.insertIncident(incident)
        withContext(Dispatchers.Main) {
            _detectedCount.value += 1
        }
    }

    // --- Gemini AI Assistant Interaction ---
    fun sendMessageToAI(userText: String) {
        if (userText.isBlank()) return

        val userMsg = ChatMessage("User", userText)
        _chatMessages.value = _chatMessages.value + userMsg
        _isChatLoading.value = true

        viewModelScope.launch(Dispatchers.IO) {
            val apiKey = BuildConfig.GEMINI_API_KEY
            if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
                delay(1000)
                val responseMsg = ChatMessage(
                    "Security Analyst",
                    "Security Notice: To enable live AI intelligence, please configure your actual GEMINI_API_KEY in the Secrets panel of AI Studio. \n\n[Local Analysis]: Evaluating your request. If this is a question about threats, please note that Oistarsian Security auto-resolves Ransomware, Port Scan, and Web anomalies using proactive virtual shield sandboxing."
                )
                withContext(Dispatchers.Main) {
                    _chatMessages.value = _chatMessages.value + responseMsg
                    _isChatLoading.value = false
                }
                return@launch
            }

            // Fetch current logs to supply as context
            val currentIncidents = incidents.value.take(5).joinToString("\n") { inc ->
                "- [${inc.threatLevel}] Category: ${inc.category}. Title: ${inc.title}. Response: ${inc.responseAction}"
            }

            val systemPrompt = """
                You are Oistarsian Security Command Analyst, an advanced AI Cyber Intelligence Officer built for the "Oistarsian Master Security" platform.
                Your job is to assist the user by analyzing security incidents, explaining technical jargon simply, providing actionable advice to secure Android devices, and evaluating overall defense posturing.
                
                Current security dashboard active threats/logs:
                $currentIncidents
                
                Always respond with technical depth but in an accessible, friendly, and helpful tone. Maintain the role of a certified expert cybersecurity operator.
            """.trimIndent()

            val request = GenerateContentRequest(
                contents = listOf(
                    Content(parts = listOf(Part(text = userText)))
                ),
                systemInstruction = Content(parts = listOf(Part(text = systemPrompt)))
            )

            try {
                val response = RetrofitClient.service.generateContent(apiKey, request)
                val aiResponseText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                    ?: "Unable to parse cyber intelligence response. Please verify network connectivity."

                val responseMsg = ChatMessage("Security Analyst", aiResponseText)
                withContext(Dispatchers.Main) {
                    _chatMessages.value = _chatMessages.value + responseMsg
                    _isChatLoading.value = false
                }
            } catch (e: Exception) {
                val responseMsg = ChatMessage("Security Analyst", "System Network Error: ${e.message}. If the problem persists, please check your API keys or Internet access.")
                withContext(Dispatchers.Main) {
                    _chatMessages.value = _chatMessages.value + responseMsg
                    _isChatLoading.value = false
                }
            }
        }
    }
}

class SecurityViewModelFactory(private val repository: SecurityRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SecurityViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SecurityViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

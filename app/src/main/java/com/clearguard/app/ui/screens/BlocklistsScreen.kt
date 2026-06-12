package com.clearguard.app.ui.screens

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.runtime.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.clearguard.app.ui.components.LiquidGlassButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clearguard.app.PreferenceKeys
import com.clearguard.app.blocking.BlocklistUpdater
import com.clearguard.app.blocking.HostBlocker
import com.clearguard.app.ui.components.ClearSwitch
import com.clearguard.app.ui.components.GlassCard
import com.clearguard.app.ui.components.LiquidGlassButton
import com.clearguard.app.ui.components.LiquidGlassIconButton
import com.clearguard.app.ui.theme.ClearColors
import com.clearguard.app.ui.theme.ClearDesign
import com.clearguard.app.vpn.ClearGuardVpnService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.net.URI
import java.text.DateFormat
import java.util.Date
import java.util.Locale

data class BlocklistRow(val name: String, val count: String, val source: String)

data class Conflict(
    val domain: String,
    val type: String,
    val description: String,
    val actions: List<String>
)

@Composable
fun BlocklistsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { PreferenceKeys.prefs(context) }

    var updating by remember { mutableStateOf(false) }
    var updateMessage by remember { mutableStateOf("") }
    var activeHosts by remember { mutableStateOf(HostBlocker.get(context).snapshot().blockedHostCount) }
    var downloadedHosts by remember { mutableStateOf(prefs.getInt(PreferenceKeys.KEY_LAST_UPDATE_COUNT, 0)) }

    // Filter sources (the blocklist URLs) — user-editable.
    val sources = remember { mutableStateListOf<String>().apply { addAll(loadSources(prefs)) } }
    var disabledSources by remember { mutableStateOf(loadDisabledSources(prefs)) }
    var newSource by remember { mutableStateOf("") }
    var sourceError by remember { mutableStateOf("") }

    // Personal block / allow rules — user-editable, applied live.
    val customBlocks = remember {
        mutableStateListOf<String>().apply {
            addAll(PreferenceKeys.stringSetSorted(context, PreferenceKeys.KEY_CUSTOM_BLOCKS))
        }
    }
    var newBlock by remember { mutableStateOf("") }
    var blockError by remember { mutableStateOf("") }

    val securityBlocks = remember {
        mutableStateListOf<String>().apply {
            addAll(PreferenceKeys.stringSetSorted(context, PreferenceKeys.KEY_SECURITY_BLOCKS))
        }
    }
    var newSecurityBlock by remember { mutableStateOf("") }
    var securityBlockError by remember { mutableStateOf("") }

    val allowList = remember {
        mutableStateListOf<String>().apply {
            addAll(PreferenceKeys.stringSetSorted(context, PreferenceKeys.KEY_ALLOWLIST))
        }
    }
    var newAllow by remember { mutableStateOf("") }
    var allowError by remember { mutableStateOf("") }

    // Filter conflict detector state
    var conflicts by remember { mutableStateOf(emptyList<Conflict>()) }
    var lastConflictCheck by remember { mutableStateOf(0L) }

    // Auto-detect conflicts on load / when user rules change (convenient for the detector feature)
    LaunchedEffect(customBlocks.size, securityBlocks.size, allowList.size) {
        if (customBlocks.isNotEmpty() || securityBlocks.isNotEmpty() || allowList.isNotEmpty()) {
            conflicts = detectConflicts(context, customBlocks, securityBlocks, allowList)
            lastConflictCheck = System.currentTimeMillis()
        }
    }

    fun refreshActiveHosts() {
        activeHosts = HostBlocker.get(context).snapshot().blockedHostCount
    }

    // Re-read lists into the singleton and tell a running VPN to drop its cache.
    // reload() re-parses the downloaded hosts file, so keep it off the main thread.
    fun applyRulesChanged() {
        scope.launch {
            withContext(Dispatchers.IO) {
                HostBlocker.get(context).reload()
                ClearGuardVpnService.reloadIfRunning(context)
            }
            refreshActiveHosts()
        }
    }

    fun addSource() {
        sourceError = ""
        val normalized = normalizeSourceUrl(newSource)
        if (normalized == null) {
            sourceError = "Enter a valid https:// blocklist URL"
            return
        }
        if (sources.any { it.equals(normalized, ignoreCase = true) }) {
            sourceError = "That source is already added"
            return
        }
        sources.add(normalized)
        sources.sort()
        persistSources(prefs, sources)
        disabledSources = disabledSources - normalized
        persistDisabledSources(prefs, disabledSources)
        newSource = ""
        updateMessage = "Source added — tap Update to download it"
    }

    fun removeSource(url: String) {
        sources.remove(url)
        persistSources(prefs, sources)
        disabledSources = disabledSources - url
        persistDisabledSources(prefs, disabledSources)
    }

    fun setSourceEnabled(url: String, enabled: Boolean) {
        disabledSources = if (enabled) {
            disabledSources - url
        } else {
            disabledSources + url
        }
        persistDisabledSources(prefs, disabledSources)
        updateMessage = if (enabled) {
            "${PreferenceKeys.sourceDisplayName(url)} enabled — tap Update to refresh"
        } else {
            "${PreferenceKeys.sourceDisplayName(url)} paused — tap Update to rebuild"
        }
    }

    fun addCustomBlock() {
        blockError = ""
        val normalized = HostBlocker.normalizeDomain(newBlock)
        if (normalized == null) {
            blockError = "Enter a valid domain like ads.example.com"
            return
        }
        if (!PreferenceKeys.addToStringSet(context, PreferenceKeys.KEY_CUSTOM_BLOCKS, normalized)) {
            blockError = "Already in your blocks"
            return
        }
        customBlocks.add(normalized)
        customBlocks.sort()
        newBlock = ""
        applyRulesChanged()
    }

    fun removeCustomBlock(domain: String) {
        PreferenceKeys.removeFromStringSet(context, PreferenceKeys.KEY_CUSTOM_BLOCKS, domain)
        customBlocks.remove(domain)
        applyRulesChanged()
    }

    fun addSecurityBlock() {
        securityBlockError = ""
        val normalized = HostBlocker.normalizeDomain(newSecurityBlock)
        if (normalized == null) {
            securityBlockError = "Enter a valid domain like phish.example.com"
            return
        }
        if (!PreferenceKeys.addToStringSet(context, PreferenceKeys.KEY_SECURITY_BLOCKS, normalized)) {
            securityBlockError = "Already in your threat blocks"
            return
        }
        securityBlocks.add(normalized)
        securityBlocks.sort()
        newSecurityBlock = ""
        applyRulesChanged()
    }

    fun removeSecurityBlock(domain: String) {
        PreferenceKeys.removeFromStringSet(context, PreferenceKeys.KEY_SECURITY_BLOCKS, domain)
        securityBlocks.remove(domain)
        applyRulesChanged()
    }

    fun addAllow() {
        allowError = ""
        val normalized = HostBlocker.normalizeDomain(newAllow)
        if (normalized == null) {
            allowError = "Enter a valid domain like cdn.example.com"
            return
        }
        if (!PreferenceKeys.addToStringSet(context, PreferenceKeys.KEY_ALLOWLIST, normalized)) {
            allowError = "Already allowed"
            return
        }
        allowList.add(normalized)
        allowList.sort()
        newAllow = ""
        applyRulesChanged()
    }

    fun removeAllow(domain: String) {
        PreferenceKeys.removeFromStringSet(context, PreferenceKeys.KEY_ALLOWLIST, domain)
        allowList.remove(domain)
        applyRulesChanged()
    }

    // Reloads every list shown on this screen from preferences (used after import).
    fun refreshAllFromPrefs() {
        sources.clear()
        sources.addAll(loadSources(prefs))
        disabledSources = loadDisabledSources(prefs)
        customBlocks.clear()
        customBlocks.addAll(PreferenceKeys.stringSetSorted(context, PreferenceKeys.KEY_CUSTOM_BLOCKS))
        securityBlocks.clear()
        securityBlocks.addAll(PreferenceKeys.stringSetSorted(context, PreferenceKeys.KEY_SECURITY_BLOCKS))
        allowList.clear()
        allowList.addAll(PreferenceKeys.stringSetSorted(context, PreferenceKeys.KEY_ALLOWLIST))
        refreshActiveHosts()
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                updateMessage = withContext(Dispatchers.IO) {
                    try {
                        val stream = context.contentResolver.openOutputStream(uri)
                            ?: return@withContext "Could not open the destination file"
                        stream.use { it.write(buildBackupJson(context).toByteArray(Charsets.UTF_8)) }
                        "Backup exported"
                    } catch (e: Exception) {
                        "Export failed: ${e.message}"
                    }
                }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    try {
                        val text = context.contentResolver.openInputStream(uri)
                            ?.bufferedReader()?.use { it.readText() }
                            ?: return@withContext "Could not read the selected file"
                        applyBackupJson(context, text)
                    } catch (e: Exception) {
                        "Import failed: ${e.message}"
                    }
                }
                refreshAllFromPrefs()
                updateMessage = result
            }
        }
    }

    val rows = listOf(
        BlocklistRow("Built-in starter list", "12", "Bundled asset"),
        BlocklistRow("Downloaded hosts", formatCount(downloadedHosts), "Manual update")
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = ClearDesign.screenHPadding, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Blocklists", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            LiquidGlassButton(
                onClick = {
                    scope.launch {
                        updating = true
                        updateMessage = ""
                        val result = withContext(Dispatchers.IO) {
                            // updateNow() reloads the HostBlocker singleton on success.
                            val r = BlocklistUpdater.updateNow(context.applicationContext)
                            ClearGuardVpnService.reloadIfRunning(context)
                            r
                        }
                        refreshActiveHosts()
                        downloadedHosts = prefs.getInt(PreferenceKeys.KEY_LAST_UPDATE_COUNT, 0)
                        updateMessage = if (result.success) {
                            result.message.ifBlank {
                                "Loaded ${formatCount(result.hostCount)} domains from " +
                                    "${result.successfulSources}/${result.totalSources} sources"
                            }
                        } else {
                            result.message
                        }
                        updating = false
                    }
                },
                modifier = Modifier
                    .height(40.dp)
                    .width(140.dp),
                enabled = !updating,
                cornerRadius = 20.dp,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp)
            ) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    if (updating) "Updating" else "Update",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        Text(
            "${formatCount(activeHosts)} active blocked domains. Last update: ${lastUpdateLabel(prefs.getLong(PreferenceKeys.KEY_LAST_UPDATE_MILLIS, 0L))}",
            fontSize = 12.sp,
            color = ClearColors.muted,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
        )
        if (updateMessage.isNotBlank()) {
            Text(
                updateMessage,
                fontSize = 12.sp,
                color = ClearColors.green,
                modifier = Modifier.padding(start = 4.dp, bottom = 10.dp)
            )
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f)
        ) {
            // --- Filter sources ---
            item { SectionLabel("Filter sources") }
            item {
                HelpText("Use the sliders to enable or pause each trusted list. Changes apply after Update.")
            }
            item {
                AddDomainField(
                    value = newSource,
                    onValueChange = {
                        newSource = it
                        if (sourceError.isNotEmpty()) sourceError = ""
                    },
                    onAdd = { addSource() },
                    label = "Add filter URL",
                    placeholder = "https://example.com/hosts.txt"
                )
            }
            if (sourceError.isNotBlank()) {
                item { ErrorText(sourceError) }
            }
            if (sources.isEmpty()) {
                item { HelpText("No sources yet. Add one above to start downloading filters.") }
            } else {
                items(sources, key = { "src:$it" }) { url ->
                    EntryRow(
                        title = sourceTitle(url),
                        subtitle = url,
                        enabled = url !in disabledSources,
                        onEnabledChange = { enabled -> setSourceEnabled(url, enabled) },
                        onRemove = null
                    )
                }
            }

            // --- Personal Block AI (natural language) ---
            item {
                GlassCard {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Personal Block AI", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = ClearColors.green)
                        Text("Describe what to block. Example: \"Block all betting ads and loan ads.\"", fontSize = 12.sp, color = ClearColors.muted)
                        Spacer(Modifier.height(8.dp))

                        var aiQuery by remember { mutableStateOf("") }
                        OutlinedTextField(
                            value = aiQuery,
                            onValueChange = { aiQuery = it },
                            placeholder = { Text("Block betting, fake loans, investment scams...", fontSize = 13.sp) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = {
                                if (aiQuery.isNotBlank()) {
                                    val added = applyPersonalBlockAI(context, aiQuery)
                                    customBlocks.addAll(added)
                                    aiQuery = ""
                                }
                            })
                        )
                        Spacer(Modifier.height(8.dp))
                        LiquidGlassButton(
                            onClick = {
                                if (aiQuery.isNotBlank()) {
                                    val added = applyPersonalBlockAI(context, aiQuery)
                                    customBlocks.addAll(added)
                                    aiQuery = ""
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Parse & Apply Rules")
                        }
                    }
                }
            }

            // --- Filter Conflict Detector ---
            item {
                GlassCard {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Filter Conflict Detector", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = ClearColors.green)
                        Text("Detect overlapping or contradictory rules between custom blocks, security blocks, allowlist, and downloaded filters.", fontSize = 12.sp, color = ClearColors.muted)
                        Spacer(Modifier.height(8.dp))

                        LiquidGlassButton(
                            onClick = {
                                val newConflicts = detectConflicts(
                                    context,
                                    customBlocks,
                                    securityBlocks,
                                    allowList
                                )
                                conflicts = newConflicts
                                lastConflictCheck = System.currentTimeMillis()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Run Conflict Detector")
                        }

                        if (conflicts.isNotEmpty()) {
                            Spacer(Modifier.height(12.dp))
                            Text("${conflicts.size} conflict(s) found:", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                            conflicts.forEach { conflict ->
                                Spacer(Modifier.height(6.dp))
                                GlassCard(glassAlpha = 0.7f) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(conflict.domain, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = ClearColors.danger)
                                        Text("${conflict.type}: ${conflict.description}", fontSize = 12.sp, color = ClearColors.text)
                                        Spacer(Modifier.height(8.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            conflict.actions.forEach { action ->
                                                LiquidGlassButton(
                                                    onClick = {
                                                        when (action) {
                                                            "Remove from allowlist" -> {
                                                                removeAllow(conflict.domain)
                                                                applyRulesChanged()
                                                            }
                                                            "Remove from custom blocks" -> {
                                                                removeCustomBlock(conflict.domain)
                                                                applyRulesChanged()
                                                            }
                                                            "Remove from security blocks" -> {
                                                                removeSecurityBlock(conflict.domain)
                                                                applyRulesChanged()
                                                            }
                                                        }
                                                        // Re-run detection after resolve
                                                        conflicts = detectConflicts(context, customBlocks, securityBlocks, allowList)
                                                    },
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Text(action, fontSize = 11.sp)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } else if (lastConflictCheck > 0) {
                            Spacer(Modifier.height(8.dp))
                            Text("No conflicts detected in your user rules.", fontSize = 12.sp, color = ClearColors.muted)
                        }
                    }
                }
            }

            // Wire risk into custom block auto-suggest: High Risk Phones from local FRI DB
            item {
                GlassCard {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Suggested High-Risk Phones (FRI DB)", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = ClearColors.danger)
                        Text("From local fri_risk_db.txt + runtime seeds. Tap to add to security blocks (auto-suggest for blocking high-risk senders).", fontSize = 11.sp, color = ClearColors.muted)
                        Spacer(Modifier.height(8.dp))

                        val highRisk = remember { com.clearguard.app.security.OnDeviceRuleEngine.getHighRiskPhones(10) }
                        if (highRisk.isEmpty()) {
                            Text("No high-risk entries loaded.", fontSize = 12.sp, color = ClearColors.muted)
                        } else {
                            highRisk.forEach { num ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(num, modifier = Modifier.weight(1f), fontSize = 13.sp)
                                    LiquidGlassButton(
                                        onClick = {
                                            val marker = "phone:$num"
                                            if (PreferenceKeys.addToStringSet(context, PreferenceKeys.KEY_SECURITY_BLOCKS, marker)) {
                                                securityBlocks.add(marker)
                                                applyRulesChanged()
                                            }
                                        },
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Text("Add to Security", fontSize = 10.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // --- Custom blocks ---
            item {
                Spacer(Modifier.height(6.dp))
                SectionLabel("Custom blocks (${customBlocks.size})")
            }
            item { HelpText("Domains you always want blocked. Subdomains are blocked too.") }
            item {
                AddDomainField(
                    value = newBlock,
                    onValueChange = {
                        newBlock = it
                        if (blockError.isNotEmpty()) blockError = ""
                    },
                    onAdd = { addCustomBlock() },
                    label = "Block a domain",
                    placeholder = "ads.example.com"
                )
            }
            if (blockError.isNotBlank()) {
                item { ErrorText(blockError) }
            }
            items(customBlocks, key = { "block:$it" }) { domain ->
                EntryRow(title = domain, subtitle = null, onRemove = { removeCustomBlock(domain) })
            }

            // --- Security blocks ---
            item {
                Spacer(Modifier.height(6.dp))
                SectionLabel("Security blocks (${securityBlocks.size})")
            }
            item { HelpText("Domains to block for strict security (scam, malware, phishing).") }
            item {
                AddDomainField(
                    value = newSecurityBlock,
                    onValueChange = {
                        newSecurityBlock = it
                        if (securityBlockError.isNotEmpty()) securityBlockError = ""
                    },
                    onAdd = { addSecurityBlock() },
                    label = "Block a security threat domain",
                    placeholder = "phish.example.com"
                )
            }
            if (securityBlockError.isNotBlank()) {
                item { ErrorText(securityBlockError) }
            }
            items(securityBlocks, key = { "sec:$it" }) { domain ->
                EntryRow(title = domain, subtitle = null, onRemove = { removeSecurityBlock(domain) })
            }

            // --- Allowlist ---
            item {
                Spacer(Modifier.height(6.dp))
                SectionLabel("Allowlist (${allowList.size})")
            }
            item { HelpText("Domains that are never blocked, even if a filter list contains them.") }
            item {
                AddDomainField(
                    value = newAllow,
                    onValueChange = {
                        newAllow = it
                        if (allowError.isNotEmpty()) allowError = ""
                    },
                    onAdd = { addAllow() },
                    label = "Allow a domain",
                    placeholder = "cdn.example.com"
                )
            }
            if (allowError.isNotBlank()) {
                item { ErrorText(allowError) }
            }
            items(allowList, key = { "allow:$it" }) { domain ->
                EntryRow(title = domain, subtitle = null, onRemove = { removeAllow(domain) })
            }

            // --- Backup ---
            item {
                Spacer(Modifier.height(6.dp))
                SectionLabel("Backup")
            }
            item {
                HelpText("Export sources, rules, exclusions, and settings to a JSON file, or restore them from a backup.")
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    LiquidGlassButton(
                        onClick = { exportLauncher.launch("clearguard-backup.json") },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.Default.Download,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Text("Export", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                    LiquidGlassButton(
                        onClick = {
                            importLauncher.launch(
                                arrayOf("application/json", "text/*", "application/octet-stream")
                            )
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.Default.Upload,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Text("Import", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                }
            }

            // --- Summary ---
            item {
                Spacer(Modifier.height(6.dp))
                SectionLabel("Lists")
            }
            items(rows, key = { "row:${it.name}" }) { row ->
                GlassListItem(row)
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 4.dp, top = 14.dp, bottom = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(ClearColors.green)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = ClearColors.text
        )
    }
}

@Composable
private fun HelpText(text: String) {
    Text(
        text,
        fontSize = 12.sp,
        color = ClearColors.muted,
        modifier = Modifier.padding(start = 4.dp)
    )
}

@Composable
private fun ErrorText(text: String) {
    Text(
        text,
        fontSize = 12.sp,
        color = ClearColors.danger,
        modifier = Modifier.padding(start = 4.dp)
    )
}

@Composable
private fun AddDomainField(
    value: String,
    onValueChange: (String) -> Unit,
    onAdd: () -> Unit,
    label: String,
    placeholder: String
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Uri,
            imeAction = ImeAction.Done
        ),
        keyboardActions = KeyboardActions(onDone = { onAdd() }),
        trailingIcon = {
            LiquidGlassIconButton(
                onClick = onAdd,
                enabled = value.isNotBlank(),
                size = 34.dp
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Add",
                    modifier = Modifier.size(17.dp)
                )
            }
        }
    )
}

@Composable
private fun EntryRow(
    title: String,
    subtitle: String?,
    enabled: Boolean? = null,
    onEnabledChange: ((Boolean) -> Unit)? = null,
    onRemove: (() -> Unit)? = null
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 18.dp,
        glassAlpha = 0.82f,
        elevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 6.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle != null) {
                    Text(
                        subtitle,
                        fontSize = 11.sp,
                        color = ClearColors.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (enabled != null && onEnabledChange != null) {
                Spacer(Modifier.width(10.dp))
                ClearSwitch(
                    checked = enabled,
                    onCheckedChange = onEnabledChange
                )
            }
            if (onRemove != null) {
                Spacer(Modifier.width(8.dp))
                LiquidGlassIconButton(
                    onClick = onRemove,
                    accent = ClearColors.danger,
                    contentColor = ClearColors.danger,
                    size = 34.dp
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Remove",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun GlassListItem(row: BlocklistRow) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 18.dp,
        glassAlpha = 0.82f,
        elevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(row.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Text("${row.count} domains", fontSize = 12.sp, color = ClearColors.muted)
            }
            Text(row.source, fontSize = 12.sp, color = ClearColors.muted)
        }
    }
}

/** Serializes all user data (rules, sources, exclusions, settings) to pretty JSON. */
private fun buildBackupJson(context: android.content.Context): String {
    val prefs = PreferenceKeys.prefs(context)
    val root = JSONObject()
    root.put("app", "ShieldDNS")
    root.put("backup_version", 1)
    root.put("exported_at_millis", System.currentTimeMillis())
    root.put("sources", JSONArray(PreferenceKeys.stringSetSorted(context, PreferenceKeys.KEY_SOURCE_URLS)))
    root.put("disabled_sources", JSONArray(PreferenceKeys.stringSetSorted(context, PreferenceKeys.KEY_DISABLED_SOURCE_URLS)))
    root.put("custom_blocks", JSONArray(PreferenceKeys.stringSetSorted(context, PreferenceKeys.KEY_CUSTOM_BLOCKS)))
    root.put("security_blocks", JSONArray(PreferenceKeys.stringSetSorted(context, PreferenceKeys.KEY_SECURITY_BLOCKS)))
    root.put("allowlist", JSONArray(PreferenceKeys.stringSetSorted(context, PreferenceKeys.KEY_ALLOWLIST)))
    root.put("excluded_apps", JSONArray(PreferenceKeys.stringSetSorted(context, PreferenceKeys.KEY_EXCLUDED_APPS)))

    val settings = JSONObject()
    settings.put("scam_shield_enabled", prefs.getBoolean(PreferenceKeys.KEY_SCAM_SHIELD_ENABLED, PreferenceKeys.DEFAULT_SCAM_SHIELD_ENABLED))
    settings.put("indian_scam_shield_enabled", prefs.getBoolean(PreferenceKeys.KEY_INDIAN_SCAM_SHIELD_ENABLED, PreferenceKeys.DEFAULT_INDIAN_SCAM_SHIELD_ENABLED))
    settings.put("doh_enabled", prefs.getBoolean(PreferenceKeys.KEY_DOH_ENABLED, PreferenceKeys.DEFAULT_DOH_ENABLED))
    settings.put("doh_url", prefs.getString(PreferenceKeys.KEY_DOH_URL, PreferenceKeys.DEFAULT_DOH_URL))
    settings.put("doh_provider", prefs.getString(PreferenceKeys.KEY_DOH_PROVIDER, PreferenceKeys.DEFAULT_DOH_PROVIDER))
    settings.put("upstream_dns", prefs.getString(PreferenceKeys.KEY_UPSTREAM_DNS, PreferenceKeys.DEFAULT_UPSTREAM_DNS))
    settings.put("cache_ttl_seconds", prefs.getInt(PreferenceKeys.KEY_CACHE_TTL_SECONDS, PreferenceKeys.DEFAULT_CACHE_TTL_SECONDS))
    settings.put("bypass_guard_enabled", prefs.getBoolean(PreferenceKeys.KEY_BYPASS_GUARD_ENABLED, PreferenceKeys.DEFAULT_BYPASS_GUARD_ENABLED))
    settings.put("auto_update_enabled", prefs.getBoolean(PreferenceKeys.KEY_AUTO_UPDATE_ENABLED, PreferenceKeys.DEFAULT_AUTO_UPDATE_ENABLED))
    settings.put("resume_on_boot", prefs.getBoolean(PreferenceKeys.KEY_RESUME_ON_BOOT, PreferenceKeys.DEFAULT_RESUME_ON_BOOT))
    settings.put("theme_mode", prefs.getString(PreferenceKeys.KEY_THEME_MODE, PreferenceKeys.DEFAULT_THEME_MODE))
    root.put("settings", settings)

    return root.toString(2)
}

/**
 * Validates and applies a backup. Lists replace the stored ones wholesale; every
 * entry is re-normalized so a hand-edited file cannot inject invalid rules.
 * Returns a user-facing status message. Runs on a background thread.
 */
private fun applyBackupJson(context: android.content.Context, raw: String): String {
    val root = try {
        JSONObject(raw)
    } catch (e: JSONException) {
        return "Import failed: not a valid backup file"
    }
    if (!root.has("sources") && !root.has("custom_blocks") &&
        !root.has("allowlist") && !root.has("settings")
    ) {
        return "Import failed: not a ShieldDNS backup"
    }

    val prefs = PreferenceKeys.prefs(context)
    val editor = prefs.edit()
    var imported = 0

    root.optJSONArray("sources")?.let { array ->
        val cleaned = LinkedHashSet<String>()
        for (i in 0 until array.length()) {
            normalizeSourceUrl(array.optString(i))?.let { cleaned.add(it) }
        }
        editor.putStringSet(PreferenceKeys.KEY_SOURCE_URLS, cleaned)
        imported += cleaned.size
    }
    root.optJSONArray("disabled_sources")?.let { array ->
        val cleaned = LinkedHashSet<String>()
        for (i in 0 until array.length()) {
            normalizeSourceUrl(array.optString(i))?.let { cleaned.add(it) }
        }
        editor.putStringSet(PreferenceKeys.KEY_DISABLED_SOURCE_URLS, cleaned)
    }
    root.optJSONArray("custom_blocks")?.let { array ->
        val cleaned = LinkedHashSet<String>()
        for (i in 0 until array.length()) {
            HostBlocker.normalizeDomain(array.optString(i))?.let { cleaned.add(it) }
        }
        editor.putStringSet(PreferenceKeys.KEY_CUSTOM_BLOCKS, cleaned)
        imported += cleaned.size
    }
    root.optJSONArray("security_blocks")?.let { array ->
        val cleaned = LinkedHashSet<String>()
        for (i in 0 until array.length()) {
            HostBlocker.normalizeDomain(array.optString(i))?.let { cleaned.add(it) }
        }
        editor.putStringSet(PreferenceKeys.KEY_SECURITY_BLOCKS, cleaned)
        imported += cleaned.size
    }
    root.optJSONArray("allowlist")?.let { array ->
        val cleaned = LinkedHashSet<String>()
        for (i in 0 until array.length()) {
            HostBlocker.normalizeDomain(array.optString(i))?.let { cleaned.add(it) }
        }
        editor.putStringSet(PreferenceKeys.KEY_ALLOWLIST, cleaned)
        imported += cleaned.size
    }
    root.optJSONArray("excluded_apps")?.let { array ->
        val cleaned = LinkedHashSet<String>()
        for (i in 0 until array.length()) {
            val packageName = array.optString(i).trim()
            if (packageName.isNotEmpty()) {
                cleaned.add(packageName)
            }
        }
        editor.putStringSet(PreferenceKeys.KEY_EXCLUDED_APPS, cleaned)
    }

    root.optJSONObject("settings")?.let { settings ->
        if (settings.has("scam_shield_enabled")) {
            editor.putBoolean(PreferenceKeys.KEY_SCAM_SHIELD_ENABLED, settings.optBoolean("scam_shield_enabled", PreferenceKeys.DEFAULT_SCAM_SHIELD_ENABLED))
            if (settings.has("indian_scam_shield_enabled")) {
                editor.putBoolean(PreferenceKeys.KEY_INDIAN_SCAM_SHIELD_ENABLED, settings.optBoolean("indian_scam_shield_enabled", PreferenceKeys.DEFAULT_INDIAN_SCAM_SHIELD_ENABLED))
            }
        }
        if (settings.has("doh_enabled")) {
            editor.putBoolean(PreferenceKeys.KEY_DOH_ENABLED, settings.optBoolean("doh_enabled", PreferenceKeys.DEFAULT_DOH_ENABLED))
        }
        val dohUrl = settings.optString("doh_url", "")
        if (dohUrl.startsWith("https://", ignoreCase = true) && dohUrl.length > 12) {
            editor.putString(PreferenceKeys.KEY_DOH_URL, dohUrl)
        }
        val dohProvider = settings.optString("doh_provider", "")
        if (dohProvider.isNotEmpty()) {
            editor.putString(PreferenceKeys.KEY_DOH_PROVIDER, dohProvider)
        }
        val upstream = settings.optString("upstream_dns", "")
        if (upstream.isNotEmpty()) {
            editor.putString(PreferenceKeys.KEY_UPSTREAM_DNS, upstream)
        }
        val ttl = settings.optInt("cache_ttl_seconds", -1)
        if (ttl in 30..900) {
            editor.putInt(PreferenceKeys.KEY_CACHE_TTL_SECONDS, ttl)
        }
        if (settings.has("bypass_guard_enabled")) {
            editor.putBoolean(PreferenceKeys.KEY_BYPASS_GUARD_ENABLED, settings.optBoolean("bypass_guard_enabled", PreferenceKeys.DEFAULT_BYPASS_GUARD_ENABLED))
        }
        if (settings.has("auto_update_enabled")) {
            editor.putBoolean(PreferenceKeys.KEY_AUTO_UPDATE_ENABLED, settings.optBoolean("auto_update_enabled", PreferenceKeys.DEFAULT_AUTO_UPDATE_ENABLED))
        }
        if (settings.has("resume_on_boot")) {
            editor.putBoolean(PreferenceKeys.KEY_RESUME_ON_BOOT, settings.optBoolean("resume_on_boot", PreferenceKeys.DEFAULT_RESUME_ON_BOOT))
        }
        val themeMode = settings.optString("theme_mode", "")
        if (themeMode in listOf("system", "light", "dark")) {
            editor.putString(PreferenceKeys.KEY_THEME_MODE, themeMode)
        }
    }

    editor.apply()
    HostBlocker.get(context).reload()
    ClearGuardVpnService.reloadIfRunning(context)
    return "Backup imported ($imported list entries). Appearance changes apply on next launch."
}

private fun loadSources(prefs: android.content.SharedPreferences): List<String> {
    val stored = prefs.getStringSet(PreferenceKeys.KEY_SOURCE_URLS, PreferenceKeys.defaultSources())
        ?: PreferenceKeys.defaultSources()
    return stored.sorted()
}

private fun loadDisabledSources(prefs: android.content.SharedPreferences): Set<String> {
    val stored = prefs.getStringSet(PreferenceKeys.KEY_DISABLED_SOURCE_URLS, emptySet())
        ?: emptySet()
    return stored.toSet()
}

private fun persistSources(prefs: android.content.SharedPreferences, sources: List<String>) {
    prefs.edit()
        .putStringSet(PreferenceKeys.KEY_SOURCE_URLS, LinkedHashSet(sources))
        .apply()
}

private fun persistDisabledSources(prefs: android.content.SharedPreferences, sources: Set<String>) {
    prefs.edit()
        .putStringSet(PreferenceKeys.KEY_DISABLED_SOURCE_URLS, LinkedHashSet(sources))
        .apply()
}

// === Personal Block AI Parser (simple on-device natural language → custom blocks) ===
private fun applyPersonalBlockAI(context: Context, query: String): List<String> {
    val lower = query.lowercase()
    val termsToBlock = mutableSetOf<String>()

    val categoryMap = mapOf(
        "bet" to listOf("bet", "dream11", "rummy", "casino", "1xbet", "dafabet", "betway", "gambl", "lottery"),
        "loan" to listOf("loan", "instantloan", "quickloan", "credit", "kredit", "rupee loan", "personal loan"),
        "invest" to listOf("invest", "double money", "guaranteed", "trading", "crypto", "bitcoin", "mutual fund", "forex"),
        "job" to listOf("job", "parttime", "work from home", "earn from home", "registration fee", "hiring"),
        "kyc" to listOf("kyc", "ekyc", "update kyc"),
        "support" to listOf("customer care", "helpline", "toll free", "support number"),
        "apk" to listOf("apk", "install app", "download app"),
        "ad" to listOf("ad", "ads", "sponsored", "promo")
    )

    categoryMap.forEach { (key, terms) ->
        if (lower.contains(key) || terms.any { lower.contains(it) }) {
            termsToBlock.addAll(terms)
        }
    }

    lower.split(Regex("[\\s,]+")).forEach { word ->
        if (word.length > 3 && (word.contains(".") || word.contains("ad") || word.contains("bet") || word.contains("loan"))) {
            HostBlocker.normalizeDomain(word)?.let { termsToBlock.add(it) }
        }
    }

    if (termsToBlock.isEmpty()) {
        termsToBlock.addAll(lower.split(Regex("[\\s,]+")).filter { it.length > 2 })
    }

    val addedTerms = mutableListOf<String>()
    termsToBlock.forEach { term ->
        val normalized = HostBlocker.normalizeDomain(term) ?: term.lowercase()
        if (PreferenceKeys.addToStringSet(context, PreferenceKeys.KEY_CUSTOM_BLOCKS, normalized)) {
            addedTerms.add(normalized)
        }
    }

    HostBlocker.get(context).reload()
    ClearGuardVpnService.reloadIfRunning(context)
    return addedTerms
}

private fun detectConflicts(
    context: Context,
    customBlocks: List<String>,
    securityBlocks: List<String>,
    allowList: List<String>
): List<Conflict> {
    val conflicts = mutableListOf<Conflict>()
    val customSet = customBlocks.toSet()
    val securitySet = securityBlocks.toSet()
    val allowSet = allowList.toSet()

    // 1. Custom block conflicting with allowlist (allowlist wins)
    customSet.intersect(allowSet).forEach { domain ->
        conflicts.add(
            Conflict(
                domain,
                "Custom Block vs Allowlist",
                "This domain is explicitly blocked by you but also allowed. The allowlist takes precedence.",
                listOf("Remove from allowlist", "Remove from custom blocks")
            )
        )
    }

    // 2. Security block conflicting with allowlist (serious, security intent overridden)
    securitySet.intersect(allowSet).forEach { domain ->
        conflicts.add(
            Conflict(
                domain,
                "Security Block vs Allowlist",
                "High-priority security block (scam/malware) is being allowed. This may be dangerous.",
                listOf("Remove from allowlist", "Remove from security blocks")
            )
        )
    }

    // 3. Redundant custom block (already in downloaded filter lists)
    try {
        val dlFile = HostBlocker.downloadedHostsFile(context)
        if (dlFile.exists()) {
            val downloaded = mutableSetOf<String>()
            dlFile.forEachLine { line ->
                downloaded.addAll(HostBlocker.hostsFromLine(line))
            }
            customSet.intersect(downloaded).forEach { domain ->
                conflicts.add(
                    Conflict(
                        domain,
                        "Redundant Custom Block",
                        "This domain is already blocked by one or more of your active downloaded filter lists.",
                        listOf("Remove from custom blocks")
                    )
                )
            }
        }
    } catch (e: Exception) {
        // Ignore read errors
    }

    // 4. Custom block also in security blocks (redundant double-block)
    customSet.intersect(securitySet).forEach { domain ->
        conflicts.add(
            Conflict(
                domain,
                "Custom vs Security Block",
                "Domain blocked in both your custom blocks and security blocks (redundant).",
                listOf("Remove from custom blocks", "Remove from security blocks")
            )
        )
    }

    // 5. Allowlist item that doesn't seem to be blocking anything useful (optional, low priority)
    // For now, skip heavy full-block-set computation

    return conflicts
}

private fun normalizeSourceUrl(input: String): String? {
    var value = input.trim()
    if (value.isEmpty()) {
        return null
    }
    if (!value.contains("://")) {
        value = "https://$value"
    }
    if (!value.lowercase(Locale.US).startsWith("https://")) {
        // Cleartext traffic is disabled, so only HTTPS sources can be fetched.
        return null
    }
    return try {
        val uri = URI(value)
        if (uri.host.isNullOrBlank()) null else value
    } catch (e: Exception) {
        null
    }
}

private fun sourceTitle(url: String): String {
    val displayName = PreferenceKeys.sourceDisplayName(url)
    if (displayName != "Custom filter list") {
        return displayName
    }
    return try {
        URI(url).host ?: url
    } catch (e: Exception) {
        url
    }
}

private fun lastUpdateLabel(millis: Long): String {
    if (millis <= 0L) return "never"
    return DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, Locale.getDefault())
        .format(Date(millis))
}

private fun formatCount(value: Int): String {
    return when {
        value >= 1_000_000 -> String.format(Locale.US, "%.1fm", value / 1_000_000.0)
        value >= 1_000 -> String.format(Locale.US, "%.1fk", value / 1_000.0)
        else -> value.toString()
    }
}

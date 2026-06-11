package com.clearguard.app.ui.screens

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clearguard.app.PreferenceKeys
import com.clearguard.app.blocking.BlocklistUpdater
import com.clearguard.app.blocking.HostBlocker
import com.clearguard.app.ui.components.GlassCard
import com.clearguard.app.ui.components.LiquidGlassButton
import com.clearguard.app.ui.components.LiquidGlassIconButton
import com.clearguard.app.ui.theme.ClearColors
import com.clearguard.app.vpn.ClearGuardVpnService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URI
import java.text.DateFormat
import java.util.Date
import java.util.Locale

data class BlocklistRow(val name: String, val count: String, val source: String)

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

    val allowList = remember {
        mutableStateListOf<String>().apply {
            addAll(PreferenceKeys.stringSetSorted(context, PreferenceKeys.KEY_ALLOWLIST))
        }
    }
    var newAllow by remember { mutableStateOf("") }
    var allowError by remember { mutableStateOf("") }

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
        newSource = ""
        updateMessage = "Source added — tap Update to download it"
    }

    fun removeSource(url: String) {
        sources.remove(url)
        persistSources(prefs, sources)
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

    val rows = listOf(
        BlocklistRow("Built-in starter list", "12", "Bundled asset"),
        BlocklistRow("Downloaded hosts", formatCount(downloadedHosts), "Manual update")
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 12.dp)
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
                            "Loaded ${formatCount(result.hostCount)} domains from " +
                                "${result.successfulSources}/${result.totalSources} sources"
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
                HelpText("Hosts-style lists downloaded over HTTPS when you tap Update.")
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
                    EntryRow(title = sourceHost(url), subtitle = url, onRemove = { removeSource(url) })
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
    Text(
        text,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = ClearColors.text,
        modifier = Modifier.padding(start = 4.dp, top = 2.dp)
    )
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
private fun EntryRow(title: String, subtitle: String?, onRemove: () -> Unit) {
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

private fun loadSources(prefs: android.content.SharedPreferences): List<String> {
    val stored = prefs.getStringSet(PreferenceKeys.KEY_SOURCE_URLS, PreferenceKeys.defaultSources())
        ?: PreferenceKeys.defaultSources()
    return stored.sorted()
}

private fun persistSources(prefs: android.content.SharedPreferences, sources: List<String>) {
    prefs.edit()
        .putStringSet(PreferenceKeys.KEY_SOURCE_URLS, LinkedHashSet(sources))
        .apply()
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

private fun sourceHost(url: String): String {
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

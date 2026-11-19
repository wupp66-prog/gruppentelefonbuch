package de.mehrnetz.gruppentelefonbuch

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val SUGGESTIONS = listOf("Familie", "Firma", "Gruppenleiter", "Freunde", "Nachbarn", "Verein")

// ---------------------------------------------------------------- Gemeinsame Zeile

@Composable
fun ContactListItem(
    c: ContactFull,
    extra: String? = null,
    onClick: () -> Unit,
    onRemove: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val best = c.bestPhone()
    ListItem(
        headlineContent = { Text(c.contact.name) },
        supportingContent = {
            val sub = listOfNotNull(best?.number, extra).joinToString(" · ")
            if (sub.isNotEmpty()) Text(sub)
        },
        trailingContent = {
            Row {
                if (best != null) {
                    IconButton(onClick = { dial(context, best.number) }) {
                        Icon(Icons.Default.Call, contentDescription = "Anrufen")
                    }
                }
                if (onRemove != null) {
                    IconButton(onClick = onRemove) {
                        Icon(Icons.Default.Close, contentDescription = "Aus Gruppe entfernen")
                    }
                }
            }
        },
        modifier = Modifier.clickable { onClick() }
    )
    HorizontalDivider()
}

// ---------------------------------------------------------------- Gruppenübersicht

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupsScreen(
    dao: PhoneDao,
    onOpenGroup: (Long, String) -> Unit,
    onOpenContact: (Long) -> Unit,
    onNewContact: () -> Unit
) {
    val context = LocalContext.current
    val db = remember { AppDb.get(context) }
    val scope = rememberCoroutineScope()
    val groups by dao.groups().collectAsState(initial = emptyList())
    val total by dao.contactCount().collectAsState(initial = 0)

    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val q = query.trim()
    val results by remember(q) {
        if (q.isEmpty()) flowOf(emptyList<ContactFull>()) else dao.search(q)
    }.collectAsState(initial = emptyList())

    var showNew by remember { mutableStateOf(false) }
    var editGroup by remember { mutableStateOf<GroupRow?>(null) }
    var deleteGroup by remember { mutableStateOf<GroupRow?>(null) }
    var menu by remember { mutableStateOf(false) }
    var fabMenu by remember { mutableStateOf(false) }
    var pendingRestore by remember { mutableStateOf<Uri?>(null) }

    // Datensicherung
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) scope.launch {
            try {
                val text = Backup.export(db)
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use {
                        it.write(text.toByteArray(Charsets.UTF_8))
                    } ?: throw IllegalStateException("Datei nicht beschreibbar")
                }
                toast(context, "Datensicherung gespeichert")
            } catch (e: Exception) {
                toast(context, "Sicherung fehlgeschlagen: ${e.message}")
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) pendingRestore = uri }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (searching) {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = { Text("Name, Nummer, E-Mail …") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Text("Telefonbuch")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        searching = !searching
                        if (!searching) query = ""
                    }) {
                        Icon(
                            if (searching) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = "Suchen"
                        )
                    }
                    Box {
                        IconButton(onClick = { menu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Menü")
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text("Datensicherung erstellen") },
                                onClick = {
                                    menu = false
                                    exportLauncher.launch("telefonbuch-sicherung.json")
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Datensicherung wiederherstellen") },
                                onClick = {
                                    menu = false
                                    importLauncher.launch(arrayOf("*/*"))
                                }
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            Box {
                FloatingActionButton(onClick = { fabMenu = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Neu")
                }
                DropdownMenu(expanded = fabMenu, onDismissRequest = { fabMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Neuer Kontakt") },
                        onClick = {
                            fabMenu = false
                            onNewContact()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Neue Gruppe") },
                        onClick = {
                            fabMenu = false
                            showNew = true
                        }
                    )
                }
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (searching && q.isNotEmpty()) {
                if (results.isEmpty()) {
                    Text("Nichts gefunden", Modifier.padding(16.dp))
                }
                LazyColumn {
                    items(results, key = { it.contact.id }) { r ->
                        ContactListItem(
                            c = r,
                            extra = r.groups.joinToString(", ") { it.name }.ifEmpty { null },
                            onClick = { onOpenContact(r.contact.id) }
                        )
                    }
                }
            } else {
                LazyColumn {
                    item {
                        ListItem(
                            headlineContent = { Text("Alle Kontakte") },
                            supportingContent = {
                                Text(if (total == 1) "1 Kontakt" else "$total Kontakte")
                            },
                            modifier = Modifier.clickable { onOpenGroup(ALL, "Alle Kontakte") }
                        )
                        HorizontalDivider()
                    }
                    if (groups.isEmpty()) {
                        item {
                            Text(
                                "Noch keine Gruppen. Mit dem Plus-Knopf legst du eine an (z. B. Familie, Firma, Gruppenleiter).",
                                Modifier.padding(16.dp)
                            )
                        }
                    }
                    items(groups, key = { it.id }) { g ->
                        ListItem(
                            headlineContent = { Text(g.name) },
                            supportingContent = {
                                Text(if (g.count == 1) "1 Kontakt" else "${g.count} Kontakte")
                            },
                            trailingContent = {
                                Row {
                                    IconButton(onClick = { editGroup = g }) {
                                        Icon(Icons.Default.Edit, contentDescription = "Umbenennen")
                                    }
                                    IconButton(onClick = { deleteGroup = g }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Löschen")
                                    }
                                }
                            },
                            modifier = Modifier.clickable { onOpenGroup(g.id, g.name) }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    if (showNew) {
        GroupNameDialog(
            title = "Neue Gruppe",
            initial = "",
            showSuggestions = true,
            onDismiss = { showNew = false },
            onSave = { name ->
                scope.launch { dao.insert(ContactGroup(name = name)) }
                showNew = false
            }
        )
    }
    editGroup?.let { g ->
        GroupNameDialog(
            title = "Gruppe umbenennen",
            initial = g.name,
            showSuggestions = false,
            onDismiss = { editGroup = null },
            onSave = { name ->
                scope.launch { dao.update(ContactGroup(id = g.id, name = name)) }
                editGroup = null
            }
        )
    }
    deleteGroup?.let { g ->
        AlertDialog(
            onDismissRequest = { deleteGroup = null },
            title = { Text("Gruppe löschen?") },
            text = {
                Text("Die Gruppe „${g.name}“ wird gelöscht. Die Kontakte bleiben erhalten (unter „Alle Kontakte“ und in ihren anderen Gruppen).")
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { dao.delete(ContactGroup(id = g.id, name = g.name)) }
                    deleteGroup = null
                }) { Text("Löschen") }
            },
            dismissButton = { TextButton(onClick = { deleteGroup = null }) { Text("Abbrechen") } }
        )
    }
    pendingRestore?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text("Datensicherung wiederherstellen?") },
            text = { Text("Alle aktuellen Gruppen und Kontakte werden durch den Inhalt der Sicherung ersetzt.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingRestore = null
                    scope.launch {
                        try {
                            val text = withContext(Dispatchers.IO) {
                                context.contentResolver.openInputStream(uri)
                                    ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                                    ?: throw IllegalStateException("Datei nicht lesbar")
                            }
                            Backup.restore(db, text)
                            toast(context, "Datensicherung wiederhergestellt")
                        } catch (e: Exception) {
                            toast(context, "Wiederherstellen fehlgeschlagen – nichts wurde geändert (${e.message})")
                        }
                    }
                }) { Text("Ersetzen") }
            },
            dismissButton = { TextButton(onClick = { pendingRestore = null }) { Text("Abbrechen") } }
        )
    }
}

@Composable
private fun GroupNameDialog(
    title: String,
    initial: String,
    showSuggestions: Boolean,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Gruppenname") },
                    singleLine = true
                )
                if (showSuggestions) {
                    Row(
                        Modifier.padding(top = 12.dp).horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SUGGESTIONS.forEach { s ->
                            AssistChip(onClick = { name = s }, label = { Text(s) })
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onSave(name.trim()) }) {
                Text("Speichern")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } }
    )
}

// ---------------------------------------------------------------- Kontakte einer Gruppe

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsScreen(
    dao: PhoneDao,
    groupId: Long,
    groupName: String,
    onBack: () -> Unit,
    onOpenContact: (Long) -> Unit,
    onNew: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isGroup = groupId > 0
    val contacts by remember(groupId) {
        if (isGroup) dao.inGroup(groupId) else dao.all()
    }.collectAsState(initial = emptyList())

    var menu by remember { mutableStateOf(false) }
    var showRound by remember { mutableStateOf(false) }
    var showMailChoice by remember { mutableStateOf(false) }
    var removeContact by remember { mutableStateOf<ContactFull?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("$groupName (${contacts.size})") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
                actions = {
                    if (isGroup && contacts.isNotEmpty()) {
                        Box {
                            IconButton(onClick = { menu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "Gruppenaktionen")
                            }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(
                                    text = { Text("Rundruf (alle der Reihe nach anrufen)") },
                                    onClick = {
                                        menu = false
                                        showRound = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("SMS an alle") },
                                    onClick = {
                                        menu = false
                                        groupSms(context, contacts)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("E-Mail an alle") },
                                    onClick = {
                                        menu = false
                                        showMailChoice = true
                                    }
                                )
                            }
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNew) {
                Icon(Icons.Default.Add, contentDescription = "Neuer Kontakt")
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (contacts.isEmpty()) {
                Text(
                    "Hier ist noch niemand. Mit dem Plus-Knopf legst du einen Kontakt an.",
                    Modifier.padding(16.dp)
                )
            }
            LazyColumn {
                items(contacts, key = { it.contact.id }) { c ->
                    ContactListItem(
                        c = c,
                        onClick = { onOpenContact(c.contact.id) },
                        onRemove = if (isGroup) ({ removeContact = c }) else null
                    )
                }
            }
        }
    }

    if (showRound) {
        CallRoundDialog(members = contacts, onDismiss = { showRound = false })
    }
    if (showMailChoice) {
        AlertDialog(
            onDismissRequest = { showMailChoice = false },
            title = { Text("E-Mail an ${contacts.size} Kontakte") },
            text = {
                Text("Sollen die Empfänger sichtbar im Feld „An“ stehen oder als Blindkopie (BCC), sodass niemand die anderen Adressen sieht?")
            },
            confirmButton = {
                TextButton(onClick = {
                    showMailChoice = false
                    groupMail(context, contacts, bcc = false)
                }) { Text("Im Feld „An“") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showMailChoice = false
                    groupMail(context, contacts, bcc = true)
                }) { Text("Blindkopie (BCC)") }
            }
        )
    }
    removeContact?.let { c ->
        AlertDialog(
            onDismissRequest = { removeContact = null },
            title = { Text("Aus Gruppe entfernen?") },
            text = { Text("„${c.contact.name}“ wird nur aus „$groupName“ entfernt und bleibt als Kontakt erhalten.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { dao.removeFromGroup(groupId, c.contact.id) }
                    removeContact = null
                }) { Text("Entfernen") }
            },
            dismissButton = { TextButton(onClick = { removeContact = null }) { Text("Abbrechen") } }
        )
    }
}

/**
 * Android erlaubt Apps keine echte Konferenzschaltung. Der Rundruf führt deshalb
 * durch die Gruppe: Name und Nummer anzeigen, Anrufen antippen, danach "Weiter".
 */
@Composable
private fun CallRoundDialog(members: List<ContactFull>, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val list = members.filter { it.bestPhone() != null }
    var idx by remember { mutableIntStateOf(0) }

    if (list.isEmpty()) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Rundruf") },
            text = { Text("Kein Kontakt in dieser Gruppe hat eine Telefonnummer.") },
            confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } }
        )
        return
    }
    val i = idx.coerceIn(0, list.size - 1)
    val c = list[i]
    val phone = c.bestPhone()!!

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rundruf ${i + 1} von ${list.size}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(c.contact.name, style = MaterialTheme.typography.titleLarge)
                Text("${phone.number} (${phone.label})")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(enabled = i > 0, onClick = { idx = i - 1 }) { Text("Zurück") }
                    OutlinedButton(enabled = i < list.size - 1, onClick = { idx = i + 1 }) { Text("Weiter") }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { dial(context, phone.number) }) { Text("Anrufen") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Schließen") } }
    )
}

package de.mehrnetz.gruppentelefonbuch

import android.app.Activity
import android.content.Intent
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private val PHONE_LABELS = listOf("Mobil", "Privat", "Arbeit", "Sonstige")
private val EMAIL_LABELS = listOf("Privat", "Arbeit", "Sonstige")

private fun nextLabel(labels: List<String>, current: String): String =
    labels[(labels.indexOf(current) + 1) % labels.size]

// ---------------------------------------------------------------- Kontakt ansehen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactDetailScreen(dao: PhoneDao, id: Long, onBack: () -> Unit, onEdit: () -> Unit) {
    val context = LocalContext.current
    val c by remember(id) { dao.contact(id) }.collectAsState(initial = null)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(c?.contact?.name ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
                actions = {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "Bearbeiten")
                    }
                }
            )
        }
    ) { padding ->
        val contact = c
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
            if (contact != null) {
                contact.sortedPhones().forEach { p ->
                    ListItem(
                        headlineContent = { Text(p.number) },
                        supportingContent = { Text(p.label) },
                        trailingContent = {
                            Row {
                                IconButton(onClick = { sms(context, listOf(p.number)) }) {
                                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "SMS senden")
                                }
                                IconButton(onClick = { dial(context, p.number) }) {
                                    Icon(Icons.Default.Call, contentDescription = "Anrufen")
                                }
                            }
                        },
                        modifier = Modifier.clickable { dial(context, p.number) }
                    )
                    HorizontalDivider()
                }
                contact.sortedEmails().forEach { e ->
                    ListItem(
                        headlineContent = { Text(e.address) },
                        supportingContent = { Text("E-Mail · ${e.label}") },
                        trailingContent = {
                            Icon(Icons.Default.Email, contentDescription = "E-Mail schreiben")
                        },
                        modifier = Modifier.clickable { mail(context, listOf(e.address)) }
                    )
                    HorizontalDivider()
                }
                if (contact.contact.address.isNotBlank()) {
                    ListItem(
                        headlineContent = { Text(contact.contact.address) },
                        supportingContent = { Text("Wohnadresse") },
                        trailingContent = {
                            Icon(Icons.Default.Place, contentDescription = "Auf Karte zeigen")
                        },
                        modifier = Modifier.clickable { openMap(context, contact.contact.address) }
                    )
                    HorizontalDivider()
                }
                if (contact.groups.isNotEmpty()) {
                    ListItem(
                        headlineContent = { Text(contact.groups.sortedBy { it.name.lowercase() }.joinToString(", ") { it.name }) },
                        supportingContent = { Text("Gruppen") }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Kontakt anlegen / bearbeiten

private data class Draft(val value: String = "", val label: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactEditScreen(
    dao: PhoneDao,
    id: Long,
    preGroup: Long,
    onClose: () -> Unit,
    onDeleted: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val groups by dao.groups().collectAsState(initial = emptyList())

    var name by remember(id) { mutableStateOf("") }
    var address by remember(id) { mutableStateOf("") }
    val phones = remember(id) { mutableStateListOf(Draft(label = "Mobil")) }
    val emails = remember(id) { mutableStateListOf(Draft(label = "Privat")) }
    var selected by remember(id) {
        mutableStateOf(if (preGroup > 0) setOf(preGroup) else emptySet<Long>())
    }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(id) {
        if (id != 0L) {
            dao.contactOnce(id)?.let { c ->
                name = c.contact.name
                address = c.contact.address
                phones.clear()
                phones.addAll(c.sortedPhones().map { Draft(it.number, it.label) })
                if (phones.isEmpty()) phones.add(Draft(label = "Mobil"))
                emails.clear()
                emails.addAll(c.sortedEmails().map { Draft(it.address, it.label) })
                if (emails.isEmpty()) emails.add(Draft(label = "Privat"))
                selected = c.groups.map { it.id }.toSet()
            }
        }
    }

    // Kontakt aus den Handy-Kontakten übernehmen (braucht keine Berechtigung)
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val uri = r.data?.data
        if (r.resultCode == Activity.RESULT_OK && uri != null) {
            val cols = arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            )
            context.contentResolver.query(uri, cols, null, null, null)?.use { cur ->
                if (cur.moveToFirst()) {
                    name = cur.getString(0) ?: name
                    val number = cur.getString(1) ?: ""
                    if (number.isNotBlank()) {
                        val firstEmpty = phones.indexOfFirst { it.value.isBlank() }
                        if (firstEmpty >= 0) phones[firstEmpty] = phones[firstEmpty].copy(value = number)
                        else phones.add(Draft(number, "Mobil"))
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (id == 0L) "Neuer Kontakt" else "Kontakt bearbeiten") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "Abbrechen")
                    }
                },
                actions = {
                    IconButton(
                        enabled = name.isNotBlank(),
                        onClick = {
                            scope.launch {
                                dao.save(
                                    Contact(id = id, name = name.trim(), address = address.trim()),
                                    phones.filter { it.value.isNotBlank() }
                                        .map { PhoneNumber(contactId = 0, number = it.value.trim(), label = it.label) },
                                    emails.filter { it.value.isNotBlank() }
                                        .map { EmailAddress(contactId = 0, address = it.value.trim(), label = it.label) },
                                    selected
                                )
                                onClose()
                            }
                        }
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "Speichern")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            if (id == 0L) {
                OutlinedButton(
                    onClick = {
                        try {
                            picker.launch(
                                Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
                            )
                        } catch (e: Exception) {
                            toast(context, "Kontaktauswahl nicht verfügbar")
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Aus Handy-Kontakten übernehmen") }
            }

            Text("Telefonnummern", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
            phones.forEachIndexed { i, d ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = d.value,
                        onValueChange = { phones[i] = d.copy(value = it) },
                        label = { Text("Nummer") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { phones[i] = d.copy(label = nextLabel(PHONE_LABELS, d.label)) }) {
                        Text(d.label)
                    }
                    IconButton(onClick = { phones.removeAt(i) }) {
                        Icon(Icons.Default.Close, contentDescription = "Nummer entfernen")
                    }
                }
            }
            TextButton(onClick = { phones.add(Draft(label = "Mobil")) }) { Text("+ Weitere Nummer") }

            Text("E-Mail-Adressen", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
            emails.forEachIndexed { i, d ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = d.value,
                        onValueChange = { emails[i] = d.copy(value = it) },
                        label = { Text("E-Mail") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { emails[i] = d.copy(label = nextLabel(EMAIL_LABELS, d.label)) }) {
                        Text(d.label)
                    }
                    IconButton(onClick = { emails.removeAt(i) }) {
                        Icon(Icons.Default.Close, contentDescription = "Adresse entfernen")
                    }
                }
            }
            TextButton(onClick = { emails.add(Draft(label = "Privat")) }) { Text("+ Weitere E-Mail-Adresse") }

            Text("Wohnadresse", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
            OutlinedTextField(
                value = address,
                onValueChange = { address = it },
                label = { Text("Straße, PLZ, Ort") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth()
            )

            Text("Gruppen", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
            if (groups.isEmpty()) {
                Text("Es gibt noch keine Gruppen. Lege auf der Startseite zuerst eine an.")
            }
            groups.forEach { g ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            selected = if (g.id in selected) selected - g.id else selected + g.id
                        }
                ) {
                    Checkbox(
                        checked = g.id in selected,
                        onCheckedChange = {
                            selected = if (it) selected + g.id else selected - g.id
                        }
                    )
                    Text(g.name)
                }
            }

            if (id != 0L) {
                OutlinedButton(
                    onClick = { confirmDelete = true },
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
                ) { Text("Kontakt ganz löschen") }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Kontakt löschen?") },
            text = { Text("„$name“ wird aus allen Gruppen gelöscht.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        dao.deleteContact(Contact(id = id, name = name))
                        onDeleted()
                    }
                }) { Text("Löschen") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Abbrechen") } }
        )
    }
}

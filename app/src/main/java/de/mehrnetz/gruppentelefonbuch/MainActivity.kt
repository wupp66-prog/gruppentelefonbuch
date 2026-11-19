package de.mehrnetz.gruppentelefonbuch

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

const val NONE = -1L
const val ALL = -2L

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
            ) {
                App()
            }
        }
    }
}

@Composable
fun App() {
    val context = LocalContext.current
    val dao = remember { AppDb.get(context).dao() }

    // Navigation: NONE = nicht offen, ALL = "Alle Kontakte"
    var groupId by rememberSaveable { mutableLongStateOf(NONE) }
    var groupName by rememberSaveable { mutableStateOf("") }
    var detailId by rememberSaveable { mutableLongStateOf(NONE) }
    var editId by rememberSaveable { mutableLongStateOf(NONE) } // 0 = neuer Kontakt
    var preGroup by rememberSaveable { mutableLongStateOf(NONE) }

    when {
        editId != NONE -> {
            BackHandler { editId = NONE }
            ContactEditScreen(
                dao = dao,
                id = editId,
                preGroup = preGroup,
                onClose = { editId = NONE },
                onDeleted = {
                    editId = NONE
                    detailId = NONE
                }
            )
        }
        detailId != NONE -> {
            BackHandler { detailId = NONE }
            ContactDetailScreen(
                dao = dao,
                id = detailId,
                onBack = { detailId = NONE },
                onEdit = {
                    preGroup = NONE
                    editId = detailId
                }
            )
        }
        groupId != NONE -> {
            BackHandler { groupId = NONE }
            ContactsScreen(
                dao = dao,
                groupId = groupId,
                groupName = groupName,
                onBack = { groupId = NONE },
                onOpenContact = { detailId = it },
                onNew = {
                    preGroup = if (groupId > 0) groupId else NONE
                    editId = 0L
                }
            )
        }
        else -> {
            GroupsScreen(
                dao = dao,
                onOpenGroup = { id, name ->
                    groupId = id
                    groupName = name
                },
                onOpenContact = { detailId = it },
                onNewContact = {
                    preGroup = NONE
                    editId = 0L
                }
            )
        }
    }
}

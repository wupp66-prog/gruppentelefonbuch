package de.mehrnetz.gruppentelefonbuch

import androidx.room.withTransaction
import org.json.JSONArray
import org.json.JSONObject

/** Datensicherung als JSON-Datei (Gruppen, Kontakte, Nummern, E-Mail-Adressen, Adressen, Zuordnungen). */
object Backup {

    suspend fun export(db: AppDb): String {
        val dao = db.dao()
        val groups = dao.allGroups()
        val contacts = dao.allContactsFull()

        val root = JSONObject()
        root.put("version", 1)

        val groupsJson = JSONArray()
        groups.forEach { g ->
            groupsJson.put(JSONObject().put("id", g.id).put("name", g.name))
        }
        root.put("groups", groupsJson)

        val contactsJson = JSONArray()
        contacts.forEach { c ->
            val o = JSONObject()
            o.put("name", c.contact.name)
            o.put("address", c.contact.address)
            val phones = JSONArray()
            c.sortedPhones().forEach { phones.put(JSONObject().put("label", it.label).put("number", it.number)) }
            o.put("phones", phones)
            val emails = JSONArray()
            c.sortedEmails().forEach { emails.put(JSONObject().put("label", it.label).put("address", it.address)) }
            o.put("emails", emails)
            val gids = JSONArray()
            c.groups.forEach { gids.put(it.id) }
            o.put("groups", gids)
            contactsJson.put(o)
        }
        root.put("contacts", contactsJson)
        return root.toString(2)
    }

    /** Ersetzt alle vorhandenen Daten. Bei einem Fehler bleibt alles unverändert. */
    suspend fun restore(db: AppDb, json: String) {
        val root = JSONObject(json)
        val groupsJson = root.getJSONArray("groups")
        val contactsJson = root.getJSONArray("contacts")
        val dao = db.dao()

        db.withTransaction {
            dao.wipeContacts()
            dao.wipeGroups()

            val idMap = HashMap<Long, Long>()
            for (i in 0 until groupsJson.length()) {
                val g = groupsJson.getJSONObject(i)
                idMap[g.getLong("id")] = dao.insert(ContactGroup(name = g.getString("name")))
            }

            for (i in 0 until contactsJson.length()) {
                val o = contactsJson.getJSONObject(i)
                val phones = ArrayList<PhoneNumber>()
                val pj = o.optJSONArray("phones") ?: JSONArray()
                for (k in 0 until pj.length()) {
                    val p = pj.getJSONObject(k)
                    phones.add(PhoneNumber(contactId = 0, number = p.getString("number"), label = p.optString("label", "Mobil")))
                }
                val emails = ArrayList<EmailAddress>()
                val ej = o.optJSONArray("emails") ?: JSONArray()
                for (k in 0 until ej.length()) {
                    val e = ej.getJSONObject(k)
                    emails.add(EmailAddress(contactId = 0, address = e.getString("address"), label = e.optString("label", "Privat")))
                }
                val gids = ArrayList<Long>()
                val gj = o.optJSONArray("groups") ?: JSONArray()
                for (k in 0 until gj.length()) {
                    idMap[gj.getLong(k)]?.let { gids.add(it) }
                }
                dao.save(
                    Contact(name = o.getString("name"), address = o.optString("address", "")),
                    phones, emails, gids
                )
            }
        }
    }
}

package de.mehrnetz.gruppentelefonbuch

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

fun toast(context: Context, text: String) {
    Toast.makeText(context, text, Toast.LENGTH_LONG).show()
}

private fun start(context: Context, intent: Intent, error: String) {
    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        toast(context, error)
    }
}

/** Entfernt Leerzeichen, Bindestriche, Schrägstriche usw. */
fun cleanNumber(s: String): String = s.filter { it.isDigit() || it == '+' }

fun ContactFull.sortedPhones(): List<PhoneNumber> = phones.sortedBy { it.id }
fun ContactFull.sortedEmails(): List<EmailAddress> = emails.sortedBy { it.id }

/** Nummer für Gruppenaktionen: erste Mobilnummer, sonst die erste Nummer. */
fun ContactFull.bestPhone(): PhoneNumber? {
    val list = sortedPhones()
    return list.firstOrNull { it.label == "Mobil" } ?: list.firstOrNull()
}

fun dial(context: Context, phone: String) {
    start(
        context,
        Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", cleanNumber(phone), null)),
        "Keine Telefon-App gefunden"
    )
}

/** SMS an eine oder mehrere Nummern (Gruppen-SMS). */
fun sms(context: Context, numbers: List<String>) {
    val list = numbers.map { cleanNumber(it) }.filter { it.isNotEmpty() }
    if (list.isEmpty()) return
    start(
        context,
        Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + list.joinToString(";"))),
        "Keine SMS-App gefunden"
    )
}

/** E-Mail-Programm öffnen, Empfänger sind bereits eingetragen. */
fun mail(context: Context, to: List<String>, bcc: Boolean = false) {
    if (to.isEmpty()) return
    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))
    intent.putExtra(if (bcc) Intent.EXTRA_BCC else Intent.EXTRA_EMAIL, to.toTypedArray())
    start(context, intent, "Kein E-Mail-Programm gefunden")
}

fun openMap(context: Context, address: String) {
    start(
        context,
        Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(address))),
        "Keine Karten-App gefunden"
    )
}

/** SMS an alle Mitglieder der Gruppe. */
fun groupSms(context: Context, members: List<ContactFull>) {
    val numbers = members.mapNotNull { it.bestPhone()?.number }
    if (numbers.isEmpty()) {
        toast(context, "Kein Kontakt in dieser Gruppe hat eine Telefonnummer")
        return
    }
    val missing = members.size - numbers.size
    if (missing > 0) toast(context, "$missing Kontakt(e) ohne Nummer übersprungen")
    sms(context, numbers)
}

/** E-Mail an alle Mitglieder der Gruppe (jeweils die erste Adresse). */
fun groupMail(context: Context, members: List<ContactFull>, bcc: Boolean) {
    val addresses = members.mapNotNull { it.sortedEmails().firstOrNull()?.address }
    if (addresses.isEmpty()) {
        toast(context, "Kein Kontakt in dieser Gruppe hat eine E-Mail-Adresse")
        return
    }
    val missing = members.size - addresses.size
    if (missing > 0) toast(context, "$missing Kontakt(e) ohne E-Mail-Adresse übersprungen")
    mail(context, addresses, bcc)
}

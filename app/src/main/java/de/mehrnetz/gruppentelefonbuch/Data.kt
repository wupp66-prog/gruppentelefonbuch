package de.mehrnetz.gruppentelefonbuch

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.Junction
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity
data class ContactGroup(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String
)

@Entity
data class Contact(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val address: String = ""
)

@Entity(
    foreignKeys = [ForeignKey(
        entity = Contact::class,
        parentColumns = ["id"],
        childColumns = ["contactId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("contactId")]
)
data class PhoneNumber(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val contactId: Long,
    val number: String,
    val label: String
)

@Entity(
    foreignKeys = [ForeignKey(
        entity = Contact::class,
        parentColumns = ["id"],
        childColumns = ["contactId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("contactId")]
)
data class EmailAddress(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val contactId: Long,
    val address: String,
    val label: String
)

/** Verknüpfung: ein Kontakt kann in beliebig vielen Gruppen sein. */
@Entity(
    primaryKeys = ["groupId", "contactId"],
    foreignKeys = [
        ForeignKey(
            entity = ContactGroup::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Contact::class,
            parentColumns = ["id"],
            childColumns = ["contactId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("contactId")]
)
data class GroupMember(val groupId: Long, val contactId: Long)

/** Gruppe mit Anzahl der Kontakte (für die Startseite). */
data class GroupRow(val id: Long, val name: String, val count: Int)

/** Kontakt mit allen Nummern, E-Mail-Adressen und Gruppen. */
data class ContactFull(
    @Embedded val contact: Contact,
    @Relation(parentColumn = "id", entityColumn = "contactId")
    val phones: List<PhoneNumber>,
    @Relation(parentColumn = "id", entityColumn = "contactId")
    val emails: List<EmailAddress>,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(value = GroupMember::class, parentColumn = "contactId", entityColumn = "groupId")
    )
    val groups: List<ContactGroup>
)

@Dao
abstract class PhoneDao {
    @Query(
        "SELECT g.id AS id, g.name AS name, COUNT(m.contactId) AS count " +
            "FROM ContactGroup g LEFT JOIN GroupMember m ON m.groupId = g.id " +
            "GROUP BY g.id ORDER BY g.name COLLATE NOCASE"
    )
    abstract fun groups(): Flow<List<GroupRow>>

    @Query("SELECT COUNT(*) FROM Contact")
    abstract fun contactCount(): Flow<Int>

    @Transaction
    @Query(
        "SELECT c.* FROM Contact c JOIN GroupMember m ON m.contactId = c.id " +
            "WHERE m.groupId = :groupId ORDER BY c.name COLLATE NOCASE"
    )
    abstract fun inGroup(groupId: Long): Flow<List<ContactFull>>

    @Transaction
    @Query("SELECT * FROM Contact ORDER BY name COLLATE NOCASE")
    abstract fun all(): Flow<List<ContactFull>>

    @Transaction
    @Query("SELECT * FROM Contact WHERE id = :id")
    abstract fun contact(id: Long): Flow<ContactFull?>

    @Transaction
    @Query("SELECT * FROM Contact WHERE id = :id")
    abstract suspend fun contactOnce(id: Long): ContactFull?

    @Transaction
    @Query(
        "SELECT DISTINCT c.* FROM Contact c " +
            "LEFT JOIN PhoneNumber p ON p.contactId = c.id " +
            "LEFT JOIN EmailAddress e ON e.contactId = c.id " +
            "WHERE c.name LIKE '%' || :q || '%' OR c.address LIKE '%' || :q || '%' " +
            "OR p.number LIKE '%' || :q || '%' OR e.address LIKE '%' || :q || '%' " +
            "ORDER BY c.name COLLATE NOCASE"
    )
    abstract fun search(q: String): Flow<List<ContactFull>>

    // --- für die Datensicherung
    @Query("SELECT * FROM ContactGroup")
    abstract suspend fun allGroups(): List<ContactGroup>

    @Transaction
    @Query("SELECT * FROM Contact")
    abstract suspend fun allContactsFull(): List<ContactFull>

    @Query("DELETE FROM Contact")
    abstract suspend fun wipeContacts()

    @Query("DELETE FROM ContactGroup")
    abstract suspend fun wipeGroups()

    // --- Gruppen
    @Insert abstract suspend fun insert(group: ContactGroup): Long
    @Update abstract suspend fun update(group: ContactGroup)
    @Delete abstract suspend fun delete(group: ContactGroup)

    // --- Kontakte
    @Insert abstract suspend fun insertContact(contact: Contact): Long
    @Update abstract suspend fun updateContact(contact: Contact)
    @Delete abstract suspend fun deleteContact(contact: Contact)
    @Insert abstract suspend fun insertPhones(items: List<PhoneNumber>)
    @Insert abstract suspend fun insertEmails(items: List<EmailAddress>)
    @Insert abstract suspend fun insertMembers(items: List<GroupMember>)

    @Query("DELETE FROM PhoneNumber WHERE contactId = :id")
    abstract suspend fun clearPhones(id: Long)

    @Query("DELETE FROM EmailAddress WHERE contactId = :id")
    abstract suspend fun clearEmails(id: Long)

    @Query("DELETE FROM GroupMember WHERE contactId = :id")
    abstract suspend fun clearMembers(id: Long)

    @Query("DELETE FROM GroupMember WHERE groupId = :groupId AND contactId = :contactId")
    abstract suspend fun removeFromGroup(groupId: Long, contactId: Long)

    /** Legt einen Kontakt an (id = 0) oder aktualisiert ihn, samt Nummern, Adressen und Gruppen. */
    @Transaction
    open suspend fun save(
        contact: Contact,
        phones: List<PhoneNumber>,
        emails: List<EmailAddress>,
        groupIds: Collection<Long>
    ): Long {
        val id = if (contact.id == 0L) {
            insertContact(contact)
        } else {
            updateContact(contact)
            contact.id
        }
        clearPhones(id)
        clearEmails(id)
        clearMembers(id)
        insertPhones(phones.map { it.copy(id = 0, contactId = id) })
        insertEmails(emails.map { it.copy(id = 0, contactId = id) })
        insertMembers(groupIds.distinct().map { GroupMember(it, id) })
        return id
    }
}

@Database(
    entities = [ContactGroup::class, Contact::class, PhoneNumber::class, EmailAddress::class, GroupMember::class],
    version = 2,
    exportSchema = false
)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): PhoneDao

    companion object {
        @Volatile private var instance: AppDb? = null

        /** Version 1 (ein Kontakt = eine Gruppe, eine Nummer) -> Version 2. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `Contact` RENAME TO `Contact_old`")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `Contact` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, `address` TEXT NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `PhoneNumber` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`contactId` INTEGER NOT NULL, `number` TEXT NOT NULL, `label` TEXT NOT NULL, " +
                        "FOREIGN KEY(`contactId`) REFERENCES `Contact`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_PhoneNumber_contactId` ON `PhoneNumber` (`contactId`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `EmailAddress` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`contactId` INTEGER NOT NULL, `address` TEXT NOT NULL, `label` TEXT NOT NULL, " +
                        "FOREIGN KEY(`contactId`) REFERENCES `Contact`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_EmailAddress_contactId` ON `EmailAddress` (`contactId`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `GroupMember` (`groupId` INTEGER NOT NULL, `contactId` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`groupId`, `contactId`), " +
                        "FOREIGN KEY(`groupId`) REFERENCES `ContactGroup`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
                        "FOREIGN KEY(`contactId`) REFERENCES `Contact`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_GroupMember_contactId` ON `GroupMember` (`contactId`)")

                db.execSQL("INSERT INTO `Contact` (`id`, `name`, `address`) SELECT `id`, `name`, '' FROM `Contact_old`")
                db.execSQL(
                    "INSERT INTO `PhoneNumber` (`contactId`, `number`, `label`) " +
                        "SELECT `id`, `phone`, 'Mobil' FROM `Contact_old`"
                )
                db.execSQL(
                    "INSERT INTO `GroupMember` (`groupId`, `contactId`) SELECT `groupId`, `id` FROM `Contact_old`"
                )
                db.execSQL("DROP TABLE `Contact_old`")
            }
        }

        fun get(context: Context): AppDb = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext, AppDb::class.java, "telefonbuch.db"
            ).addMigrations(MIGRATION_1_2).build().also { instance = it }
        }
    }
}

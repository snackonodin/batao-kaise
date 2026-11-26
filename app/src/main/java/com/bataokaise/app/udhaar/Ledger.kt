package com.bataokaise.app.udhaar

import android.content.Context
import android.util.Base64
import androidx.room.*
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.security.MessageDigest
import java.security.SecureRandom

@Entity(tableName = "customers", indices = [Index(value = ["name"], unique = true)])
data class Customer(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String)

/** amount > 0: udhaar given. amount < 0: repayment received. */
@Entity(tableName = "entries", indices = [Index("customerId")])
data class Entry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val customerId: Long,
    val amount: Long,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val deleted: Boolean = false,
)

/** Saved Rescue Circle case, so a person can later turn the fix into a reviewed journey. */
@Entity(tableName = "rescue_cases")
data class RescueCase(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val reason: String,
    val goal: String,
    val app: String,
    val screen: String,
    val failedStep: String,
    val attempts: Int,
    /** Already redacted. */
    val summary: String,
    val sharedTo: String = "",
)

data class Balance(val id: Long, val name: String, val balance: Long, val lastAt: Long)

@Dao
interface LedgerDao {
    @Query("SELECT * FROM customers ORDER BY name") suspend fun customers(): List<Customer>
    @Query("SELECT * FROM customers WHERE id = :id") suspend fun customer(id: Long): Customer?
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertCustomer(c: Customer): Long
    @Query("SELECT * FROM customers WHERE name = :name COLLATE NOCASE LIMIT 1") suspend fun byName(name: String): Customer?
    @Insert suspend fun insertEntry(e: Entry): Long
    @Update suspend fun updateEntry(e: Entry)
    @Query("SELECT * FROM entries WHERE customerId = :cid AND deleted = 0 ORDER BY createdAt DESC") fun entries(cid: Long): Flow<List<Entry>>
    @Query("SELECT * FROM entries WHERE id = :id") suspend fun entry(id: Long): Entry?
    @Query("""SELECT c.id AS id, c.name AS name, COALESCE(SUM(CASE WHEN e.deleted = 0 THEN e.amount ELSE 0 END), 0) AS balance,
              COALESCE(MAX(e.createdAt), 0) AS lastAt
              FROM customers c LEFT JOIN entries e ON e.customerId = c.id GROUP BY c.id ORDER BY balance DESC, name""")
    fun balances(): Flow<List<Balance>>
    @Query("""SELECT c.id AS id, c.name AS name, COALESCE(SUM(CASE WHEN e.deleted = 0 THEN e.amount ELSE 0 END), 0) AS balance,
              COALESCE(MAX(e.createdAt), 0) AS lastAt
              FROM customers c LEFT JOIN entries e ON e.customerId = c.id GROUP BY c.id ORDER BY balance DESC, name""")
    suspend fun balancesNow(): List<Balance>
    @Insert suspend fun insertCase(c: RescueCase): Long
    @Query("SELECT COUNT(*) FROM customers") suspend fun customerCount(): Int
}

@Database(entities = [Customer::class, Entry::class, RescueCase::class], version = 1, exportSchema = false)
abstract class LedgerDb : RoomDatabase() {
    abstract fun dao(): LedgerDao

    companion object {
        @Volatile private var inst: LedgerDb? = null

        fun get(ctx: Context): LedgerDb = inst ?: synchronized(this) {
            inst ?: run {
                System.loadLibrary("sqlcipher")
                val factory = SupportOpenHelperFactory(Secrets.dbKey(ctx))
                Room.databaseBuilder(ctx.applicationContext, LedgerDb::class.java, "udhaar.db")
                    .openHelperFactory(factory).build().also { inst = it }
            }
        }
    }
}

/** Keys and the ledger PIN live in EncryptedSharedPreferences, backed by the Android Keystore. */
object Secrets {
    private fun prefs(ctx: Context) = EncryptedSharedPreferences.create(
        ctx, "batao_secrets",
        MasterKey.Builder(ctx).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun dbKey(ctx: Context): ByteArray {
        val p = prefs(ctx)
        p.getString("db_key", null)?.let { return Base64.decode(it, Base64.NO_WRAP) }
        val k = ByteArray(32).also { SecureRandom().nextBytes(it) }
        p.edit().putString("db_key", Base64.encodeToString(k, Base64.NO_WRAP)).apply()
        return k
    }

    fun hasPin(ctx: Context) = prefs(ctx).contains("pin_hash")
    fun setPin(ctx: Context, pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        prefs(ctx).edit().putString("pin_salt", Base64.encodeToString(salt, Base64.NO_WRAP)).putString("pin_hash", hash(pin, salt)).apply()
    }
    fun checkPin(ctx: Context, pin: String): Boolean {
        val p = prefs(ctx)
        val salt = Base64.decode(p.getString("pin_salt", null) ?: return false, Base64.NO_WRAP)
        return MessageDigest.isEqual(hash(pin, salt).toByteArray(), (p.getString("pin_hash", "") ?: "").toByteArray())
    }
    private fun hash(pin: String, salt: ByteArray): String {
        var h = salt + pin.toByteArray()
        repeat(10_000) { h = MessageDigest.getInstance("SHA-256").digest(h + salt) }
        return Base64.encodeToString(h, Base64.NO_WRAP)
    }
}

/** Similar-name resolution: "Ramesh" -> "Ramesh Kumar", or ask "Ramesh Kumar ya Ramesh Yadav?". */
object NameMatch {
    sealed class R {
        data class Exact(val name: String) : R()
        data class Ambiguous(val options: List<String>) : R()
        data class New(val name: String) : R()
    }

    fun resolve(spoken: String, existing: List<String>): R {
        val s = spoken.trim().lowercase()
        existing.firstOrNull { it.lowercase() == s }?.let { return R.Exact(it) }
        val first = s.split(" ").first()
        val hits = existing.filter { e -> e.lowercase().split(" ").first() == first && (s.split(" ").size == 1 || e.lowercase().startsWith(s)) }
        return when (hits.size) {
            0 -> R.New(spoken.trim().split(" ").joinToString(" ") { it.replaceFirstChar(Char::uppercase) })
            1 -> R.Exact(hits[0])
            else -> R.Ambiguous(hits.take(3))
        }
    }

    /** Pick from options using any distinguishing word in the answer ("Yadav wala" -> "Ramesh Yadav"). */
    fun pick(answerTokens: List<String>, options: List<String>): String? {
        val toks = answerTokens.map { it.lowercase() }.toSet()
        val scored = options.map { o -> o to o.lowercase().split(" ").drop(1).count { it in toks } }
        val best = scored.maxByOrNull { it.second } ?: return null
        if (best.second == 0) {
            if (toks.any { it in setOf("pehla", "pehle", "first", "ek") }) return options.first()
            if (toks.any { it in setOf("doosra", "dusra", "second", "do") }) return options.getOrNull(1)
            return null
        }
        return if (scored.count { it.second == best.second } == 1) best.first else null
    }
}

@Serializable data class SeedEntry(val amount: Long, val note: String = "", val days_ago: Int = 0)
@Serializable data class SeedCustomer(val name: String, val entries: List<SeedEntry>)

class LedgerRepo(private val ctx: Context) {
    val dao by lazy { LedgerDb.get(ctx).dao() }

    /** Demo Mode: seed fake customers once from assets/demo/seeded_ledger.json. */
    suspend fun seedIfEmpty() {
        if (dao.customerCount() > 0) return
        val text = runCatching { ctx.assets.open("seeded_ledger.json").bufferedReader().use { it.readText() } }.getOrNull() ?: return
        val seed = Json { ignoreUnknownKeys = true }.decodeFromString<List<SeedCustomer>>(text)
        val day = 86_400_000L
        seed.forEach { c ->
            val id = customerId(c.name)
            c.entries.forEach { e -> dao.insertEntry(Entry(customerId = id, amount = e.amount, note = e.note, createdAt = System.currentTimeMillis() - e.days_ago * day)) }
        }
    }

    suspend fun customerId(name: String): Long = dao.byName(name)?.id ?: dao.insertCustomer(Customer(name = name)).let { if (it > 0) it else dao.byName(name)!!.id }
    suspend fun names() = dao.customers().map { it.name }
    suspend fun add(name: String, amount: Long, note: String = ""): Long = dao.insertEntry(Entry(customerId = customerId(name), amount = amount, note = note))
    suspend fun balanceOf(name: String): Long = dao.balancesNow().firstOrNull { it.name.equals(name, true) }?.balance ?: 0
    suspend fun top(n: Int) = dao.balancesNow().filter { it.balance > 0 }.take(n)
    suspend fun softDelete(entryId: Long) { dao.entry(entryId)?.let { dao.updateEntry(it.copy(deleted = true)) } }
    suspend fun undelete(entryId: Long) { dao.entry(entryId)?.let { dao.updateEntry(it.copy(deleted = false)) } }
    suspend fun edit(entryId: Long, amount: Long) { dao.entry(entryId)?.let { dao.updateEntry(it.copy(amount = amount)) } }
}

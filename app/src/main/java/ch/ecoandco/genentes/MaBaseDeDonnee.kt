package ch.ecoandco.genentes

import android.content.ContentValues
import android.content.Context
import android.database.MatrixCursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import java.util.Calendar
import org.json.JSONObject
import org.json.JSONArray


class MaBaseDeDonnees(private val context: Context) : SQLiteOpenHelper(context, "anniversaires.db", null, 1) {

    companion object {
        private const val TAG = "MaBaseDeDonnees"
    }

    override fun onCreate(db: SQLiteDatabase) {
        try {
            // 1. Création des tables (votre code précédent)
            val createParents = """CREATE TABLE parents (id INTEGER PRIMARY KEY AUTOINCREMENT, nomComplet TEXT)"""
            val createEnfants = """CREATE TABLE enfants (id INTEGER PRIMARY KEY AUTOINCREMENT, prenom TEXT, dateNaissance INTEGER, idParent1 INTEGER, idParent2 INTEGER)"""

            db.execSQL(createParents)
            db.execSQL(createEnfants)

            // 2. APPEL DE LA FONCTION DE TEST
            peuplerDonneesTest(db)
        } catch (e: Exception) {
            Log.e(TAG, "Erreur dans onCreate", e)
        }
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        try {
            db.execSQL("DROP TABLE IF EXISTS enfants")
            db.execSQL("DROP TABLE IF EXISTS parents")
            onCreate(db)
        } catch (e: Exception) {
            Log.e(TAG, "Erreur dans onUpgrade", e)
        }
    }

    // --- NOUVELLE FONCTION : Insère des faux données si la table est vide ---
    private fun peuplerDonneesTest(db: SQLiteDatabase) {
        try {
            // Vérifions si on a déjà des parents (pour ne pas doubler les données à chaque fois)
            val cursor = db.rawQuery("SELECT COUNT(*) FROM parents", null)
            cursor.moveToFirst()
            val count = cursor.getInt(0)
            cursor.close()

            if (count > 0) return // Si des données existent, on ne fait rien

            // Charger les données depuis le fichier sample_data.json
            val inputStream = context.resources.openRawResource(R.raw.sample_data)
            val jsonString = inputStream.bufferedReader().use { it.readText() }

            importFromJson(jsonString, db)
        } catch (e: Exception) {
            Log.e(TAG, "Erreur dans peuplerDonneesTest", e)
        }
    }

    /**
     * Convert date string in format "dd.MM.yyyy" to milliseconds
     */
    private fun parseDateToMillis(dateString: String): Long {
        return try {
            val parts = dateString.split(".")
            if (parts.size == 3) {
                val day = parts[0].toInt()
                val month = parts[1].toInt()
                val year = parts[2].toInt()

                val calendar = Calendar.getInstance()
                calendar.set(year, month - 1, day, 0, 0, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                calendar.timeInMillis
            } else {
                0L
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors du parsing de la date: $dateString", e)
            0L
        }
    }

    // Votre fonction de récupération (à garder telle quelle)
    fun recupererTousLesEnfantsAvecParents(quelTri: String = "enfants"): android.database.Cursor {
        return try {
            val colonneTri = when (quelTri) {
                "parents" -> "p1.nomComplet COLLATE NOCASE ASC"
                "date"    -> "e.dateNaissance ASC" // Pas de COLLATE NOCASE nécessaire pour des dates (Long/Int)
                else      -> "e.prenom COLLATE NOCASE ASC" // Valeur par défaut (enfants)
            }
            val db = this.readableDatabase
            val query = """
                SELECT e.id as enfantId, e.prenom as enfantPrenom, e.dateNaissance,
                       p1.nomComplet as parent1,
                       p2.nomComplet as parent2
                FROM enfants e
                JOIN parents p1 ON e.idParent1 = p1.id
                LEFT JOIN parents p2 ON e.idParent2 = p2.id
                ORDER BY $colonneTri 
            """.trimIndent()
            db.rawQuery(query, null)
        } catch (e: Exception) {
            Log.e(TAG, "Erreur dans recupererTousLesEnfantsAvecParents", e)
            MatrixCursor(arrayOf("enfantPrenom", "dateNaissance", "parent1", "parent2"))
        }
    }

    // N'oubliez pas votre fonction ajouterParent si vous voulez tester le bouton plus tard
    fun ajouterParent(nomComplet: String): Long {
        return try {
            val db = this.writableDatabase
            val values = ContentValues().apply {
                put("nomComplet", nomComplet)
            }
            // insert retourne l'ID de la ligne créée, ou -1 en cas d'erreur
            db.insert("parents", null, values)
        } catch (e: Exception) {
            Log.e(TAG, "Erreur dans ajouterParent", e)
            -1L
        }
    }
    fun ajouterEnfant(prenom: String, dateNaissance: Long): Long {
        return try {
            val db = this.writableDatabase
            val values = ContentValues().apply {
                put("prenom", prenom)
                put("dateNaissance", dateNaissance)
            }
            db.insert("enfants", null, values)
        } catch (e: Exception) {
            Log.e(TAG, "Erreur dans ajouterEnfant", e)
            -1L
        }
    }

    fun mettreAJourParentsEnfant(idEnfant: Long, idParent1: Long, idParent2: Long?): Boolean {
        return try {
            val db = this.writableDatabase
            val values = ContentValues().apply {
                put("idParent1", idParent1)
                // On met à jour idParent2 seulement s'il existe, sinon on met NULL
                if (idParent2 != null) {
                    put("idParent2", idParent2)
                } else {
                    putNull("idParent2")
                }
            }
            // Mise à jour : UPDATE Enfant SET idParent1=?, idParent2=? WHERE id=?
            val rowsAffected = db.update(
                "enfants",       // Nom de la table
                values,         // Les nouvelles valeurs
                "id = ?",       // Clause WHERE
                arrayOf(idEnfant.toString()) // Arguments pour le WHERE
            )
            rowsAffected > 0
        } catch (e: Exception) {
            Log.e(TAG, "Erreur dans mettreAJourParentsEnfant", e)
            false
        }
    }

    fun deleteLine(idEnfant: Int): Boolean {
        return try {
            val db = this.writableDatabase
            val rowsAffected = db.delete(
                "enfants","id = ?", arrayOf(idEnfant.toString())
            )
            rowsAffected > 0
        } catch (e: Exception) {
            Log.e(TAG, "Erreur dans la suppression", e)
            false
        }
    }

    /**
     * Export database data to JSON file
     */
    fun exportToJson(): String {
        return try {
            val parser = DataParser()
            // Create a simple user person representing the app user
            val user = createPersonFromDatabase(this.readableDatabase)
            parser.export(user)
        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors de l'export", e)
            "{}" // Return empty JSON object on error
        }
    }

    /**
     * Import from JSON file and populate database
     */
    fun importFromJson(json: String, db: SQLiteDatabase = this.writableDatabase): Boolean {
        return try {
            val parser = DataParser()
            val person = parser.import(json)
            if (person != null) {
                clearDatabase(db)
                populateDatabaseFromPerson(db, person)
                Log.i(TAG, "Données importées avec succès")
                true
            } else {
                Log.e(TAG, "Échec du parsing du fichier JSON")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors de l'import", e)
            false
        }
    }

    /**
     * Clear database tables
     */
    private fun clearDatabase(db: SQLiteDatabase) {
        try {
            db.delete("enfants", null, null)
            db.delete("parents", null, null)
        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors du nettoyage de la BDD", e)
        }
    }

    /**
     * Populate database from Person object
     */
    private fun populateDatabaseFromPerson(db: SQLiteDatabase, person: Person) {
        try {            
            // Insérer les parents
            val parentValues = ContentValues()
            val enfantValues = ContentValues()
            for (parent in person.amis.filter { it.enfants.isNotEmpty() }) {
                parentValues.clear()
                parentValues.put("id", parent.id)
                parentValues.put("nomComplet", "${parent.prenom} ${parent.nom}")
                db.insert("parents", null, parentValues)
                if (parent.conjoint != null) {
                    parentValues.clear()
                    parentValues.put("id", parent.conjoint!!.id)
                    parentValues.put("nomComplet", "${parent.conjoint!!.prenom} ${parent.conjoint!!.nom}")
                    db.insert("parents", null, parentValues)
                }
                for (enfant in parent.enfants) {
                    enfantValues.clear()
                    enfantValues.put("id", enfant.id)
                    enfantValues.put("prenom", enfant.prenom)
                    val dateMillis = enfant.dateNaissance?.let { parseDateToMillis(it) } ?: 0L
                    enfantValues.put("dateNaissance", dateMillis)
                    enfantValues.put("idParent1", parent.id)
                    
                    // Add second parent if exists
                    parent.conjoint?.let { conjoint ->
                        enfantValues.put("idParent2", conjoint.id)
                    } ?: run {
                        enfantValues.putNull("idParent2")
                    }
                    
                    db.insert("enfants", null, enfantValues)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors du remplissage de la BDD", e)
        }
    }

    /**
     * Populate Person from database
     */
    private fun createPersonFromDatabase(db: SQLiteDatabase): Person {

        // Create the root person (user/"Me")
        val rootPerson = Person(
            id = 0,
            prenom = "Me",
            nom = ""
        )

        var nextId = 1;

        try {            
            // Query all parents from database
            val parentCursor = db.rawQuery("SELECT id, nomComplet FROM parents", null)
            val parentMap = mutableMapOf<Int, Person>()
            
            if (parentCursor.moveToFirst()) {
                do {
                    val parentId = parentCursor.getInt(0)
                    val nomComplet = parentCursor.getString(1)
                    val parts = nomComplet.split(" ", limit = 2)
                    val prenom = parts[0]
                    val nom = if (parts.size > 1) parts[1] else ""
                    
                    parentMap[parentId] = Person(
                        id = nextId++,
                        prenom = prenom,
                        nom = nom
                    )
                } while (parentCursor.moveToNext())
            }
            parentCursor.close()
            
            // Query all children and build relationships
            val enfantCursor = db.rawQuery(
                "SELECT id, prenom, dateNaissance, idParent1, idParent2 FROM enfants",
                null
            )
            
            if (enfantCursor.moveToFirst()) {
                do {
                    val enfantId = enfantCursor.getInt(0)
                    val prenom = enfantCursor.getString(1)
                    val dateNaissance = enfantCursor.getLong(2)
                    val idParent1 = enfantCursor.getInt(3)
                    val idParent2 = if (enfantCursor.isNull(4)) null else enfantCursor.getInt(4)
                    
                    // Convert milliseconds back to date string "dd.MM.yyyy"
                    val dateString = if (dateNaissance != 0L) {
                        val calendar = Calendar.getInstance()
                        calendar.timeInMillis = dateNaissance
                        val day = calendar.get(Calendar.DAY_OF_MONTH).toString().padStart(2, '0')
                        val month = (calendar.get(Calendar.MONTH) + 1).toString().padStart(2, '0')
                        val year = calendar.get(Calendar.YEAR)
                        "$day.$month.$year"
                    } else {
                        null
                    }
                    
                    val enfant = Person(
                        id = nextId++,
                        prenom = prenom,
                        nom = "",
                        dateNaissance = dateString
                    )
                    
                    // Add child to parent1
                    if (parentMap.containsKey(idParent1)) {
                        val parent1 = parentMap[idParent1]!!
                        parent1.enfants = parent1.enfants + enfant
                    }
                    
                    // Add child to parent2 if exists
                    if (idParent2 != null && parentMap.containsKey(idParent2)) {
                        val parent2 = parentMap[idParent2]!!
                        parent2.enfants = parent2.enfants + enfant
                    }
                } while (enfantCursor.moveToNext())
            }
            enfantCursor.close()
            
            // Build conjoint relationships
            val parentIds = parentMap.keys.toList()
            for (i in parentIds.indices) {
                for (j in i + 1 until parentIds.size) {
                    val parent1 = parentMap[parentIds[i]]!!
                    val parent2 = parentMap[parentIds[j]]!!
                    
                    // Check if they have common children, which indicates they are a couple
                    val children1 = parent1.enfants.map { it.id }.toSet()
                    val children2 = parent2.enfants.map { it.id }.toSet()
                    val commonChildren = children1.intersect(children2)
                    
                    if (commonChildren.isNotEmpty()) {
                        parent1.conjoint = parent2
                        parent2.conjoint = parent1
                    }
                }
            }
            
            // Set all parents as amis (friends) of the root person
            rootPerson.amis = parentMap.values.toList()

            
        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors de la création d'un objet Person à partir de la BDD", e)
        }

        return rootPerson
    }
}
package ch.ecoandco.enfantsDesCopains

import android.content.ContentValues
import android.content.Context
import android.database.MatrixCursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import java.util.Calendar
import kotlin.compareTo


class MaBaseDeDonnees(private val context: Context) : SQLiteOpenHelper(context, "anniversaires.db", null, 2) {

    companion object {
        private const val TAG = "MaBaseDeDonnees"
    }

    override fun onCreate(db: SQLiteDatabase) {
        try {
            // 1. Création des tables (votre code précédent)
            val createParents = """CREATE TABLE parents (id INTEGER PRIMARY KEY AUTOINCREMENT, nomComplet TEXT, groupe TEXT)"""
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
        Log.d(TAG, "Migration de la version $oldVersion vers $newVersion")

        // Gestion pas à pas des migrations
        // Si on passe de 1 à 2 (ou plus), on exécute le bloc 1 puis le 2
        if (oldVersion < 2) {
            try {
                // Ajout de la colonne 'groupe' à la table 'parents'
                // IF NOT EXISTS évite une erreur si la colonne existe déjà (sécurité)
                db.execSQL("ALTER TABLE parents ADD COLUMN groupe TEXT")
                Log.d(TAG, "Mise à jour de la table de données avec succès.")
            } catch (e: Exception) {
                Log.e(TAG, "Erreur lors de la mise a jour.", e)
            }
        }

        // Si vous avez une version 3 plus tard, vous ajouterez un bloc :
        // if (oldVersion < 3) { ... }

    }

    // --- NOUVELLE FONCTION : Insère des fausses données si la table est vide ---
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
    fun recupererTousLesEnfantsAvecParents(quelTri: String = "date", quelGroupe: String? = "null"): android.database.Cursor {
        return try {
            val colonneTri = when (quelTri) {
                "parents" -> "p1.nomComplet COLLATE NOCASE ASC, e.prenom COLLATE NOCASE ASC"
                "enfants" -> "e.prenom COLLATE NOCASE ASC"
                "date"    -> """
                CASE 
                    WHEN strftime('%m-%d', e.dateNaissance/1000, 'unixepoch') >= strftime('%m-%d', 'now') 
                    THEN strftime('%Y', 'now') || '-' || strftime('%m-%d', e.dateNaissance/1000, 'unixepoch')
                    ELSE (strftime('%Y', 'now') + 1) || '-' || strftime('%m-%d', e.dateNaissance/1000, 'unixepoch')
                END ASC
            """.trimIndent().replace("\n", " ")
                else      -> "e.dateNaissance ASC"
            }

            val groupeTri = if (quelGroupe == null || quelGroupe == "null" || quelGroupe == "copains") {
                "p1.groupe = 'copains' OR p1.groupe IS NULL"
            } else {
                "p1.groupe = '$quelGroupe'"
            }
            val db = this.readableDatabase
            val query = """
            SELECT e.id as enfantId, e.prenom as enfantPrenom, e.dateNaissance,
                   p1.nomComplet as parent1,
                   p2.nomComplet as parent2, 
                   p1.groupe as groupeCategorie
            FROM enfants e
            JOIN parents p1 ON e.idParent1 = p1.id
            LEFT JOIN parents p2 ON e.idParent2 = p2.id
            WHERE $groupeTri
            ORDER BY $colonneTri 
        """.trimIndent()

            db.rawQuery(query, null)
        } catch (e: Exception) {
            Log.e(TAG, "Erreur dans la fonction qui récupère les enfants avec les parents.", e)
            MatrixCursor(arrayOf("enfantPrenom", "dateNaissance", "parent1", "parent2"))
        }
    }

    // N'oubliez pas votre fonction ajouterParent si vous voulez tester le bouton plus tard
    fun ajouterParent(nomComplet: String, groupe: String): Long {
        return try {
            val db = this.writableDatabase
            val values = ContentValues().apply {
                put("nomComplet", nomComplet)
                put("groupe", groupe)
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
            // Mise à jour : UPDATE Enfant SET idParent1 = ?, idParent2 = ? WHERE id = ?
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

    fun supprimerParIds(ids: List<Int>): Boolean {
        return try {
            if (ids.isEmpty()) return false // Sécurité : rien à supprimer

            val db = this.writableDatabase

            // 1. Créer la clause "IN (?, ?, ?)" avec autant de points d'interrogation que d'IDs
            val placeholders = ids.joinToString(",") { "?" }
            val whereClause = "id IN ($placeholders)"

            // 2. Convertir la liste d'Int en tableau de String (exigé par la fonction delete)
            val whereArgs = ids.map { it.toString() }.toTypedArray()

            // 3. Exécuter la suppression
            val rowsAffected = db.delete(
                "enfants",      // Nom de la table
                whereClause,    // "id IN (?, ?, ?)"
                whereArgs       // ["1", "2", "3"]
            )

            Log.d(TAG, "Suppression réussie : $rowsAffected ligne(s) affectée(s)")
            rowsAffected > 0

        } catch (e: Exception) {
            Log.e(TAG, "Erreur dans la suppression par IDs", e)
            false
        }
    }

    /**
     * Import from JSON file and populate database
     */
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
    fun importFromJson(jsonString: String, mode: String, db: SQLiteDatabase = this.writableDatabase): Boolean {
        return try {
            val rootJson = JSONObject(jsonString)
            val dataArray: JSONArray

            // 1. Extraire le tableau de données selon le format
            if (rootJson.has("data")) {
                // Nouveau format : {"mode": "...", "data": [...]}
                dataArray = rootJson.getJSONArray("data")
            } else {
                // Ancien format : [...] directement à la racine
                dataArray = JSONArray(jsonString)
            }

            if (mode == "replace") {
                // --- LOGIQUE ACTUELLE (REPLACE) ---
                // 1. Vider la base (DELETE FROM parents; DELETE FROM enfants;)
                // 2. Parser le JSON et réinsérer tout avec les nouveaux IDs (ou ceux du fichier si vous gardez la logique nextId)
                // C'est votre code actuel qui fonctionne déjà.
                return executeReplaceImport(dataArray)

            } else if (mode == "merge") {
                // --- NOUVELLE LOGIQUE (MERGE) ---
                return executeMergeImport(dataArray)
            } else {
                Log.e(TAG, "Mode inconnu: $mode")
                false
            }

        } catch (e: Exception) {
            Log.e(TAG, "Erreur globale d'import", e)
            false
        }
    }

    fun executeReplaceImport(dataArray: JSONArray, db: SQLiteDatabase = this.writableDatabase): Boolean {
        return try {
            val parser = DataParser()
            val person = parser.import(JSONArray)
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
    private fun executeMergeImport(dataArray: JSONArray, db: SQLiteDatabase = this.writableDatabase): Boolean {
        val idMapping = mutableMapOf<Int, Int>() // Map : Ancien ID (fichier) -> Nouvel ID (local)

        db.beginTransaction()
        try {
            // --- PASS 1 : Insertion de toutes les personnes (Parents et Enfants) ---
            // On crée d'abord tous les enregistrements pour obtenir leurs NOUVEAUX IDs locaux.
            for (i in 0 until dataArray.length()) {
                val personJson = dataArray.getJSONObject(i)
                val oldId = personJson.getInt("id")

                val prenom = personJson.getString("prenom")
                val nom = if (personJson.has("nom")) personJson.getString("nom") else ""
                val groupe = if (personJson.has("groupe")) personJson.getString("groupe") else ""
                val naissanceStr = if (personJson.has("naissance")) personJson.getString("naissance") else null

                var newLocalId: Int

                if (naissanceStr != null) {
                    // --- C'est un ENFANT ---
                    // Conversion date "dd.MM.yyyy" -> Timestamp
                    val timestamp = try {
                        val parts = naissanceStr.split(".")
                        if (parts.size == 3) {
                            val cal = Calendar.getInstance()
                            cal.set(Calendar.YEAR, parts[2].toInt())
                            cal.set(Calendar.MONTH, parts[1].toInt() - 1) // 0-based
                            cal.set(Calendar.DAY_OF_MONTH, parts[0].toInt())
                            cal.set(Calendar.HOUR_OF_DAY, 0)
                            cal.set(Calendar.MINUTE, 0)
                            cal.set(Calendar.SECOND, 0)
                            cal.set(Calendar.MILLISECOND, 0)
                            cal.timeInMillis
                        } else {
                            0L
                        }
                    } catch (e: Exception) {
                        Log.w("Import", "Erreur date: $naissanceStr", e)
                        0L
                    }

                    // Insertion avec idParent1 et idParent2 à 0/null pour l'instant
                    // On les corrigera dans le PASS 2
                    val stmt = db.compileStatement(
                        "INSERT INTO enfants (prenom, dateNaissance, idParent1, idParent2) VALUES (?, ?, 0, NULL)"
                    )
                    stmt.bindString(1, prenom)
                    stmt.bindLong(2, timestamp)
                    stmt.executeInsert()
                    newLocalId = db.lastInsertRowId().toInt()

                } else {
                    // --- C'est un PARENT ---
                    val nomComplet = if (nom.isNotEmpty()) "$prenom $nom" else prenom

                    val stmt = db.compileStatement(
                        "INSERT INTO parents (nomComplet, groupe) VALUES (?, ?)"
                    )
                    stmt.bindString(1, nomComplet)
                    stmt.bindString(2, groupe)
                    stmt.executeInsert()
                    newLocalId = db.lastInsertRowId().toInt()
                }

                // On stocke la correspondance Ancien ID -> Nouvel ID
                idMapping[oldId] = newLocalId
            }

            // --- PASS 2 : Mise à jour des liens (Enfants -> Parents) ---
            // On parcourt à nouveau le JSON pour relier les enfants à leurs parents
            // en utilisant la map de correspondance.
            for (i in 0 until dataArray.length()) {
                val personJson = dataArray.getJSONObject(i)
                val oldId = personJson.getInt("id")
                val newLocalId = idMapping[oldId]!!

                val naissanceStr = if (personJson.has("naissance")) personJson.getString("naissance") else null

                if (naissanceStr == null) {
                    // --- C'est un PARENT : on doit lier ses enfants à lui ---
                    if (personJson.has("relations")) {
                        val relations = personJson.getJSONObject("relations")
                        if (relations.has("enfants")) {
                            val enfantsArray = relations.getJSONArray("enfants")

                            for (j in 0 until enfantsArray.length()) {
                                val oldChildId = enfantsArray.getInt(j)

                                // Si l'enfant a été importé (il est dans la map)
                                if (idMapping.containsKey(oldChildId)) {
                                    val newChildId = idMapping[oldChildId]!!

                                    // On récupère les parents actuels de cet enfant dans la BDD locale
                                    val cursor = db.rawQuery(
                                        "SELECT idParent1, idParent2 FROM enfants WHERE id = ?",
                                        arrayOf(newChildId.toString())
                                    )

                                    if (cursor.moveToFirst()) {
                                        val currentP1 = cursor.getInt(0)
                                        // val currentP2 = if (cursor.isNull(1)) null else cursor.getInt(1)
                                        cursor.close()

                                        // Règle : "Parent1 = première occurrence trouvée"
                                        // Si idParent1 est vide (0) ou nul, on le remplit avec ce parent.
                                        // Sinon, on ignore (ou on pourrait remplir Parent2, mais la consigne dit "Parent1 première occurence")
                                        // Pour être plus robuste, on va remplir Parent1 si vide, sinon Parent2 si vide.

                                        if (currentP1 == 0) {
                                            db.execSQL(
                                                "UPDATE enfants SET idParent1 = ? WHERE id = ?",
                                                arrayOf(newLocalId, newChildId)
                                            )
                                        } else {
                                            // Optionnel : remplir Parent2 si vide
                                            // Pour l'instant, on suit strictement "Parent1 première occurence"
                                            // donc si P1 est déjà pris par un autre import précédent, on ne fait rien.
                                            // Si vous voulez absolument remplir P2, décommentez ci-dessous :
                                            db.execSQL(
                                                "UPDATE enfants SET idParent2 = ? WHERE id = ? AND idParent2 IS NULL",
                                                arrayOf(newLocalId, newChildId)
                                            )
                                        }
                                    } else {
                                        cursor.close()
                                    }
                                }
                            }
                        }
                    }
                }
            }

            db.setTransactionSuccessful()
            return true

        } catch (e: Exception) {
            Log.e("Import", "Erreur transaction merge", e)
            return false
        } finally {
            db.endTransaction()
        }
    }


    private fun createPersonFromDatabase(db: SQLiteDatabase): Person {

        // Create the root person (user/"Me")
        val rootPerson = Person(
            id = 0,
            prenom = "Me",
            groupe = "Copains",
            nom = ""
        )

        var nextId = 1;

        try {
            // Query all parents from database
            val parentCursor = db.rawQuery("SELECT id, nomComplet, groupe FROM parents", null)
            val parentMap = mutableMapOf<Int, Person>()

            if (parentCursor.moveToFirst()) {
                do {
                    val parentId = parentCursor.getInt(0)
                    val nomComplet = parentCursor.getString(1)
                    val groupe = parentCursor.getString(2)
                    val parts = nomComplet.split(" ", limit = 2)
                    val prenom = parts[0]
                    val nom = if (parts.size > 1) parts[1] else ""

                    parentMap[parentId] = Person(
                        id = nextId++,
                        prenom = prenom,
                        groupe = groupe,
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


    // Cette fonction retourne une LISTE de Personnes (Parents + Enfants sélectionnés)
// Elle conserve les VRAIS IDs de la base de données.
     fun createPersonsFromSelection(selectedChildIds: List<Int>): List<Person> {
        val peopleMap = mutableMapOf<Int, Person>()

        if (selectedChildIds.isEmpty()) return emptyList()
        val db = this.writableDatabase

        try {
            // ÉTAPE 1 : Identifier les IDs de parents à charger
            // On construit une clause SQL "IN (id1, id2, ...)" pour les enfants
            val placeholders = selectedChildIds.joinToString(",") { "?" }

            // On récupère les IDs des parents concernés par ces enfants
            val parentIdsSet = mutableSetOf<Int>()
            val parentQuery = db.rawQuery(
                "SELECT idParent1, idParent2 FROM enfants WHERE id IN ($placeholders)",
                selectedChildIds.map { it.toString() }.toTypedArray()
            )

            if (parentQuery.moveToFirst()) {
                do {
                    if (!parentQuery.isNull(0)) parentIdsSet.add(parentQuery.getInt(0))
                    if (!parentQuery.isNull(1)) parentIdsSet.add(parentQuery.getInt(1))
                } while (parentQuery.moveToNext())
            }
            parentQuery.close()

            // L'ensemble des IDs à charger = Enfants sélectionnés + Leurs Parents
            val allIdsToLoad = selectedChildIds.toSet() + parentIdsSet

            // ÉTAPE 2 : Charger les Parents
            // On utilise une requête avec "IN" pour charger uniquement les parents nécessaires
            val parentPlaceholders = parentIdsSet.joinToString(",") { "?" }
            val parentCursor = db.rawQuery(
                "SELECT id, nomComplet, groupe FROM parents WHERE id IN ($parentPlaceholders)",
                parentIdsSet.map { it.toString() }.toTypedArray()
            )

            if (parentCursor.moveToFirst()) {
                do {
                    val realId = parentCursor.getInt(0) // VRAI ID BDD
                    val nomComplet = parentCursor.getString(1)
                    val groupe = parentCursor.getString(2)
                    val parts = nomComplet.split(" ", limit = 2)
                    val prenom = parts[0]
                    val nom = if (parts.size > 1) parts[1] else ""

                    peopleMap[realId] = Person(
                        id = realId, // On garde le vrai ID !
                        prenom = prenom,
                        nom = nom,
                        groupe = groupe
                    )
                } while (parentCursor.moveToNext())
            }
            parentCursor.close()

            // ÉTAPE 3 : Charger les Enfants sélectionnés
            val enfantCursor = db.rawQuery(
                "SELECT id, prenom, dateNaissance, idParent1, idParent2 FROM enfants WHERE id IN ($placeholders)",
                selectedChildIds.map { it.toString() }.toTypedArray()
            )

            if (enfantCursor.moveToFirst()) {
                do {
                    val realId = enfantCursor.getInt(0) // VRAI ID BDD
                    val prenom = enfantCursor.getString(1)
                    val dateNaissance = enfantCursor.getLong(2)
                    val idParent1 = enfantCursor.getInt(3)
                    val idParent2 = if (enfantCursor.isNull(4)) null else enfantCursor.getInt(4)

                    val dateString = if (dateNaissance != 0L) {
                        val calendar = Calendar.getInstance()
                        calendar.timeInMillis = dateNaissance
                        val day = calendar.get(Calendar.DAY_OF_MONTH).toString().padStart(2, '0')
                        val month = (calendar.get(Calendar.MONTH) + 1).toString().padStart(2, '0')
                        val year = calendar.get(Calendar.YEAR)
                        "$day.$month.$year"
                    } else null

                    val enfant = Person(
                        id = realId, // On garde le vrai ID !
                        prenom = prenom,
                        nom = "",
                        dateNaissance = dateString
                    )

                    // On l'ajoute à la map globale
                    peopleMap[realId] = enfant

                    // On met à jour les liens "enfants" dans les parents (si chargés)
                    // Note: On modifie la liste via une propriété mutable si possible,
                    // ou on recrée la liste comme dans votre code original.
                    // Ici, comme Person a 'var enfants: List<Person>', on doit réassigner.

                    if (peopleMap.containsKey(idParent1)) {
                        val parent1 = peopleMap[idParent1]!!
                        parent1.enfants = parent1.enfants + enfant
                    }
                    if (idParent2 != null && peopleMap.containsKey(idParent2)) {
                        val parent2 = peopleMap[idParent2]!!
                        parent2.enfants = parent2.enfants + enfant
                    }

                } while (enfantCursor.moveToNext())
            }
            enfantCursor.close()

            // ÉTAPE 4 : Reconstruire les conjoints (simplifié pour la sélection)
            // On ne vérifie les conjoints que parmi les parents chargés dans cette sélection
            val loadedParents = peopleMap.values.filter { p -> p.id !in selectedChildIds } // Approximation: les parents ne sont pas dans la liste des IDs enfants

            // On utilise la même logique que votre code original pour trouver les conjoints par enfants communs
            // Mais limitée aux parents chargés dans 'peopleMap'
            val parentIdsList = loadedParents.map { it.id }

            for (i in parentIdsList.indices) {
                for (j in i + 1 until parentIdsList.size) {
                    val p1 = peopleMap[parentIdsList[i]]!!
                    val p2 = peopleMap[parentIdsList[j]]!!

                    // 1. On transforme les listes d'enfants en Set d'IDs pour pouvoir utiliser intersect
                    val childrenIds1 = p1.enfants.map { it.id }.toSet()
                    val childrenIds2 = p2.enfants.map { it.id }.toSet()

                    // 2. On trouve l'intersection des deux ensembles d'IDs
                    val commonChildrenIds = childrenIds1.intersect(childrenIds2)

                    // 3. Si l'intersection n'est pas vide, ce sont des conjoints
                    if (commonChildrenIds.isNotEmpty()) {
                        p1.conjoint = p2
                        p2.conjoint = p1
                        // On met aussi à jour les IDs pour l'export
                        p1.conjointId = p2.id
                        p2.conjointId = p1.id
                    }
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors de la création de la sélection", e)
            return emptyList()
        }

        // Retourne la liste des objets Person (Parents + Enfants) avec leurs vrais IDs
        return peopleMap.values.toList()
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
                parentValues.put("groupe", parent.groupe)
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

    // Pas de 'suspend', c'est une fonction normale
    fun recupIDparentsEtChangeCategorie (ids: List<Int>, keyCategorie: String): Boolean {
        return try {
            if (ids.isEmpty()) return false

            val db = this.writableDatabase
            val parentsIdsToUpdate = mutableListOf<Int>()

            // --- REQUÊTE DE LECTURE (Natif pur) ---
            val placeholders = ids.joinToString(",") { "?" }
            val sqlRecup = "SELECT idParent1, idParent2 FROM enfants WHERE id IN ($placeholders)"

            // Conversion des Int en String pour les arguments de rawQuery
            val selectionArgs = ids.map { it.toString() }.toTypedArray()

            val cursor = db.rawQuery(sqlRecup, selectionArgs)

            cursor.use { // Fermeture automatique
                if (it.moveToFirst()) {
                    do {
                        val col1 = it.getColumnIndex("idParent1")
                        val col2 = it.getColumnIndex("idParent2")

                        if (!it.isNull(col1)) parentsIdsToUpdate.add(it.getInt(col1))
                        if (!it.isNull(col2)) parentsIdsToUpdate.add(it.getInt(col2))
                    } while (it.moveToNext())
                }
            }

            if (parentsIdsToUpdate.isEmpty()) return false

            // --- REQUÊTE D'ÉCRITURE (Natif pur) ---
            val uniqueParentsIds = parentsIdsToUpdate.distinct()
            val placeholdersParents = uniqueParentsIds.joinToString(",") { "?" }
            val sqlUpdate = "UPDATE parents SET groupe = ? WHERE id IN ($placeholdersParents)"

            val updateArgs = mutableListOf<String>()
            updateArgs.add(keyCategorie)
            updateArgs.addAll(uniqueParentsIds.map { it.toString() })

            db.execSQL(sqlUpdate, updateArgs.toTypedArray())

            true

        } catch (e: Exception) {
            Log.e(TAG, "Erreur SQL native : ${e.message}", e)
            false
        }
    }
}
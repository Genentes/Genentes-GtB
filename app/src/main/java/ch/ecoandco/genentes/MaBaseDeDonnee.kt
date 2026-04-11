package ch.ecoandco.genentes

import android.content.ContentValues
import android.content.Context
import android.database.MatrixCursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import java.util.Calendar


class MaBaseDeDonnees(context: Context) : SQLiteOpenHelper(context, "anniversaires.db", null, 1) {

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

            // --- INSERTION DES PARENTS ---
            val values = ContentValues()

            // Parent 1 : Jean Dupont
            values.put("nomComplet", "Jean B")
            val idJean = db.insert("parents", null, values).toInt()

            // Parent 2 : Marie Dupont
            values.clear()
            values.put("nomComplet", "Marie Dupont")
            val idMarie = db.insert("parents", null, values).toInt()

            // Parent 3 : Paul Martin
            values.clear()
            values.put("nomComplet", "Paul spaces")
            val idPaul = db.insert("parents", null, values).toInt()

            // --- INSERTION DES ENFANTS ---
            values.clear()

            // Enfant 1 : Louis (Parents : Jean & Marie)
            values.put("prenom", "Louis")
            values.put("dateNaissance", getTimeStamp(2018, 5, 12)) // 12 Mai 2018
            values.put("idParent1", idJean)
            values.put("idParent2", idMarie)
            db.insert("enfants", null, values)

            // Enfant 2 : Sophie (Parents : Jean & Marie)
            values.clear()
            values.put("prenom", "Sophie")
            values.put("dateNaissance", getTimeStamp(2020, 8, 25)) // 25 Août 2020
            values.put("idParent1", idJean)
            values.put("idParent2", idMarie)
            db.insert("enfants", null, values)

            // Enfant 3 : Lucas (Parent : Paul seul)
            values.clear()
            values.put("prenom", "Lucas")
            values.put("dateNaissance", getTimeStamp(2019, 2, 10)) // 10 Février 2019
            values.put("idParent1", idPaul)
            values.putNull("idParent2") // Parent unique
            db.insert("enfants", null, values)
        } catch (e: Exception) {
            Log.e(TAG, "Erreur dans peuplerDonneesTest", e)
        }
    }

    // Petite fonction utilitaire pour créer un timestamp facilement
    private fun getTimeStamp(year: Int, month: Int, day: Int): Long {
        return try {
            val calendar = Calendar.getInstance()
            calendar.set(year, month - 1, day) // Le mois commence à 0 en Java/Kotlin (0=Janvier)
            calendar.timeInMillis
        } catch (e: Exception) {
            Log.e(TAG, "Erreur dans getTimeStamp", e)
            0L
        }
    }

    // Votre fonction de récupération (à garder telle quelle)
    fun recupererTousLesEnfantsAvecParents(quelTri: String = "enfant"): android.database.Cursor {
        return try {
            val colonneTri = when (quelTri) {
                "parents" -> "p1.nomComplet COLLATE NOCASE ASC"
                "date"    -> "e.dateNaissance ASC" // Pas de COLLATE NOCASE nécessaire pour des dates (Long/Int)
                else      -> "e.prenom COLLATE NOCASE ASC" // Valeur par défaut (enfants)
            }
            val db = this.readableDatabase
            val query = """
                SELECT e.prenom as enfantPrenom, e.dateNaissance,
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

    fun deleteLine(idEnfant: Long): Boolean {
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
}
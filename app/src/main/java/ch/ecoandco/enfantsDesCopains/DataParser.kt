package ch.ecoandco.enfantsDesCopains

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/**
* Classe de données universelle représentant toute personne du système (parents, enfants, conjoint, amis)
* Les propriétés sont optionnelles selon le rôle de la personne
*
* @param id Identifiant unique de la personne
* @param prenom Prénom de la personne
* @param nom Nom de famille (optionnel)
* @param groupe Groupe (optionnel)
* @param conjointId ID de référence au conjoint (stocké avant la résolution)
* @param conjoint Objet Person du conjoint (après résolution des références)
* @param enfantIds Liste des IDs des enfants (stockée avant la résolution)
* @param enfants Liste des objets Person des enfants (après résolution)
* @param amisIds Liste des IDs des amis (stockée avant la résolution)
* @param amis Liste des objets Person des amis (après résolution)
* @param dateNaissance Date de naissance au format "dd.MM.yyyy"
*/
class Person(
    val id: Int,
    val prenom: String,
    val nom: String = "",
    val groupe: String? = null,
    var conjointId: Int? = null,
    var conjoint: Person? = null,
    var enfantIds: List<Int> = emptyList(),
    var enfants: List<Person> = emptyList(),
    val amisIds: List<Int> = emptyList(),
    var amis: List<Person> = emptyList(),
    val dateNaissance: String? = null
)

/**
 * Classe responsable du parsing et de la sérialisation des données JSON
 * Convertit le JSON en objets Person et vice-versa
 * Gère également l'import/export de fichiers
 */
class DataParser {

    companion object {
        private const val TAG = "SampleDataParser"
    }

    /**
     * Parse une chaîne JSON pour extraire les données des personnes
     * Utilise une approche en deux passes:
     *   1. Première passe: Parse tous les IDs de référence (conjointId, enfantIds, amisIds)
     *   2. Deuxième passe: Résout ces IDs en objets Person réels
     *
     * @return L'objet Person avec id=0 (représentant "Moi") avec toutes ses relations résolues
     */
    fun import(jsonArray: JSONArray): Person? {
        return try {

            // Première passe: Parse tous les objets Person sans résoudre les références
            // Chaque personne est stockée dans une map avec son ID comme clé
            val peopleMap = mutableMapOf<Int, Person>()
            for (i in 0 until jsonArray.length()) {
                val personJson = jsonArray.getJSONObject(i)
                val person = parsePerson(personJson)
                peopleMap[person.id] = person
            }

            // Deuxième passe: Résout les IDs de référence en objets Person réels
            // Crée d'abord une map des personnes résolues, puis met à jour les références
            val resolvedMap = peopleMap.toMutableMap()
            for ((id, person) in resolvedMap) {
                person.conjoint = person.conjointId?.let { resolvedMap[it] }
                person.enfants = person.enfantIds.mapNotNull { resolvedMap[it] }
                person.amis = person.amisIds.mapNotNull { resolvedMap[it] }
            }

            // Retourne la personne avec id=0 (représentant "Moi") avec toutes ses relations résolues
            resolvedMap[0]
        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors du parsing du JSON", e)
            null
        }
    }

    /**
     * Parse un objet JSON individuel pour créer un objet Person
     * Cette fonction crée une Person avec les IDs de références, pas les objets résolus
     * La résolution des références se fait dans la fonction parse() en deuxième passe
     *
     * @param jsonObject Objet JSON représentant une personne
     * @return Objet Person avec les IDs de référence (pas encore résolus)
     */
    private fun parsePerson(jsonObject: JSONObject): Person {
        // 1. ID : Utiliser optInt pour ne pas planter si manquant. Défaut à -1.
        val id = jsonObject.optInt("id", -1)

        // 2. Noms : Utiliser optString avec une valeur par défaut
        val prenom = jsonObject.optString("prenom", "Inconnu")
        val nom = jsonObject.optString("nom", "")

        // 3. Groupe : Clé optionnelle, vide si absente (compatible ancien fichier sans groupe)
        val groupe = jsonObject.optString("groupe", "copains")

        // Initialisation des IDs
        var conjointId: Int? = null
        var enfantIds = listOf<Int>()
        var amisIds = listOf<Int>()

        // 4. Relations : Vérifier l'existence avant de lire
        if (jsonObject.has("relations") && !jsonObject.isNull("relations")) {
            try {
                val relationsJson = jsonObject.getJSONObject("relations")

                // Conjoint
                if (relationsJson.has("conjoint")) {
                    conjointId = relationsJson.optInt("conjoint", -1).takeIf { it != -1 }
                }

                // Enfants (Gère à la fois JSONArray d'IDs ou autre format si besoin)
                if (relationsJson.has("enfants") && !relationsJson.isNull("enfants")) {
                    val enfantsArray = relationsJson.optJSONArray("enfants")
                    if (enfantsArray != null) {
                        val ids = mutableListOf<Int>()
                        for (i in 0 until enfantsArray.length()) {
                            // optInt évite le plantage si un élément n'est pas un int
                            ids.add(enfantsArray.optInt(i, -1))
                        }
                        enfantIds = ids.filter { it != -1 }
                    }
                }

                // Amis
                if (relationsJson.has("amis") && !relationsJson.isNull("amis")) {
                    val amisArray = relationsJson.optJSONArray("amis")
                    if (amisArray != null) {
                        val ids = mutableListOf<Int>()
                        for (i in 0 until amisArray.length()) {
                            ids.add(amisArray.optInt(i, -1))
                        }
                        amisIds = ids.filter { it != -1 }
                    }
                }
            } catch (e: Exception) {
                // Si le bloc relations est mal formé, on continue avec les valeurs par défaut (null/empty)
                Log.w("DataParser", "Erreur lecture relations pour $prenom, ignoré.", e)
            }
        }

        // 5. Date de naissance : Gérer les DEUX formats (String "dd.MM.yyyy" ET Timestamp Long)
        val dateNaissance: String? = when {
            // Cas 1: Clé "naissance" existe (Nouveau format String)
            jsonObject.has("naissance") && !jsonObject.isNull("naissance") -> {
                jsonObject.optString("naissance", null)
            }
            // Cas 2: Clé "dateNaissance" existe (Timestamp Long - comme vu dans vos logs)
            jsonObject.has("dateNaissance") && !jsonObject.isNull("dateNaissance") -> {
                val timestamp = jsonObject.optLong("dateNaissance", 0L)
                if (timestamp > 0) {
                    // Convertir le timestamp en String "dd.MM.yyyy" pour que votre classe Person soit contente
                    convertirTimestampEnDateFr(timestamp)
                } else {
                    null
                }
            }
            // Cas 3: Clé "date" existe (Autre variante possible)
            jsonObject.has("date") && !jsonObject.isNull("date") -> {
                val timestamp = jsonObject.optLong("date", 0L)
                if (timestamp > 0) convertirTimestampEnDateFr(timestamp) else null
            }
            else -> null
        }

        return Person(
            id = id,
            prenom = prenom,
            nom = nom,
            groupe = groupe, // Sera vide pour les anciens fichiers, ce qui est gérable
            conjointId = conjointId,
            enfantIds = enfantIds,
            amisIds = amisIds,
            dateNaissance = dateNaissance
        )
    }

    // Fonction utilitaire à ajouter pour la conversion
    private fun convertirTimestampEnDateFr(timestamp: Long): String {
        val sdf = java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.getDefault())
        // Important : ajuster le fuseau horaire si nécessaire, ou utiliser 'localtime' comme vu avant
        sdf.timeZone = java.util.TimeZone.getDefault()
        return sdf.format(java.util.Date(timestamp))
    }

    /**
     * Exporte un objet Person et toutes ses relations en JSON
     * Crée un tableau JSON avec toutes les personnes liées (amis, enfants, conjoint, etc.)
     * Les relations sont stockées comme des IDs, pas comme des objets imbriqués
     *
     * @return String contenant le JSON formaté avec indentation (2 espaces)
     */
    // --- NOUVELLE SIGNATURE PRINCIPALE ---
    // Cette version gère l'enveloppe JSON (mode + data)
    // Elle accepte une liste de personnes déjà prêtes à être exportées
    fun export(personsToExport: List<Person>, mode: String): String {
        return try {
            val array = mutableListOf<JSONObject>()

            // On sérialise chaque personne de la liste fournie
            // On trie par ID pour avoir un JSON propre et prévisible
            for (p in personsToExport.sortedBy { it.id }) {
                val personJson = JSONObject()
                personJson.put("id", p.id)
                personJson.put("prenom", p.prenom)

                if (p.nom.isNotEmpty()) {
                    personJson.put("nom", p.nom)
                }
                if (p.groupe != null) {
                    personJson.put("groupe", p.groupe)
                }
                if (p.dateNaissance != null) {
                    personJson.put("naissance", p.dateNaissance)
                }

                // Gestion des relations
                val relationsJson = JSONObject()
                var hasRelations = false

                // Conjoint
                if (p.conjointId != null) {
                    relationsJson.put("conjoint", p.conjointId)
                    hasRelations = true
                } else if (p.conjoint != null) {
                    relationsJson.put("conjoint", p.conjoint!!.id)
                    hasRelations = true
                }

                // Enfants
                if (p.enfantIds.isNotEmpty()) {
                    relationsJson.put("enfants", JSONArray(p.enfantIds))
                    hasRelations = true
                } else if (p.enfants.isNotEmpty()) {
                    val enfantIds = p.enfants.map { it.id }
                    relationsJson.put("enfants", JSONArray(enfantIds))
                    hasRelations = true
                }

                // Amis
                if (p.amisIds.isNotEmpty()) {
                    relationsJson.put("amis", JSONArray(p.amisIds))
                    hasRelations = true
                } else if (p.amis.isNotEmpty()) {
                    val amisIds = p.amis.map { it.id }
                    relationsJson.put("amis", JSONArray(amisIds))
                    hasRelations = true
                }

                if (hasRelations) {
                    personJson.put("relations", relationsJson)
                }

                array.add(personJson)
            }

            // --- CRÉATION DE L'ENVELOPPE ---
            val rootJson = JSONObject()
            rootJson.put("mode", mode) // "replace" ou "merge"
            rootJson.put("data", JSONArray(array))

            // Retourne le JSON formaté avec l'enveloppe
            rootJson.toString(2) // Le paramètre 2 ajoute une indentation jolie pour la lecture

        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors de l'export en JSON", e)
            ""
        }
    }

    // --- FONCTION DE COMMODITÉ POUR L'EXPORT COMPLET (RÉCURSIF) ---
    // Celle-ci garde exactement votre logique actuelle :
    // Elle part de la personne racine (ID 0), collecte tout le monde récursivement,
    // puis appelle la fonction principale ci-dessus en mode "replace".
    fun export(person: Person): String {
        val exportedIds = mutableSetOf<Int>()
        // Collecte récursive de tout le graphe connecté à 'person'
        val allPeople = collectAllPeople(person, exportedIds)

        // On délègue le travail de sérialisation à la nouvelle fonction en mode "replace"
        return export(allPeople, "replace")
    }

    // --- FONCTION DE COMMODITÉ POUR L'EXPORT SÉLECTIF ---
    // Celle-ci vous servira pour votre nouvelle fonctionnalité.
    // Vous lui passez la liste spécifique (ex: enfants sélectionnés + leurs parents),
    // et elle exporte en mode "merge".
    fun exportSelection(persons: List<Person>): String {
        // On suppose ici que 'persons' contient déjà tous les maillons nécessaires
        // (enfants + parents liés) pour que les relations soient valides.
        // Si vous avez besoin de récursivité partielle, on pourra l'ajouter ici aussi.
        return export(persons, "merge")
    }

    /**
     * Collecte récursivement toutes les personnes liées à une personne donnée
     * Évite les doublons en utilisant un Set des IDs déjà traités
     * Traverse le graphe complet des relations: conjoint, enfants, amis
     *
     * @param person La personne de départ
     * @param collected Set des IDs déjà collectés (pour éviter les boucles infinies)
     * @return Liste complète de toutes les personnes liées
     */
    private fun collectAllPeople(person: Person, collected: MutableSet<Int>): List<Person> {
        val result = mutableListOf<Person>()

        // Vérifie si on a déjà traité cette personne (évite les doublons et boucles infinies)
        if (person.id in collected) {
            return result
        }

        // Marque cette personne comme traitée et l'ajoute au résultat
        collected.add(person.id)
        result.add(person)

        // Ajoute récursivement le conjoint et ses relations
        person.conjoint?.let {
            result.addAll(collectAllPeople(it, collected))
        }

        // Ajoute récursivement tous les enfants et leurs relations
        for (enfant in person.enfants) {
            result.addAll(collectAllPeople(enfant, collected))
        }

        // Ajoute récursivement tous les amis et leurs relations
        for (ami in person.amis) {
            result.addAll(collectAllPeople(ami, collected))
        }

        return result
    }
}

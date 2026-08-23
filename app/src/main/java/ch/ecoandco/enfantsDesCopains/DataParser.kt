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
    val groupe: String = "",
    var conjointId: Int? = null,
    var conjoint: Person? = null,
    var enfantIds: List<Int> = emptyList(),
    var enfants: List<Person> = emptyList(),
    var amisIds: List<Int> = emptyList(),
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
     * Utilise une approche en deux passes :
     *   1. Première passe : Parse tous les IDs de référence (conjointId, enfantIds, amisIds)
     *   2. Deuxième passe : Résout ces IDs en objets Person réels
     * 
     * @param jsonString Contenu JSON à parser (format : tableau de personnes)
     * @return L'objet Person avec id=0 (représentant "Moi") avec toutes ses relations résolues
     */
    fun import(jsonString: String): Person? {
        return try {
            val jsonArray = JSONArray(jsonString)

            // Première passe : Parse tous les objets Person sans résoudre les références
            // Chaque personne est stockée dans une map avec son ID comme clé
            val peopleMap = mutableMapOf<Int, Person>()
            for (i in 0 until jsonArray.length()) {
                val personJson = jsonArray.getJSONObject(i)
                val person = parsePerson(personJson)
                peopleMap[person.id] = person
            }

            // Deuxième passe : Résout les IDs de référence en objets Person réels
            // Crée d'abord une map des personnes résolues, puis met à jour les références
            val resolvedMap = peopleMap.toMutableMap()
            for ((_, person) in resolvedMap) {
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
        // Extraction des propriétés principales
        val id = jsonObject.getInt("id")
        val prenom = jsonObject.getString("prenom")
        val nom = if (jsonObject.has("nom")) jsonObject.getString("nom") else ""
        val groupe = if (jsonObject.has("groupe")) jsonObject.getString("groupe") else ""

        // Initialisation des IDs de référence (à None/vide par défaut)
        var conjointId: Int? = null
        var enfantIds = listOf<Int>()
        var amisIds = listOf<Int>()
        
        // Parse le champ "relations" qui contient les références aux autres personnes
        if (jsonObject.has("relations") && !jsonObject.isNull("relations")) {
            val relationsJson = jsonObject.getJSONObject("relations")
            
            // Extraction de l'ID du conjoint (un seul ID, pas un tableau)
            if (relationsJson.has("conjoint") && !relationsJson.isNull("conjoint")) {
                conjointId = relationsJson.getInt("conjoint")
            }
            
            // Extraction des IDs des enfants (tableau d'IDs)
            if (relationsJson.has("enfants") && !relationsJson.isNull("enfants")) {
                val enfantsArray = relationsJson.getJSONArray("enfants")
                val ids = mutableListOf<Int>()
                for (i in 0 until enfantsArray.length()) {
                    ids.add(enfantsArray.getInt(i))
                }
                enfantIds = ids
            }
            
            // Extraction des IDs des amis (tableau d'IDs)
            if (relationsJson.has("amis") && !relationsJson.isNull("amis")) {
                val amisArray = relationsJson.getJSONArray("amis")
                val ids = mutableListOf<Int>()
                for (i in 0 until amisArray.length()) {
                    ids.add(amisArray.getInt(i))
                }
                amisIds = ids
            }
        }

        // Extraction de la date de naissance au format "dd.MM.yyyy"
        val dateNaissance = if (jsonObject.has("naissance") && !jsonObject.isNull("naissance")) {
            jsonObject.getString("naissance")
        } else {
            null
        }

        // Création et retour de l'objet Person avec les IDs de référence
        return Person(
            id = id,
            prenom = prenom,
            nom = nom,
            groupe = groupe,
            conjointId = conjointId,
            enfantIds = enfantIds,
            amisIds = amisIds,
            dateNaissance = dateNaissance
        )
    }

    /**
     * Exporte un objet Person et toutes ses relations en JSON
     * Crée un tableau JSON avec toutes les personnes liées (amis, enfants, conjoint, etc.)
     * Les relations sont stockées comme des IDs, pas comme des objets imbriqués
     * 
     * @param person est la personne à exporter (généralement id=0 "Moi").
     * @return String contenant le JSON formaté avec indentation (2 espaces)
     */
    fun export(person: Person): String {
        return try {
            val array = mutableListOf<JSONObject>()
            val exportedIds = mutableSetOf<Int>()
            
            // Collecte récursivement toutes les personnes liées à la personne principale
            // Cela inclut le conjoint, les enfants, les amis, etc.
            val allPeople = collectAllPeople(person, exportedIds)
            
            // Crée un objet JSON pour chaque personne
            for (p in allPeople.sortedBy { it.id }) {
                val personJson = JSONObject()
                personJson.put("id", p.id)
                personJson.put("prenom", p.prenom)
                // Ajoute le nom s'il existe
                if (p.nom.isNotEmpty()) {
                    personJson.put("nom", p.nom)
                }
                if (p.groupe.isNotEmpty()) {
                    personJson.put("groupe", p.groupe)
                }
                // Ajoute la date de naissance s'elle existe
                if (p.dateNaissance != null) {
                    personJson.put("naissance", p.dateNaissance)
                }
                
                // Ajoute le champ "relations" avec les IDs de référence
                val relationsJson = JSONObject()
                var hasRelations = false
                
                // Ajoute l'ID du conjoint s'il existe
                if (p.conjointId != null) {
                    relationsJson.put("conjoint", p.conjointId)
                    hasRelations = true
                }
                else if (p.conjoint != null) {
                    relationsJson.put("conjoint", p.conjoint!!.id)
                    hasRelations = true
                }
                // Ajoute les IDs des enfants s'il en existe
                if (p.enfantIds.isNotEmpty()) {
                    relationsJson.put("enfants", JSONArray(p.enfantIds))
                    hasRelations = true
                }
                else if (p.enfants.isNotEmpty()) {
                    val enfantIds = p.enfants.map { it.id }
                    relationsJson.put("enfants", JSONArray(enfantIds))
                    hasRelations = true
                }
                // Ajoute les IDs des amis s'il en existe
                if (p.amisIds.isNotEmpty()) {
                    relationsJson.put("amis", JSONArray(p.amisIds))
                    hasRelations = true
                }
                else if (p.amis.isNotEmpty()) {
                    val amisIds = p.amis.map { it.id }
                    relationsJson.put("amis", JSONArray(amisIds))
                    hasRelations = true
                }                
                // Ajoute le champ relations seulement s'il y a quelque chose à ajouter
                if (hasRelations) {
                    personJson.put("relations", relationsJson)
                }
                
                array.add(personJson)
            }
            
            // Retourne le JSON formaté
            "[\n" + array.joinToString(",\n") { "  $it" } + "]"
        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors de l'export en JSON", e)
            ""
        }
    }

    /**
     * Collecte récursivement toutes les personnes liées à une personne donnée
     * Évite les doublons en utilisant un Set des IDs déjà traités
     * Traverse le graphe complet des relations : conjoint, enfants, amis
     * 
     * @param person La personne de départ
     * @param collected Set des IDs (pour éviter les boucles infinies)
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

    /**
     * Exporte uniquement une sélection d'enfants et leurs parents directs.
     * Idéal pour exporter un sous-ensemble de la base sans tout le graphe (amis, etc.).
     *
     * @param allPeople Liste complète de toutes les personnes (parents et enfants) issues de la BDD.
     * @param childIdsToExport Liste des IDs des enfants à exporter.
     * @return String JSON contenant uniquement les enfants sélectionnés et leurs parents.
     */
    fun exportChildrenWithParents(allPeople: List<Person>, childIdsToExport: List<Int>): String {
        return try {
            val exportedIds = mutableSetOf<Int>()
            val peopleToExport = mutableListOf<Person>()

            // 1. Identifier et ajouter les enfants sélectionnés
            val selectedChildren = allPeople.filter { it.id in childIdsToExport }
            if (selectedChildren.isEmpty()) {
                Log.w(TAG, "Aucun enfant trouvé pour les IDs: $childIdsToExport")
                return "[]"
            }

            selectedChildren.forEach {
                if (it.id !in exportedIds) {
                    exportedIds.add(it.id)
                    peopleToExport.add(it)
                }
            }

            selectedChildren.forEach { child ->
                // Vérifier idParent1 (via conjointId ou logique parent)
                // Dans votre modèle Person, un parent est souvent vu comme un "conjoint" de l'autre parent
                // ou simplement une personne qui a cet enfant dans sa liste 'enfants'.

                // Approche la plus robuste avec votre modèle Person :
                // On cherche dans TOUTE la liste qui a cet enfant dans sa liste 'enfantIds' ou 'enfants'.
                val parents = allPeople.filter { p ->
                    p.enfantIds.contains(child.id) || p.enfants.any { e -> e.id == child.id }
                }

                parents.forEach { parent ->
                    if (parent.id !in exportedIds) {
                        exportedIds.add(parent.id)
                        peopleToExport.add(parent)

                        // Optionnel : Ajouter aussi l'autre parent (le conjoint) si on veut le couple complet
                        parent.conjoint?.let { conjoint ->
                            if (conjoint.id !in exportedIds) {
                                exportedIds.add(conjoint.id)
                                peopleToExport.add(conjoint)
                            }
                        }
                    }
                }
            }

            // 3. Générer le JSON pour ce groupe restreint
            // On réutilise la logique de création d'objet JSON de votre fonction export() existante
            buildJsonForPeople(peopleToExport.sortedBy { it.id })

        } catch (e: Exception) {
            Log.e(TAG, "Erreur export sélection enfants", e)
            "[]"
        }
    }

    /**
     * Fonction helper privée qui contient la logique de création du JSON.
     * Extraite de la fonction export() pour être réutilisée ici.
     */
    private fun buildJsonForPeople(people: List<Person>): String {
        val array = mutableListOf<JSONObject>()

        for (p in people) {
            val personJson = JSONObject()
            personJson.put("id", p.id)
            personJson.put("prenom", p.prenom)
            if (p.nom.isNotEmpty()) personJson.put("nom", p.nom)
            if (p.groupe.isNotEmpty()) personJson.put("groupe", p.groupe)
            if (p.dateNaissance != null) personJson.put("naissance", p.dateNaissance)

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
                relationsJson.put("enfants", JSONArray(p.enfants.map { it.id }))
                hasRelations = true
            }
            // Amis (inclus si présents, même si on n'a pas sélectionné les amis explicitement)
            if (p.amisIds.isNotEmpty()) {
                relationsJson.put("amis", JSONArray(p.amisIds))
                hasRelations = true
            } else if (p.amis.isNotEmpty()) {
                relationsJson.put("amis", JSONArray(p.amis.map { it.id }))
                hasRelations = true
            }

            if (hasRelations) {
                personJson.put("relations", relationsJson)
            }
            array.add(personJson)
        }

        return "[\n" + array.joinToString(",\n") { "  $it" } + "]"
    }
}

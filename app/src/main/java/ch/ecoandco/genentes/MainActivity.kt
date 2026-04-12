package ch.ecoandco.genentes // <--- IMPORTANT : Vérifiez que ceci correspond à votre vrai package

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.database.Cursor
import android.text.InputType
import android.util.Log
import android.view.LayoutInflater
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Calendar

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    // Déclaration des variables
    private lateinit var recyclerView: RecyclerView
    private lateinit var adaptateur: AnniversaireAdapter
    private lateinit var bdd: MaBaseDeDonnees

    // La liste qui va contenir nos objets formatés pour l'affichage
    private val listeEnfants = mutableListOf<LigneAnniversaire>()
    private var selectedTimestamp: Long = 0L

    private var colonneTri: String = "enfants"

    private lateinit var headerEnfant: TextView
    private lateinit var headerParents: TextView
    private lateinit var headerDate: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            super.onCreate(savedInstanceState)

            // 1. Charger le design XML
            setContentView(R.layout.activity_main)

            // 2. Initialiser la Base de Données
            // Cela va déclencher onCreate() dans MaBaseDeDonnees et insérer les données de test
            bdd = MaBaseDeDonnees(this)

            // 3. Récupérer les éléments du XML (RecyclerView)
            recyclerView = findViewById(R.id.recyclerViewAnniversaires)

            // Configurer le RecyclerView pour une liste verticale
            recyclerView.layoutManager = LinearLayoutManager(this)

            // 4. Charger les données depuis la BDD et remplir la liste
            chargerDonneesDepuisBDD()

            // 5. Créer et attacher l'Adapter
            // On passe la liste remplie à l'adapter
            adaptateur = AnniversaireAdapter(
                listeEnfants,
                        onSupprimer = { idEnfant ->
                    // C'est ici que vous avez accès à votre variable 'bdd' !
                    bdd.deleteLine(idEnfant)
                    chargerDonneesDepuisBDD()
                }
            )
            recyclerView.adapter = adaptateur

            val boutonAjouter = findViewById<Button>(R.id.boutonAjouter)

            boutonAjouter.setOnClickListener {
                // 1. Créer le contexte et l'inflateur pour la vue personnalisée
                val context = this

                // 2. Créer un Layout linéaire vertical dynamiquement (conteneur des champs)
                val layout = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(50, 40, 50, 10) // Marges internes
                }

                // 3. Créer les champs de saisie (EditText)
                val etPrenom = EditText(context).apply {
                    hint = "Prénom"
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
                }

                val etDate = EditText(context).apply {
                    hint = "\uD83D\uDDD3\uFE0F (date)"
                    isFocusable = false // Empêche le clavier de s'ouvrir, on veut le sélecteur de date
                    isClickable = true
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                }

                // 4. Gestion du clic sur le champ Date pour ouvrir le DatePicker
                etDate.setOnClickListener {
                    val calendar = Calendar.getInstance()
                    val year = calendar.get(Calendar.YEAR)
                    val month = calendar.get(Calendar.MONTH)
                    val day = calendar.get(Calendar.DAY_OF_MONTH)

                    DatePickerDialog(
                        context,
                        { _, selectedYear, selectedMonth, selectedDay ->
                            // 1. Calculer le timestamp
                            val tempCalendar = Calendar.getInstance().apply {
                                set(selectedYear, selectedMonth, selectedDay, 0, 0, 0)
                            }

                            // 2. LE STOCKER dans la variable membre de la classe
                            selectedTimestamp = tempCalendar.timeInMillis

                            // 3. Afficher la date lisible pour l'utilisateur (optionnel mais recommandé)
                            val formattedDate = "$selectedDay/${selectedMonth + 1}/$selectedYear"
                            etDate.setText(formattedDate)
                        },
                        year, month, day
                    ).show()
                }

                // 5. Ajouter les champs au layout
                layout.addView(etPrenom)
                layout.addView(etDate)

                // 6. Créer et afficher l'AlertDialog
                AlertDialog.Builder(context)
                    .setTitle("Ajouter un enfant")
                    .setView(layout)
                    .setPositiveButton("Enregistrer") { dialog, which ->
                        // Récupération des valeurs
                        val prenom = etPrenom.text.toString().trim()

                        // Validation simple
                        if (prenom.isNotEmpty() && selectedTimestamp > 0L) {
                            // Appel de votre fonction d'ajout (à adapter pour inclure la date)
                            val rowId = bdd.ajouterEnfant(prenom, selectedTimestamp)

                            if (rowId != -1L) {
                                ajouterParents(rowId)
                                selectedTimestamp = 0L
                                etDate.text.clear()
                            } else {
                                android.widget.Toast.makeText(context, "Erreur lors de l'ajout", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            android.widget.Toast.makeText(context, "Veuillez remplir tous les champs", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                    .setNegativeButton("Annuler", null)
                    .show()
            }
            headerEnfant = findViewById(R.id.TriEnfant)
            headerParents = findViewById(R.id.TriParent)
            headerDate = findViewById(R.id.TriDate)

            val fleche = getString(R.string.symbol_arrow_down) // Ou "▼" en dur si vous préférez
            headerEnfant.text = getString(R.string.label_enfant) + "$fleche"

            val btnEnfant = findViewById<TextView>(R.id.TriEnfant)
            btnEnfant.setOnClickListener {
                chargerDonneesDepuisBDD("enfant")
            }
            val btnParent = findViewById<TextView>(R.id.TriParent)
            btnParent.setOnClickListener{
                chargerDonneesDepuisBDD("parents")
            }
            val btnDate = findViewById<TextView>(R.id.TriDate)
            btnDate.setOnClickListener {
                chargerDonneesDepuisBDD("date")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Erreur dans onCreate", e)
            Toast.makeText(this, "Erreur: ${e.message}", Toast.LENGTH_LONG).show()
        }

    }

    override fun onResume() {
        super.onResume()
        chargerDonneesDepuisBDD()
    }

    private fun ajouterParents(idEnfant: Long) {
        try {
            val context = this
            val layout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(50, 40, 50, 10)
            }

            // Champs Parent 1 (Obligatoire)
            val etParent1 = EditText(context).apply {
                hint = "Parent 1"
                // Combine le type de texte classique avec l'option de majuscule sur chaque mot
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            }
            // Champs Parent 2 (Optionnel)
            val etParent2 = EditText(context).apply {
                hint = "Parent 2 (optionnel)"
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            }

            layout.addView(etParent1)
            layout.addView(etParent2)

            AlertDialog.Builder(context)
                .setTitle("Informations des parents")
                .setView(layout)
                .setPositiveButton("Enregistrer") { dialog, which ->
                    try {
                        val Parent1 = etParent1.text.toString().trim()

                        if (Parent1.isNotEmpty()) {
                            // 1. Insertion Parent 1
                            val idParent1 = bdd.ajouterParent(Parent1)

                            if (idParent1 == -1L) {
                                throw Exception("Échec insertion Parent 1 (Vérifiez la table 'Parents')")
                            }

                            // 2. Insertion Parent 2 (Optionnel)
                            val Parent2 = etParent2.text.toString().trim()
                            var idParent2: Long? = null

                            if (Parent2.isNotEmpty()) {
                                idParent2 = bdd.ajouterParent(Parent2)
                                if (idParent2 == -1L) {
                                    throw Exception("Échec insertion Parent 2")
                                }
                            }

                            // 3. Mise à jour de l'enfant
                            // Assurez-vous que cette fonction retourne bien un Booléen
                            val success = bdd.mettreAJourParentsEnfant(idEnfant, idParent1, idParent2)

                            if (success) {
                                Toast.makeText(context, "Parents enregistrés avec succès !", Toast.LENGTH_SHORT).show()
                                chargerDonneesDepuisBDD()
                            } else {
                                throw Exception("Échec mise à jour de l'enfant (Vérifiez les colonnes idParent1/2)")
                            }

                        } else {
                            Toast.makeText(context, "Le premier parent est obligatoire.", Toast.LENGTH_SHORT).show()
                        }

                    } catch (e: Exception) {
                        // C'EST ICI QUE VOUS VERREZ L'ERREUR SANS PLANTER
                        Log.e(TAG, "Erreur pendant l'enregistrement des parents", e)
                        android.widget.Toast.makeText(
                            context,
                            "Erreur : ${e.message}",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    }
                }
                .setNegativeButton("Passer", null)
                .show()
        } catch (e: Exception) {
            Log.e(TAG, "Erreur dans ajouterParents", e)
            Toast.makeText(this, "Erreur: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun chargerDonneesDepuisBDD(quelTri: String? = null) {
        try {
            // Vider la liste actuelle (au cas où on recharge)
            listeEnfants.clear()
            val colonneAUtiliser = quelTri ?: colonneTri

            val argumentTri = when (colonneAUtiliser) {
                "parents" -> "parents"
                "date"    -> "date" // Pas de COLLATE NOCASE nécessaire pour des dates (Long/Int)
                "enfants"      -> "enfants" // Valeur par défaut (enfants)
                else   -> "enfants"
            }

            // Exécuter la requête SQL (avec les JOIN)
            val curseur: Cursor = bdd.recupererTousLesEnfantsAvecParents(argumentTri)
            try {
                // Parcourir le curseur ligne par ligne (comme un while(fetch) en PHP)
                while (curseur.moveToNext()) {
                    // Récupération des colonnes par leur nom (défini dans le SQL avec AS)
                   val idEnfant = curseur.getInt(
                       curseur.getColumnIndexOrThrow("enfantId")
                   )

                    val prenomEnfant = curseur.getString(
                        curseur.getColumnIndexOrThrow("enfantPrenom")
                    )

                    val dateNaissance = curseur.getLong(
                        curseur.getColumnIndexOrThrow("dateNaissance")
                    )

                    // Gestion des parents (Parent 2 est optionnel)
                    val parent1 = curseur.getString(
                        curseur.getColumnIndexOrThrow("parent1")
                    )

                    // On récupère parent2, s'il est null dans la BDD, on met une chaîne vide
                    val indexParent2 = curseur.getColumnIndex("parent2")
                    val parent2 = if (!curseur.isNull(indexParent2)) {
                        curseur.getString(indexParent2)
                    } else {
                        ""
                    }

                    // Construction de la chaîne "Parents" pour l'affichage
                    val texteParents = if (parent2.isNotEmpty()) {
                        "$parent1 & $parent2"
                    } else {
                        parent1
                    }

                    // Création de l'objet data et ajout à la liste
                    listeEnfants.add(
                        LigneAnniversaire(
                            idEnfant = idEnfant,
                            prenomEnfant = prenomEnfant,
                            nomsParents = texteParents,
                            timestampNaissance = dateNaissance
                        )
                    )
                }
                mettreAJourIndicateursTri(argumentTri)
            }
            finally {
                // IMPORTANT : Toujours fermer le curseur pour libérer la mémoire
                curseur.close()
            }


            // Optionnel : Afficher un message si la liste est vide (débug)
            if (listeEnfants.isEmpty()) {
                Toast.makeText(this, "Aucun enfant trouvé dans la BDD", Toast.LENGTH_LONG).show()
            }

            // Rafraîchir l'affichage si l'adapter est déjà attaché.
            if (::adaptateur.isInitialized) {
                adaptateur.notifyDataSetChanged()
            }
        }
        catch(e : Exception)
        {
            Log.e(TAG, "Erreur dans chargerDonneesDepuisBDD", e)
            Toast.makeText(
                this,
                e.message ?: "Une erreur est survenue lors du chargement des données",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun mettreAJourIndicateursTri(colonneActive: String) {
        val fleche = getString(R.string.symbol_arrow_down) // Ou "▼" en dur si vous préférez

        colonneTri = colonneActive

        headerEnfant.text = getString(R.string.label_enfant)
        headerParents.text = getString(R.string.label_parents)
        headerDate.text = getString(R.string.label_date)
        // 1. Réinitialiser tous les headers sans flèche
        val texteAvecFleche = when (colonneActive) {
            "enfants" -> getString(R.string.label_enfant) + " $fleche"
            "parents" -> getString(R.string.label_parents) + " $fleche"
            "date"    -> getString(R.string.label_date) + " $fleche"
            else      -> ""
        }
        when (colonneActive) {
            "enfants" -> headerEnfant.text = texteAvecFleche
            "parents" -> headerParents.text = texteAvecFleche
            "date"    -> headerDate.text = texteAvecFleche
        }
    }
}
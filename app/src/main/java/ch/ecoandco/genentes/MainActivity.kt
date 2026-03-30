package ch.ecoandco.genentes // <--- IMPORTANT : Vérifiez que ceci correspond à votre vrai package

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.database.Cursor
import android.view.LayoutInflater
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import java.util.Calendar

class MainActivity : AppCompatActivity() {

    // Déclaration des variables
    private lateinit var recyclerView: RecyclerView
    private lateinit var adaptateur: AnniversaireAdapter
    private lateinit var bdd: MaBaseDeDonnees

    // La liste qui va contenir nos objets formatés pour l'affichage
    private val listeEnfants = mutableListOf<LigneAnniversaire>()
    private var selectedTimestamp: Long = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
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
        adaptateur = AnniversaireAdapter(listeEnfants)
        recyclerView.adapter = adaptateur

        val boutonAjouter = findViewById<Button>(R.id.boutonAjouter)

        boutonAjouter.setOnClickListener {
            // 1. Créer le contexte et l'inflateur pour la vue personnalisée
            val context = this
            val inflater = LayoutInflater.from(context)

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
            }

            val etNom = EditText(context).apply {
                hint = "Nom"
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            val etDate = EditText(context).apply {
                hint = "Date de naissance (JJ/MM/AAAA)"
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
            layout.addView(etNom)
            layout.addView(etDate)

            // 6. Créer et afficher l'AlertDialog
            AlertDialog.Builder(context)
                .setTitle("Ajouter un enfant")
                .setView(layout)
                .setPositiveButton("Enregistrer") { dialog, which ->
                    // Récupération des valeurs
                    val prenom = etPrenom.text.toString().trim()
                    val dateNaissance = selectedTimestamp

                    // Validation simple
                    if (prenom.isNotEmpty() &&  selectedTimestamp > 0L) {
                        // Appel de votre fonction d'ajout (à adapter pour inclure la date)
                        val rowId = bdd.ajouterEnfant(prenom, selectedTimestamp)

                        if (rowId != -1L) {
                            ajouterParents(rowId)
                            android.widget.Toast.makeText(context, "Enfant ajouté avec succès !", android.widget.Toast.LENGTH_SHORT).show()
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

    }

    private fun ajouterParents(idEnfant: Long) {
        val context = this
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 40, 50, 10)
        }

        // Champs Parent 1 (Obligatoire)
        val etParent1Prenom = EditText(context).apply { hint = "Prénom Parent 1" }
        val etParent1Nom = EditText(context).apply { hint = "Nom Parent 1" }

        // Champs Parent 2 (Optionnel)
        val etParent2Prenom = EditText(context).apply { hint = "Prénom Parent 2 (optionnel)" }
        val etParent2Nom = EditText(context).apply { hint = "Nom Parent 2 (optionnel)" }

        layout.addView(etParent1Prenom)
        layout.addView(etParent1Nom)
        layout.addView(etParent2Prenom)
        layout.addView(etParent2Nom)

        AlertDialog.Builder(context)
            .setTitle("Informations des parents")
            .setView(layout)
            .setPositiveButton("Enregistrer") { dialog, which ->
                val p1Prenom = etParent1Prenom.text.toString().trim()
                val p1Nom = etParent1Nom.text.toString().trim()

                if (p1Prenom.isNotEmpty() && p1Nom.isNotEmpty()) {
                    // 1. Enregistrer Parent 1 et récupérer son ID
                    val idParent1 = bdd.ajouterParent(p1Prenom, p1Nom)

                    // 2. Enregistrer Parent 2 (si rempli) et récupérer son ID
                    val p2Prenom = etParent2Prenom.text.toString().trim()
                    val p2Nom = etParent2Nom.text.toString().trim()
                    var idParent2: Long? = null

                    if (p2Prenom.isNotEmpty() || p2Nom.isNotEmpty()) {
                        idParent2 = bdd.ajouterParent(p2Prenom, p2Nom)
                    }

                    // 3. METTRE À JOUR l'enfant avec les IDs des parents
                    val success = bdd.mettreAJourParentsEnfant(idEnfant, idParent1, idParent2)

                    if (success) {
                        Toast.makeText(context, "Enfant et parents enregistrés !", Toast.LENGTH_SHORT).show()
                        // Optionnel : rafraîchir la liste
                    } else {
                        Toast.makeText(context, "Erreur lors de la liaison", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(context, "Le premier parent est obligatoire", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Passer", null)
            .show()
    }

    private fun chargerDonneesDepuisBDD() {
        // Vider la liste actuelle (au cas où on recharge)
        listeEnfants.clear()

        // Exécuter la requête SQL (avec les JOIN)
        val curseur: Cursor = bdd.recupererTousLesEnfantsAvecParents()

        // Parcourir le curseur ligne par ligne (comme un while(fetch) en PHP)
        while (curseur.moveToNext()) {
            // Récupération des colonnes par leur nom (défini dans le SQL avec AS)
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
                    prenomEnfant = prenomEnfant,
                    nomsParents = texteParents,
                    timestampNaissance = dateNaissance
                )
            )
        }

        // IMPORTANT : Toujours fermer le curseur pour libérer la mémoire
        curseur.close()

        // Optionnel : Afficher un message si la liste est vide (débug)
        if (listeEnfants.isEmpty()) {
            Toast.makeText(this, "Aucun enfant trouvé dans la BDD", Toast.LENGTH_LONG).show()
        }
    }
}
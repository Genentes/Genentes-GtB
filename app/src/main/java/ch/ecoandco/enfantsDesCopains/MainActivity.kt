package ch.ecoandco.enfantsDesCopains // <--- IMPORTANT : Vérifiez que ceci correspond à votre vrai package

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
import android.view.Menu
import android.view.MenuItem
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.ActionBar
import androidx.activity.result.contract.ActivityResultContracts
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.TimeZone
import java.util.Locale

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

    private var colonneTri: String = "date"

    private var groupeActive: String = "copains"

    private lateinit var titreCentre: TextView

    private lateinit var headerEnfant: TextView
    private lateinit var headerParents: TextView
    private lateinit var headerDate: TextView

    private lateinit var groupeCopains: TextView
    private lateinit var groupeFamille: TextView
    private lateinit var groupeTravail: TextView
    private lateinit var groupeAutre: TextView

    // File picker launcher for import
    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            // 1. Affichage de la boîte de dialogue de confirmation
            AlertDialog.Builder(this)
                .setTitle("Attention : Remplacement des données")
                .setMessage("L'importation de ce fichier va effacer intégralement votre base de données actuelle. Cette action est irréversible. Voulez-vous vraiment continuer ?")
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setPositiveButton("Oui, importer (Effacer tout)") { _, _ ->
                    // 2. Exécution seulement si l'utilisateur clique sur "Oui"
                    effectuerImport(uri)
                }
                .setNegativeButton("Annuler", null) // Le 'null' ferme simplement la boîte sans action
                .show()
        }
    }

    // Fonction helper pour isoler la logique d'import (plus propre)
    private fun effectuerImport(uri: android.net.Uri) {
        try {
            val inputStream = contentResolver.openInputStream(uri)
            val jsonContent = inputStream?.bufferedReader().use { it?.readText() }

            if (jsonContent != null) {
                // Appel à votre fonction qui vide et remplit la BDD
                if (bdd.importFromJson(jsonContent)) {
                    chargerDonneesDepuisBDD()
                    Toast.makeText(this, "Données importées avec succès", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, "Erreur lors de l'import", Toast.LENGTH_LONG).show()
                }
            } else {
                Toast.makeText(this, "Impossible de lire le fichier", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors de la lecture du fichier", e)
            Toast.makeText(this, "Erreur: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    // File saver launcher for export
    private val fileSaverLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            try {
                val timeStamp = SimpleDateFormat("yyyy_MM_dd_HHmmss", Locale.getDefault()).format(Calendar.getInstance().time)
                val fileName = "anniversaires_export_$timeStamp.json"
                
                // Get data as JSON string
                val jsonContent = bdd.exportToJson() // This should return the JSON string directly
                
                // Write to the chosen URI
                contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(jsonContent.toByteArray())
                }
                Toast.makeText(this, "Fichier sauvegardé avec succès", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.e(TAG, "Erreur lors de la sauvegarde", e)
                Toast.makeText(this, "Erreur: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            super.onCreate(savedInstanceState)

            // 1. Charger le design XML
            setContentView(R.layout.activity_main)

            val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.maToolbar)

            // 2. La définir comme barre d'action de l'activité
            // C'est cette ligne qui permet au menuInflater de fonctionner !
            setSupportActionBar(toolbar)
            supportActionBar?.displayOptions = ActionBar.DISPLAY_SHOW_CUSTOM
            supportActionBar?.setDisplayShowTitleEnabled(false)

            // 2. Récupérer le TextView personnalisé et lui donner le texte
            titreCentre = findViewById(R.id.titreCentre)
            titreCentre.text = getString(R.string.main_app_title)

            headerEnfant = findViewById(R.id.TriEnfant)
            headerParents = findViewById(R.id.TriParent)
            headerDate = findViewById(R.id.TriDate)

            groupeCopains = findViewById(R.id.boutonGroupeCopains)
            groupeFamille = findViewById(R.id.boutonGroupeFamille)
            groupeTravail = findViewById(R.id.boutonGroupeTravail)
            groupeAutre = findViewById(R.id.boutonGroupeAutre)

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
                    val calendar = Calendar.getInstance(TimeZone.getTimeZone("Europe/Zurich"))
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
                                Toast.makeText(context, "Erreur lors de l'ajout", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Toast.makeText(context, "Veuillez remplir tous les champs", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .setNegativeButton("Annuler", null)
                    .show()
            }

            val fleche = getString(R.string.symbol_arrow_down) // Ou "▼" en dur si vous préférez
            headerEnfant.text = getString(R.string.label_enfant) + "$fleche"

            headerEnfant.setOnClickListener {
                chargerDonneesDepuisBDD("enfant")
            }
            headerParents.setOnClickListener{
                chargerDonneesDepuisBDD("parents")
            }
            headerDate.setOnClickListener {
                chargerDonneesDepuisBDD("date")
            }

            groupeCopains.setOnClickListener {
                chargerDonneesDepuisBDD(argumentGroupe = null)
            }
            groupeFamille.setOnClickListener {
                chargerDonneesDepuisBDD(argumentGroupe = "famille")
            }
            groupeTravail.setOnClickListener {
                chargerDonneesDepuisBDD(argumentGroupe = "travail")
            }
            groupeAutre.setOnClickListener {
                chargerDonneesDepuisBDD(argumentGroupe = "autre")
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

    // 1. Gonfler le menu
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    // 2. Gérer le clic sur les éléments
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_export -> {
                lancerExportation()
            }
            R.id.action_import -> {
                lancerImportation()
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun lancerExportation() : Boolean {
        try {
            val timeStamp = SimpleDateFormat("yyyy_MM_dd_HHmmss", Locale.getDefault()).format(Calendar.getInstance().time)
            val fileName = "anniversaires_export_$timeStamp.json"
            
            // Launch file saver to let user choose location
            fileSaverLauncher.launch(fileName)
        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors de l'exportation", e)
            Toast.makeText(this, "Erreur: ${e.message}", Toast.LENGTH_SHORT).show()
        }
        return true
    }

    private fun lancerImportation() : Boolean {
        try {
            filePickerLauncher.launch("application/json")
        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors de l'import", e)
            Toast.makeText(this, "Erreur: ${e.message}", Toast.LENGTH_SHORT).show()
        }
        return true
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

            //Définir la catégorie
            // --- 1. Création du Titre (TextView) ---
            val labelCategorie = TextView(context).apply {
                text = "Ajouté à"
                // Optionnel : Mise en forme pour ressembler à un titre de champ
                textSize = 14f
                setTypeface(null, android.graphics.Typeface.BOLD) // Mettre en gras
                setPadding(0, 40, 0, 8) // Marge haut (40), Bas (8) pour coller un peu au spinner
                // Si votre app supporte les thèmes sombres/clair, évitez de coder la couleur en dur,
                // sinon vous pouvez ajouter: setTextColor(Color.BLACK) ou une ressource de couleur
            }

// --- 2. Création du Spinner (Votre code existant) ---
            val spinnerCategorie = Spinner(context).apply {
                // 1. Définir les options disponibles
                val categories = arrayOf("copains", "famille", "travail", "autre")

                // 2. Créer l'adaptateur pour afficher la liste (layout simple natif Android)
                val adapter = ArrayAdapter(
                    context,
                    android.R.layout.simple_spinner_item, // Layout pour l'élément sélectionné
                    categories
                )

                // 3. Définir le layout pour la liste déroulante (quand on clique)
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)

                // 4. Attacher l'adaptateur au Spinner
                this.adapter = adapter

                // Optionnel : Sélectionner "copains" par défaut (index 0)
                setSelection(0)
            }

            layout.addView(etParent1)
            layout.addView(etParent2)
                layout.addView(labelCategorie)      // Ajout du titre en premier
                layout.addView(spinnerCategorie)    // Ajout du spinner juste après


            AlertDialog.Builder(context)
                .setTitle("Informations des parents")
                .setView(layout)
                .setPositiveButton("Enregistrer") { dialog, which ->
                    try {
                        val Parent1 = etParent1.text.toString().trim()

                        if (Parent1.isNotEmpty()) {

                            val CategorieSelectionnee = spinnerCategorie.selectedItem.toString().trim()

                            // 1. Insertion Parent 1
                            val idParent1 = bdd.ajouterParent(nomComplet = Parent1, groupe = CategorieSelectionnee)

                            if (idParent1 == -1L) {
                                throw Exception("Échec insertion Parent 1 (Vérifiez la table 'Parents')")
                            }


                            // 2. Insertion Parent 2 (Optionnel)
                            val Parent2 = etParent2.text.toString().trim()
                            var idParent2: Long? = null

                            if (Parent2.isNotEmpty()) {
                                idParent2 = bdd.ajouterParent(nomComplet =Parent2, groupe = CategorieSelectionnee)
                                if (idParent2 == -1L) {
                                    throw Exception("Échec insertion Parent 2")
                                }
                            }

                            // 3. Mise à jour de l'enfant
                            // Assurez-vous que cette fonction retourne bien un Booléen
                            val success = bdd.mettreAJourParentsEnfant(idEnfant, idParent1, idParent2)

                            if (success) {
                                val message = "Parents enregistrés avec succès - ${CategorieSelectionnee}."
                                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
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

    private fun chargerDonneesDepuisBDD(quelTri: String? = null, argumentGroupe: String? = "null") {
        try {
            // Vider la liste actuelle (au cas où on recharge)
            listeEnfants.clear()
            val colonneAUtiliser = quelTri ?: colonneTri

            val argumentTri = when (colonneAUtiliser) {
                "parents" -> "parents"
                "date"    -> "date" // Pas de COLLATE NOCASE nécessaire pour des dates (Long/Int)
                "enfant"      -> "enfants"
                else   -> "date"
            }

            // Exécuter la requête SQL (avec les JOIN)
            val curseur: Cursor = bdd.recupererTousLesEnfantsAvecParents(argumentTri, argumentGroupe)
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
                Toast.makeText(this, "Personne en vue \uD83D\uDD2D", Toast.LENGTH_SHORT).show()
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
        recyclerView.layoutManager?.scrollToPosition(0)
    }

    override fun onDestroy() {
        super.onDestroy()
        // C'est le seul endroit où on ferme la connexion globale
        if (::bdd.isInitialized) {
            bdd.close()
        }
    }
}
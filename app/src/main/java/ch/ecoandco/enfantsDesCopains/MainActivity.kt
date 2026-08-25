package ch.ecoandco.enfantsDesCopains // --- IMPORTANT : Vérifiez que ceci correspond à votre vrai package

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.text.InputType
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
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
import java.util.Date
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
    private var estTriAscendant: Boolean = true     // true = Ascendant, false = Descendant

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
            AlertDialog.Builder(this)
                .setTitle("Attention : Remplacement des données")
                .setMessage("L'importation de ce fichier va effacer intégralement votre base de données actuelle. Cette action est irréversible. Voulez-vous vraiment continuer ?")
                .setPositiveButton("Oui, importer (Effacer tout)") { _, _ ->
                    effectuerImport(uri)
                }
                .setNegativeButton("Annuler", null)
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
                    afficherToastPersonnalise("Données importées avec succès")
                } else {
                    afficherToastPersonnalise("Erreur lors de l'import")
                }
            } else {
                afficherToastPersonnalise("Impossible de lire le fichier")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors de la lecture du fichier", e)
            afficherToastPersonnalise("Erreur: ${e.message}")
        }
    }

    private var jsonEnAttenteEcriture: String? = null
    private val fileSaverLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            // On récupère le JSON préparé précédemment
            val jsonContent = jsonEnAttenteEcriture

            if (!jsonContent.isNullOrEmpty()) {
                try {
                    // Écriture du fichier
                    contentResolver.openOutputStream(uri)?.use { outputStream ->
                        outputStream.write(jsonContent.toByteArray())
                    }
                    afficherToastPersonnalise("Fichier sauvegardé avec succès")
                } catch (e: Exception) {
                    Log.e(TAG, "Erreur lors de la sauvegarde", e)
                    afficherToastPersonnalise("Erreur: ${e.message}")
                } finally {
                    // Nettoyage : on efface la variable après usage
                    jsonEnAttenteEcriture = null
                }
            } else {
                afficherToastPersonnalise("Erreur: Aucune donnée à exporter.")
            }
        }
    }

    private fun afficherToastPersonnalise(message: String) {
        // Inflation de la vue sans l'attacher à un parent (null est correct ici)
        // On utilise directement la vue inflatée dans le constructeur du Toast
        val layout = LayoutInflater.from(this).inflate(R.layout.custom_toast, null)

        // Configuration du texte
        layout.findViewById<TextView>(R.id.toast_text).text = message

        // Construction du Toast avec la vue directement
        Toast(this).apply {
            view = layout // Utilisation de la propriété 'view' au lieu de la méthode dépréciée 'setView'
            duration = Toast.LENGTH_SHORT
            setGravity(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, 0, 100)
            show()
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

            // Initialisation du RecyclerView
            recyclerView = findViewById(R.id.recyclerViewAnniversaires) // Vérifie que l'ID correspond à ton XML
            recyclerView.layoutManager = LinearLayoutManager(this)

// Création de l'adapter avec la référence dynamique à la liste
            adaptateur = AnniversaireAdapter(
                getListeDonnees = { listeEnfants },
                onSupprimer = { idEnfant ->
                    // 1. On cherche la position de l'ID dans la liste actuelle
                    val position = listeEnfants.indexOfFirst { it.idEnfant == idEnfant }

                    // 2. On agit UNIQUEMENT si l'ID a été trouvé (position != -1).
                    if (position != -1) {
                        // A. Suppression dans la base de données
                        bdd.deleteLine(idEnfant)

                        // B. Suppression dans la liste en mémoire (cohérence immédiate)
                        listeEnfants.removeAt(position)

                        // C. Notification précise à l'adaptateur (Animation fluide)
                        adaptateur.notifyItemRemoved(position)

                        // Optionnel : Pour animer le glissement des éléments restants vers le haut
                        adaptateur.notifyItemRangeChanged(position, listeEnfants.size)
                    } else {
                        afficherToastPersonnalise("Déjà supprimé")
                    }
                }
            )
// Lien entre l'adapter et le RecyclerView
            recyclerView.adapter = adaptateur

// Optionnel : Écouter les changements de sélection pour mettre à jour un compteur
            adaptateur.onSelectionChanged = { nombre ->
                mettreAJourTitreSelection(nombre)
            }
// Lancement du premier chargement
            chargerDonneesDepuisBDD()

            val boutonAjouter = findViewById<Button>(R.id.boutonAjouter)

            boutonAjouter.setOnClickListener {
                // 1. Créer le contexte pour la vue personnalisée
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

                            // 3. Afficher la date lisible pour l'utilisateur (optionnel, mais recommandé)
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
                    .setPositiveButton("Enregistrer") { _, _ ->
                        // Récupération des valeurs
                        val prenom = etPrenom.text.toString().trim()

                        if (prenom.isNotEmpty() && selectedTimestamp != 0L) {
                            // Appel de votre fonction d'ajout (à adapter pour inclure la date)
                            val rowId = bdd.ajouterEnfant(prenom, selectedTimestamp)

                            if (rowId != -1L) {
                               ajouterParents(rowId)
                                selectedTimestamp = 0L
                                etDate.text.clear()
                            } else {
                                afficherToastPersonnalise("Erreur lors de l'ajout")
                            }
                        } else {
                            afficherToastPersonnalise("Veuillez remplir tous les champs")
                        }
                    }
                    .setNegativeButton("Annuler", null)
                    .show()
            }

            headerEnfant.text = getString(R.string.label_enfantArrow)

            headerEnfant.setOnClickListener {
                val nouvelleColonne = "enfant"
                if (nouvelleColonne == colonneTri) {
                    estTriAscendant = !estTriAscendant
                } else {
                    colonneTri = nouvelleColonne
                    estTriAscendant = true
                }
                mettreAJourIndicateursTri(colonneTri, estTriAscendant)
                trierEtAfficher("enfant", groupeActive)
            }
            headerParents.setOnClickListener{
                val nouvelleColonne = "parents"
                if (nouvelleColonne == colonneTri) {
                    estTriAscendant = !estTriAscendant
                } else {
                    colonneTri = nouvelleColonne
                    estTriAscendant = true
                }
                mettreAJourIndicateursTri(colonneTri, estTriAscendant)
                trierEtAfficher("parents", groupeActive)
            }
            headerDate.setOnClickListener {
                val nouvelleColonne = "date"
                if (nouvelleColonne == colonneTri) {
                    estTriAscendant = !estTriAscendant
                } else {
                    colonneTri = nouvelleColonne
                    estTriAscendant = true
                }
                mettreAJourIndicateursTri(colonneTri, estTriAscendant)
                trierEtAfficher("date", groupeActive)
            }

            groupeCopains.setOnClickListener {
                chargerDonneesDepuisBDD(colonneTri, "copains")
            }
            groupeFamille.setOnClickListener {
                chargerDonneesDepuisBDD(colonneTri, "famille")
            }
            groupeTravail.setOnClickListener {
                chargerDonneesDepuisBDD(colonneTri, "travail")
            }
            groupeAutre.setOnClickListener {
                chargerDonneesDepuisBDD(colonneTri, "autre")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erreur dans onCreate", e)
            afficherToastPersonnalise("Erreur: ${e.message}")
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

    // Fonction appelée par ton bouton "Sélectionner" (à créer dans ton menu ou layout)
    private fun lancerModeSelection() : Boolean {
        adaptateur.activerModeSelection()
        afficherBarreActionSelection(true)
        return true
    }

    // Affiche ou cache la barre avec les boutons "Annuler" et "Exporter"
    private fun afficherBarreActionSelection(afficher: Boolean) {
        val layoutSelection = findViewById<View>(R.id.layoutSelection)
        layoutSelection.visibility = if (afficher) View.VISIBLE else View.GONE

        val layoutBouton = findViewById<View>(R.id.boutonAjouterContainer)
        layoutBouton.visibility = if (afficher) View.GONE else View.VISIBLE

        if (afficher) {
            // Bouton Annuler
            findViewById<Button>(R.id.btnAnnulerSelection).setOnClickListener {
                adaptateur.desactiverModeSelection()
                afficherBarreActionSelection(false)
            }

            // Bouton Exporter
            findViewById<Button>(R.id.btnExporterSelection).setOnClickListener {
                val ids = adaptateur.getSelectedIds()
                if (ids.isEmpty()) {
                    afficherToastPersonnalise("Aucune donnée sélectionnée")
                } else {
                    exporterSelection(ids)
                    adaptateur.desactiverModeSelection()
                    afficherBarreActionSelection(false)
                }
            }
            mettreAJourTitreSelection(0)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_export -> {
                lancerExportation()
            }
            R.id.action_import -> {
                lancerImportation()
            }
            R.id.action_change_category -> {
                lancerModeSelection()
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun lancerExportation(): Boolean {
        try {
            // 1. Récupérer toutes les personnes depuis la BDD
            val toutesLesPersonnes = bdd.chargerToutesLesPersonnes()

            if (toutesLesPersonnes.isEmpty()) {
                afficherToastPersonnalise("La base de données est vide.")
                return false
            }

            // 2. Pour un export complet, on prend une personne "racine" (ex : la première)
            // La fonction export() de DataParser se chargera de trouver tous les liens récursifs.
            val personneRacine = toutesLesPersonnes.first()

            // 3. Générer le JSON complet
            val jsonContent = DataParser().export(personneRacine)

            if (jsonContent.isEmpty()) {
                afficherToastPersonnalise("Erreur lors de la génération du JSON.")
                return false
            }

            // 4. Stocker dans la variable tampon
            jsonEnAttenteEcriture = jsonContent

            // 5. Préparer le nom de fichier et lancer la boîte de dialogue
            val timeStamp = SimpleDateFormat("yyyy_MM_dd_HHmmss", Locale.getDefault())
                .format(Calendar.getInstance().time)
            val fileName = "anniversaires_export_complet_$timeStamp.json"

            fileSaverLauncher.launch(fileName)

            return true
        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors de l'exportation", e)
            afficherToastPersonnalise("Erreur: ${e.message}")
            return false
        }
    }


    private fun lancerImportation() : Boolean {
        try {
            filePickerLauncher.launch("application/json")
        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors de l'import", e)
            afficherToastPersonnalise("Erreur: ${e.message}")
        }
        return true
    }


    /**
     * Lance l'exportation pour une liste spécifique d'IDs d'enfants.
     * Inclut automatiquement les parents trouvés dans la base.
     *
     * @param idsEnfantsSelectionnes La liste des IDs des enfants à exporter (ex : listOf(1, 5, 8))
     */
    private fun lancerExportationSelection(idsEnfantsSelectionnes: List<Int>) {
        try {
            // 1. Récupérer toutes les personnes (nécessaire pour retrouver les parents par correspondance)
            val toutesLesPersonnes = bdd.chargerToutesLesPersonnes()

            if (toutesLesPersonnes.isEmpty()) {
                afficherToastPersonnalise("La base de données est vide.")
                return
            }

            // 2. Appeler la NOUVELLE fonction de DataParser créée précédemment
            val dataParser = DataParser()
            val jsonTest = dataParser.exportChildrenWithParents(toutesLesPersonnes, idsEnfantsSelectionnes)

            if (jsonTest.isEmpty() || jsonTest == "[]") {
                afficherToastPersonnalise("Aucune donnée trouvée pour cette sélection.")
                return
            }

            val context = this // Adaptez si nécessaire (requireContext())

            // 1. Créer le conteneur pour les boutons personnalisés
            val containerLayout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(64, 48, 64, 24) // Padding gauche/droite plus large pour centrer visuellement
            }

            // 2. Fonction locale pour créer un bouton stylisé
            fun ajouterBoutonAction(texte: String, action: () -> Unit) {
                val button = Button(context).apply {
                    text = texte
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        topMargin = 24 // Espace entre les boutons
                        bottomMargin = 0
                    }
                    textSize = 16f
                    // Optionnel : Mettre en gras
                    setTypeface(null, android.graphics.Typeface.BOLD)

                    setOnClickListener {
                        action()
                        // Le dialog se fermera automatiquement, car on ne définit pas de comportement de maintien
                    }
                }
                containerLayout.addView(button)
            }

            // 3. Ajouter les deux options principales
            ajouterBoutonAction("Partager la sélection (fichier JSON)") {
                preparerEtLancerExportFichier(idsEnfantsSelectionnes, toutesLesPersonnes)
            }

            ajouterBoutonAction("-> Changer de catégorie") {
                lancerChangementCategorie(idsEnfantsSelectionnes)
            }

            // 4. Construire l'AlertDialog
            AlertDialog.Builder(context)
                .setTitle("Action pour la sélection")
                .setMessage("Que souhaitez-vous faire des éléments sélectionnés ?")
                .setView(containerLayout) // < C'est ici qu'on insère nos boutons personnalisés
                .setNegativeButton("Annuler") { dialog, _ ->
                    dialog.dismiss()
                }
                .show()

        } catch (e: Exception) {
            Log.e(TAG, "Erreur préparation sélection", e)
            afficherToastPersonnalise("Erreur: ${e.message}")
        }
    }


    /**
     * Contient l'ancienne logique d'exportation vers fichier.
     * Elle reçoit les données déjà validées pour éviter de les recharger.
     */
    private fun preparerEtLancerExportFichier(
        idsEnfantsSelectionnes: List<Int>,
        toutesLesPersonnes: List<Person> // Adaptez le type 'Personne' selon votre modèle réel
    ) {
        try {
            val dataParser = DataParser()
            val jsonContent = dataParser.exportChildrenWithParents(toutesLesPersonnes, idsEnfantsSelectionnes)

            // Stocker dans la variable tampon
            jsonEnAttenteEcriture = jsonContent

            // Préparer le nom de fichier et lancer la boîte de dialogue
            val timeStamp = SimpleDateFormat("yyyy_MM_dd_HHmmss", Locale.getDefault())
                .format(Calendar.getInstance().time)
            val fileName = "anniversaires_selection_$timeStamp.json"

            fileSaverLauncher.launch(fileName)

        } catch (e: Exception) {
            Log.e(TAG, "Erreur écriture fichier", e)
            afficherToastPersonnalise("Erreur lors de l'export: ${e.message}")
        }
    }

    private fun lancerChangementCategorie(idsEnfantsSelectionnes: List<Int>) {
        // TODO: Implémenter ici la logique pour :
        // 1. Demander à l'utilisateur quelle catégorie cible choisir (autre AlertDialog ?)
        // 2. Mettre à jour la BDD pour ces IDs
        // 3. Rafraîchir l'affichage

        afficherToastPersonnalise("Fonctionnalité 'Changer catégorie' à implémenter pour : $idsEnfantsSelectionnes")

        // Exemple de structure future :
        // afficherSelectionCategorie { categorieCible >
        //     bdd.mettreAJourCategorie(idsEnfantsSelectionnes, categorieCible)
        //     rafraichirListe()
        // }
    }


    // Met à jour le texte "X élément(s) sélectionné(s)"
    private fun mettreAJourTitreSelection(count: Int) {
        val textView = findViewById<TextView>(R.id.textTitreSelection)
        textView.text = getString(R.string.message_nombre_selection, count)
    }

    // Fonction squelette pour l'export
    private fun exporterSelection(ids: Set<Int>) {
        afficherToastPersonnalise("Export des éléments : $ids")

        // Conversion simple de Set en List
        val listeIds: List<Int> = ids.toList()

        // On passe directement la liste
        lancerExportationSelection(listeIds)
    }

    @SuppressLint("SetTextI18n")
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
                text = getString(R.string.label_choixCategorie)
                // Optionnel : Mise en forme pour ressembler à un titre de champ
                textSize = 14f
                setTypeface(null, android.graphics.Typeface.BOLD) // Mettre en gras
                setPadding(0, 40, 0, 8) // Marge haut (40), Bas (8) pour coller un peu au spinner
                }

// --- 2. Création du Spinner (Votre code existant) ---
            val spinnerCategorie = Spinner(context).apply {
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
                val positionCatActuelle = when (groupeActive) {
                    "famille" -> 1
                    "travail" -> 2
                    "autre" -> 3
                    else -> 0
                }
                setSelection(positionCatActuelle)
            }
                layout.addView(labelCategorie)      // Ajout du titre en premier
                layout.addView(spinnerCategorie)    // Ajout du spinner juste après
            layout.addView(etParent1)
            layout.addView(etParent2)

            AlertDialog.Builder(context)
                .setTitle("Informations des parents")
                .setView(layout)
                .setPositiveButton("Enregistrer") { _, _ ->
                    try {
                        val parent1 = etParent1.text.toString().trim()

                        if (parent1.isNotEmpty()) {

                            val categorieSelectionnee = spinnerCategorie.selectedItem.toString().trim()

                            // 1. Insertion Parent 1
                            val idParent1 = bdd.ajouterParent(nomComplet = parent1, groupe = categorieSelectionnee)

                            if (idParent1 == -1L) {
                                throw Exception("Échec insertion Parent 1 (Vérifiez la table 'Parents')")
                            }


                            // 2. Insertion Parent 2 (Optionnel)
                            val parent2 = etParent2.text.toString().trim()
                            var idParent2: Long? = null

                            if (parent2.isNotEmpty()) {
                                idParent2 = bdd.ajouterParent(nomComplet =parent2, groupe = categorieSelectionnee)
                                if (idParent2 == -1L) {
                                    throw Exception("Échec insertion Parent 2")
                                }
                            }

                            // 3. Mise à jour de l'enfant
                            // Assurez-vous que cette fonction retourne bien un Booléen
                            val success = bdd.mettreAJourParentsEnfant(idEnfant, idParent1, idParent2)

                            if (success) {
                                val message = "Parents enregistrés avec succès - ${categorieSelectionnee}."
                                afficherToastPersonnalise(message)
                                chargerDonneesDepuisBDD()
                            } else {
                                throw Exception("Échec mise à jour de l'enfant (Vérifiez les colonnes idParent1/2)")
                            }

                        } else {
                            afficherToastPersonnalise("Le premier parent est obligatoire. ")
                        }

                    } catch (e: Exception) {
                        Log.e(TAG, "Erreur pendant l'enregistrement des parents", e)
                        afficherToastPersonnalise("Erreur : ${e.message}")
                    }
                }
                .setNegativeButton("Passer", null)
                .show()
        } catch (e: Exception) {
            Log.e(TAG, "Erreur dans ajouterParents", e)
            afficherToastPersonnalise("Erreur: ${e.message}")
        }
    }

    private fun chargerDonneesDepuisBDD(quelTri: String? = null, argumentGroupe: String? = null) {
        try {
            // Vider la liste actuelle (au cas où on recharge)
            val colonneAUtiliser = quelTri ?: colonneTri
            val groupeAUtiliser = argumentGroupe ?: groupeActive

            // 1. Sauvegarder l'ancienne taille AVANT de vider
            val ancienneTaille = listeEnfants.size

            // 2. Notifier la suppression des anciens éléments (si la liste n'était pas vide)
            // Cela dit au RecyclerView : "Enlève les X premières lignes de l'écran"
            if (::adaptateur.isInitialized && ancienneTaille > 0) {
                adaptateur.notifyItemRangeRemoved(0, ancienneTaille)
            }

            // 3. Vider la liste (maintenant que l'adaptateur est synchronisé)
            listeEnfants.clear()

            val argumentTri = when (colonneAUtiliser) {
                "parents" -> "parents"
                "date"    -> "date" // Pas de COLLATE NOCASE nécessaire pour des dates (Long/Int)
                "enfant"      -> "enfants"
                else   -> "date"
            }

            // Exécuter la requête SQL (avec les JOIN)
            bdd.recupererTousLesEnfantsAvecParents(argumentTri, groupeAUtiliser).use { curseur ->
                // Parcourir le curseur ligne par ligne
                while (curseur.moveToNext()) {
                    // Récupération des colonnes par leur nom (défini dans le SQL avec AS)
                    val idEnfant = curseur.getInt(
                        curseur.getColumnIndexOrThrow("enfantId")
                    )

                    val prenomEnfant = curseur.getString(
                        curseur.getColumnIndexOrThrow("enfantPrenom")
                    )

                    val groupeCategorie = curseur.getString(
                        curseur.getColumnIndexOrThrow("groupeCategorie")
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
                    val parent2 = if (indexParent2 != -1 && !curseur.isNull(indexParent2)) {
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
                            groupe = groupeCategorie,
                            timestampNaissance = dateNaissance
                        )
                    )
                }

                if (argumentGroupe != null && argumentGroupe != "null") {
                    this.groupeActive = argumentGroupe
                }
                mettreAJourIndicateursTri(colonneAUtiliser, estTriAscendant)
            }
            // Le curseur est automatiquement fermé ici par use()


            // Optionnel : Afficher un message si la liste est vide (débug)
            if (listeEnfants.isEmpty()) {
                afficherToastPersonnalise("Personne en vue \uD83D\uDD2D ")
            }

            if (::adaptateur.isInitialized && listeEnfants.isNotEmpty()) {
                adaptateur.notifyItemRangeInserted(0, listeEnfants.size)
                // Optionnel : Scroll to top après un rechargement
                recyclerView.scrollToPosition(0)
            }
        }
        catch(e : Exception)
        {
            Log.e(TAG, "Erreur dans la fonction qui charge les données.", e)
            afficherToastPersonnalise(e.message ?: "Une erreur est survenue lors du chargement des données")
        }
    }


    private fun trierEtAfficher(colonne: String, groupe: String? = null) {
        // 1. Filtrage par groupe (inchangé)
        val listeFiltree = if (groupe != null && groupe != "null") {
            listeEnfants.filter { it.groupe == groupe }
        } else {
            listeEnfants
        }

        // 2. Tri intelligent
        val listeTriee = when (colonne) {
            "enfant" -> {
                if (estTriAscendant) {
                    listeFiltree.sortedWith ( compareBy(
                        { it.prenomEnfant.lowercase() },
                        {it.timestampNaissance}
                    )
                    )
                } else {
                    listeFiltree.sortedWith( compareByDescending(
                        { it.prenomEnfant.lowercase() },
                        {it.timestampNaissance}
                }
            }

            "parents" -> {
                val comparateur = compareBy(
                { it.nomsParents.lowercase() },
                { it.prenomEnfant.lowercase() }
            )
                if (estTriAscendant) {
                    listeFiltree.sortedWith(comparateur)
                    } else {
                        listeFiltree.sortedWith(comparateur.reversed())
                    }
                }

            "date" -> {
                listeFiltree.sortedWith { a, b ->
                    val dateA = calculerProchainAnniversaire(a.timestampNaissance)
                    val dateB = calculerProchainAnniversaire(b.timestampNaissance)

                    if (estTriAscendant) {
                        dateA.compareTo(dateB)
                    } else {
                        dateB.compareTo(dateA)
                    }
                }
            }

            else -> listeFiltree
        }

        // 3. Mise à jour de la liste et notification (inchangé)
        val ancienneTaille = listeEnfants.size
        listeEnfants.clear()

        // Si on passe d'une liste pleine à une liste vide (ou inversement), il vaut mieux notifier proprement
        if (::adaptateur.isInitialized) {
            if (ancienneTaille > 0) adaptateur.notifyItemRangeRemoved(0, ancienneTaille)
        }

        listeEnfants.addAll(listeTriee)

        this.colonneTri = colonne
        if (groupe != null) this.groupeActive = groupe

        if (::adaptateur.isInitialized && listeEnfants.isNotEmpty()) {
            adaptateur.notifyItemRangeInserted(0, listeEnfants.size)
        }
    }

    /**
     * Calcule la date du prochain anniversaire à partir d'un timestamp de naissance.
     * Si l'anniversaire est déjà passé cette année, retourne la date de l'année prochaine.
     */
    private fun calculerProchainAnniversaire(timestampNaissance: Long): Long {
        val calendar = Calendar.getInstance()
        val now = Calendar.getInstance()

        // Charger la date de naissance dans le calendrier
        calendar.time = Date(timestampNaissance)

        // Définir l'année de l'anniversaire sur l'année actuelle
        calendar.set(Calendar.YEAR, now.get(Calendar.YEAR))

        // Si l'anniversaire de cette année est déjà passé (ou s'il est aujourd'hui mais on veut les futurs d'abord ?)
        // Comparaison : si calendar (anniv cette année) < now (aujourd'hui)
        if (calendar.before(now)) {
            // On passe à l'année prochaine
            calendar.add(Calendar.YEAR, 1)
        }

        return calendar.timeInMillis
    }


    private fun mettreAJourIndicateursTri(colonneActive: String, ascent: Boolean) {

        colonneTri = colonneActive

        val idStringTitre = when (groupeActive) {
            "famille" -> R.string.label_parents_famille
            "travail" -> R.string.label_parents_travail
            "autre" -> R.string.label_parents_autre
            else -> R.string.label_parents_copains // Cas null ou défaut
        }
        val idStringTitreArrow = when (groupeActive) {
            "famille" -> R.string.label_parents_familleArrow
            "travail" -> R.string.label_parents_travailArrow
            "autre" -> R.string.label_parents_autreArrow
            else -> R.string.label_parents_copainsArrow // Cas null ou défaut
        }
        val idStringTitreArrowReverse = when (groupeActive) {
            "famille" -> R.string.label_parents_familleArrowReverse
            "travail" -> R.string.label_parents_travailArrowReverse
            "autre" -> R.string.label_parents_autreArrowReverse
            else -> R.string.label_parents_copainsArrowReverse // Cas null ou défaut
        }

        headerEnfant.text = getString(R.string.label_enfant)
        headerParents.text = getString(idStringTitre)
        headerDate.text = getString(R.string.label_date)
        // 1. Réinitialiser tous les headers sans flèche
        val texteAvecFleche = when (colonneActive) {
            "enfant" -> getString(if (ascent) R.string.label_enfantArrow else R.string.label_enfantArrowReverse)
            "parents" -> getString(if (ascent) idStringTitreArrow else idStringTitreArrowReverse)
            "date"   -> getString(if (ascent) R.string.label_dateArrow else R.string.label_dateArrowReverse)
            else     -> ""
        }
        when (colonneActive) {
            "enfant" -> headerEnfant.text = texteAvecFleche
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
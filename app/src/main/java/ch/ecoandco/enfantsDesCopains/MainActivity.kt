package ch.ecoandco.enfantsDesCopains // --- IMPORTANT : Vérifiez que ceci correspond à votre vrai package

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.text.InputType
import android.util.Log
import android.view.Gravity
import android.view.HapticFeedbackConstants
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
import org.json.JSONObject
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

    private var estEnModeSelection = false
    // NOUVEAU : Référence à votre barre d'action (à initialiser dans onCreate)
    private lateinit var layoutSelection: View
    private lateinit var layoutBoutonAjouter: View
    private lateinit var textTitreSelection: TextView
    private lateinit var btnAnnuler: Button
    private lateinit var btnExporter: Button
    private lateinit var btnSupprimer: Button
    private lateinit var importWarningBanner: LinearLayout
    private lateinit var importWarningText: TextView
    private lateinit var btnUndoImport: Button
    private lateinit var btnConfirmImport: Button

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

    // Variable temporaire pour stocker le contenu JSON avant l'écriture
    private var jsonContentToSave: String = ""

    private var backupJsonBeforeImport: String? = null


    // UN SEUL launcher pour tous les exports
    private val fileSaverLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            try {
                // Écriture du contenu stocké dans la variable
                contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(jsonContentToSave.toByteArray())
                }
                afficherToastPersonnalise("Fichier sauvegardé avec succès")
            } catch (e: Exception) {
                Log.e(TAG, "Erreur lors de la sauvegarde", e)
                afficherToastPersonnalise("Erreur: ${e.message}")
            }
        }
    }


    // File picker launcher for import
    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val fileSize = contentResolver.openInputStream(uri)?.available() ?: 0
            if (fileSize > 5 * 1024 * 1024) { // Limite à 5 Mo
                afficherToastPersonnalise("Fichier trop volumieux (Max 5 Mo)")
                return@registerForActivityResult
            }
            try {
                // Lecture anticipée pour détecter le mode
                val jsonString = contentResolver.openInputStream(uri)?.use { inputStream ->
                    inputStream.bufferedReader().use { reader -> reader.readText() }
                }

                if (jsonString.isNullOrEmpty()) {
                    afficherToastPersonnalise("Fichier vide ou illisible")
                    return@registerForActivityResult
                }

                val rootJson = JSONObject(jsonString)
                // Détection du mode : si pas de champ "mode", c'est un ancien fichier -> replace
                val mode = if (rootJson.has("mode")) {
                    rootJson.getString("mode")
                } else {
                    "replace"
                }

                backupJsonBeforeImport = bdd.exportToJson() // Votre fonction existante

                // Adaptation du message selon le mode
                val title = if (mode == "merge") "Importer la sélection (Fusion)"
                else "Attention : Remplacement des données"

                val message = if (mode == "merge") {
                    "Les données de ce fichier seront ajoutées à votre base actuelle. Les doublons potentiels seront gérés automatiquement."
                } else {
                    "L'importation de ce fichier va effacer intégralement votre base de données actuelle. Cette action est irréversible. Voulez-vous vraiment continuer ?"
                }

                // Affichage de l'alerte adaptée
                AlertDialog.Builder(this)
                    .setTitle(title)
                    .setMessage(message)
                    .setPositiveButton(if (mode == "merge") "Oui, ajouter à ma liste" else "Oui, effacer et importer") { _, _ ->
                        // On passe le JSON et le mode à la fonction d'import
                        effectuerImport(uri, jsonString, mode)
                    }
                    .setNegativeButton("Annuler", null)
                    .show()

            } catch (e: Exception) {
                Log.e(TAG, "Erreur lecture préliminaire", e)
                afficherToastPersonnalise("Fichier JSON invalide")
            }
        }
    }

    // MODIFICATION DANS effectuerImport (en cas de succès)
    private fun effectuerImport(uri: android.net.Uri, jsonContent: String, mode: String) {
        try {
            if (bdd.importFromJson(jsonContent, mode)) {
                chargerDonneesDepuisBDD()

                // Si on a une sauvegarde, on affiche le bandeau au lieu d'une popup
                if (backupJsonBeforeImport != null) {
                    afficherBandeauSauvegarde()
                } else {
                    afficherToastPersonnalise("Import réussi")
                }
            } else {
                afficherToastPersonnalise("Erreur lors de l'import")
                chargerDonneesDepuisBDD()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors de l'import", e)
            afficherToastPersonnalise("Erreur: ${e.message}")
            chargerDonneesDepuisBDD()
        }
    }

    // Nouvelle fonction pour afficher le bandeau
    private fun afficherBandeauSauvegarde() {
        val dateFormat = android.text.format.DateFormat.getDateFormat(this)
        val timeFormat = android.text.format.DateFormat.getTimeFormat(this)
        val now = Calendar.getInstance().time

        val dateStr = "${dateFormat.format(now)} à ${timeFormat.format(now)}"

        importWarningText.text = "Import effectué le $dateStr. Vos données peuvent encore être restaurées."
        importWarningBanner.visibility = View.VISIBLE
    }

    // Fonction appelée quand on clique sur "Garder"
    private fun validerImportDefinitif() {
        backupJsonBeforeImport = null // On vide la sauvegarde de la mémoire
        importWarningBanner.visibility = View.GONE // On cache le bandeau
        afficherToastPersonnalise("Modifications validées définitivement.")
    }

    // Fonction appelée quand on clique sur "Annuler"
    private fun restaurerSauvegarde() {
        val backup = backupJsonBeforeImport
        if (backup != null) {
            try {
                // On réimporte la sauvegarde
                if (bdd.importFromJson(backup, "replace")) {
                    chargerDonneesDepuisBDD()
                    afficherToastPersonnalise("Version précédente restaurée avec succès")

                    // Nettoyage
                    backupJsonBeforeImport = null
                    importWarningBanner.visibility = View.GONE
                } else {
                    afficherToastPersonnalise("Échec de la restauration")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Erreur restauration", e)
                afficherToastPersonnalise("Erreur lors de la restauration")
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

            layoutSelection = findViewById(R.id.layoutSelection)
            layoutBoutonAjouter = findViewById(R.id.boutonAjouterContainer) // Ou l'ID de votre bouton "+"
            textTitreSelection = findViewById(R.id.textTitreSelection)
            btnAnnuler = findViewById(R.id.btnAnnulerSelection)
            btnExporter = findViewById(R.id.btnExporterSelection)
            btnSupprimer = findViewById(R.id.btnSupprimerSelection)

            layoutSelection.visibility = View.GONE

            // Initialisation des vues du bandeau
            importWarningBanner = findViewById(R.id.importWarningBanner)
            importWarningText = findViewById(R.id.importWarningText)
            btnUndoImport = findViewById(R.id.btnUndoImport)
            btnConfirmImport = findViewById(R.id.btnConfirmImport)

            // Action du bouton Annuler
            btnUndoImport.setOnClickListener {
                restaurerSauvegarde()
            }

            // Action du bouton Valider (Garder)
            btnConfirmImport.setOnClickListener {
                validerImportDefinitif()
            }


// 2. Listener du bouton ANNULER
            btnAnnuler.setOnClickListener {
                quitterModeSelection()
            }

// 3. Listener du bouton EXPORTER
            btnExporter.setOnClickListener {
                val ids = adaptateur.getSelectedIds() // C'est déjà une List
                if (ids.isEmpty()) {
                    afficherToastPersonnalise("Aucun élément sélectionné")
                } else {
                    exporterSelection(ids) // Ça matche parfaitement
                    quitterModeSelection()
                }
            }

// 4. Listener du bouton SUPPRIMER
            btnSupprimer.setOnClickListener {
                val ids = adaptateur.getSelectedIds()

                if (ids.isEmpty()) {
                    Toast.makeText(this, "Rien à supprimer", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                // Message dynamique selon le nombre
                val message = if (ids.size == 1)
                    "Voulez-vous vraiment supprimer cet élément ?"
                else
                    "Voulez-vous vraiment supprimer ces ${ids.size} éléments ?"

                // Dialog de confirmation
                AlertDialog.Builder(this)
                    .setTitle("Confirmation")
                    .setMessage(message)
                    .setPositiveButton("Supprimer") { _, _ ->
                        supprimerElements(ids)
                        quitterModeSelection()
                    }
                    .setNegativeButton("Annuler", null)
                    .show()
            }

            // 2. Initialiser la Base de Données
            // Cela va déclencher onCreate() dans MaBaseDeDonnees et insérer les données de test
            bdd = MaBaseDeDonnees(this)

            // Initialisation du RecyclerView
            recyclerView = findViewById(R.id.recyclerViewAnniversaires) // Vérifie que l'ID correspond à ton XML
            recyclerView.layoutManager = LinearLayoutManager(this)

// Lancement du premier chargement
            chargerDonneesDepuisBDD()


            adaptateur = AnniversaireAdapter(
                getListeDonnees = { listeEnfants },

                // --- CLIC COURT ---
                onItemClick = { id, position ->
                    if (estEnModeSelection) {
                        // Si on est EN mode sélection : le clic court bascule la sélection
                        adaptateur.toggleSelection(id, position)
                        mettreAJourTitreSelection(adaptateur.getSelectedCount())

                        // Optionnel : Si plus aucun élément n'est sélectionné, on quitte le mode ?
                        // if (adaptateur.getSelectedCount() == 0) quitterModeSelection()
                    } else {
                        // Si on est en mode NORMAL : le clic court fait ce qu'il veut (rien, ou ouvrir le détail)
                        // Pour l'instant, on ne fait rien ou on ouvre le détail
                        // Toast.makeText(this, "Détail de $id", Toast.LENGTH_SHORT).show()
                    }
                },

                // --- CLIC LONG (C'EST ICI QUE ÇA SE PASSE) ---
                onItemLongClick = { id, position ->
                    if (!estEnModeSelection) {
                        // 1. VIBRATION (Haptic Feedback)
                        val itemView = recyclerView.findViewHolderForAdapterPosition(position)?.itemView
                        itemView?.let { view ->
                            view.isHapticFeedbackEnabled = true
                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        }

                        // 2. LANCER LE MODE SÉLECTION
                        activerModeSelection(id, position)
                    } else {
                        // Si on est déjà en mode sélection, un clic long agit comme un toggle normal
                        adaptateur.toggleSelection(id, position)
                        mettreAJourTitreSelection(adaptateur.getSelectedCount())
                    }
                }
            )
            recyclerView.adapter = adaptateur

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
            val fileName = "anniversaires_export_complet_$timeStamp.json"

            // 1. Générez le JSON complet
            val jsonContent = bdd.exportToJson()

            // 2. Stockez le contenu
            jsonContentToSave = jsonContent

            // 3. Lancez le launcher avec le nom spécifique
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

    // --- NOUVELLES FONCTIONS POUR L'ÉTAPE B ---

    /**
     * Fonction appelée par le listener de l'adapter quand on fait un clic long
     */
    private fun activerModeSelection(idPremierItem: Int, position: Int) {
        if (estEnModeSelection) return // Déjà activé

        estEnModeSelection = true
        adaptateur.isSelectionMode = true
        adaptateur.toggleSelection(idPremierItem, position)
        layoutSelection.visibility = View.VISIBLE
        layoutBoutonAjouter.visibility = View.GONE
        // 4. On met à jour le titre (1 élément sélectionné)
        mettreAJourTitreSelection(adaptateur.getSelectedCount())
    }


    /**
     * Fonction pour mettre à jour le texte "X élément(s) sélectionné(s)"
     */
    private fun mettreAJourTitreSelection(count: Int) {
        val text = if (count == 1) "$count élément sélectionné" else "$count éléments sélectionnés"
        textTitreSelection.text = text
    }
    private fun supprimerElements(ids: List<Int>) {
        // On crée une liste des positions à supprimer
        val positionsASupprimer = mutableListOf<Int>()

        // On parcourt la liste à l'envers pour ne pas fausser les index lors de la suppression
        for (i in listeEnfants.size - 1 downTo 0) {
            val item = listeEnfants[i]
            if (ids.contains(item.idEnfant)) {
                positionsASupprimer.add(i) // On note la position
                listeEnfants.removeAt(i)   // On retire de la liste locale immédiatement
            }
        }

        // 2. Supprimer dans la BDD (toujours en premier ou en parallèle)
        bdd.supprimerParIds(ids)

         positionsASupprimer.sorted().forEach { position ->
            adaptateur.notifyItemRemoved(position)
        }

         afficherToastPersonnalise("${ids.size} élément(s) supprimé(s)")

        // Si la liste est vide ou pour être sûr, on peut vérifier l'état
        if (listeEnfants.isEmpty()) {
           afficherToastPersonnalise("Aucun élément à supprimer")
        }
    }

    /**
     * Fonction pour quitter le mode sélection (Bouton Annuler)
     */
    private fun quitterModeSelection() {
        estEnModeSelection = false
        adaptateur.clearSelection() // Vide la liste et notifie l'adapter
        layoutSelection.visibility = View.GONE
        layoutBoutonAjouter.visibility = View.VISIBLE
        textTitreSelection.text = ""
    }


    private fun lancerChangementCategorie(idsEnfantsSelectionnes: List<Int>, context: Context, dialog: AlertDialog) {
        dialog.dismiss()

        val categories = listOf("-- Sélectionner --", "Copains", "Famille", "Travail", "Autre")
        var categorieSelectionnee = categories[0] // Valeur par défaut

        // 3. Créer le layout du Spinner dynamiquement
        val spinner = Spinner(context)
        val adapter = ArrayAdapter(
            context,
            android.R.layout.simple_spinner_item,
            categories
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter

        // Écouter la sélection de l'utilisateur
        spinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                categorieSelectionnee = categories[position]
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        val padding = 50
        spinner.setPadding(padding, 20, padding, 20)

        AlertDialog.Builder(context)
            .setTitle("Choisir une nouvelle catégorie")
            .setMessage("Sélectionnez la catégorie de destination :")
            .setView(spinner)
            .setPositiveButton("Valider") { _, _ ->
                // L'utilisateur a cliqué sur Valider
                executerLeChangementDeCategorie(idsEnfantsSelectionnes, categorieSelectionnee)
            }
            .setNegativeButton("Annuler") { d, _ ->
                d.dismiss()
            }
            .show()
    }

    private fun executerLeChangementDeCategorie(ids: List<Int>, categorie: String) {
        val exportTo = when (categorie) {
            "Copains" -> "copains"
            "Famille" -> "famille"
            "Travail" -> "travail"
            "Autre" -> "autre"
            else -> {
                afficherToastPersonnalise("Catégorie invalide")
                return
            }
        }
        afficherToastPersonnalise("Traitement en cours...")
        // On crée un nouveau thread pour ne pas bloquer l'interface
        Thread {
            // Ce code s'exécute en arrière-plan
            val succes = bdd.recupIDparentsEtChangeCategorie(ids, exportTo)

            // IMPORTANT : Pour afficher un Toast ou modifier l'UI, on doit revenir sur le thread principal
            runOnUiThread {
                if (succes) {
                    afficherToastPersonnalise("Ok, déplacé vers $categorie")
                    chargerDonneesDepuisBDD()
                } else {
                    afficherToastPersonnalise("Échec de la mise à jour")
                }
            }
        }.start()
    }

    private fun exporterSelection(ids: List<Int>) {
        try {
            val context = this

            val containerLayout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(64, 48, 64, 24)
            }

            fun ajouterBoutonAction(texte: String, action: () -> Unit) {
                val button = Button(context).apply {
                    text = texte
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        topMargin = 24
                        bottomMargin = 0
                    }
                    textSize = 16f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    setOnClickListener {
                        action()
                        // Fermer le dialog si nécessaire via une référence, ou laisser le clic fermer le bouton
                        // Si le dialog est créé localement, il faut le garder en référence pour le fermer
                    }
                }
                containerLayout.addView(button)
            }

            // On crée le dialog mais on le garde en référence pour pouvoir le fermer depuis les actions
            val dialog = AlertDialog.Builder(context)
                .setTitle("Action pour la sélection")
                .setMessage("Que souhaitez-vous faire des éléments sélectionnés ?")
                .setView(containerLayout)
                .setNegativeButton("Annuler") { d, _ -> d.dismiss() }
                .create()

            ajouterBoutonAction("Partager la sélection (fichier JSON)") {
                try {
                    val selectedPersons = bdd.createPersonsFromSelection(ids)

                    if (selectedPersons.isEmpty()) {
                        afficherToastPersonnalise("Aucune donnée à exporter")
                        dialog.dismiss()
                        return@ajouterBoutonAction
                    }
                    val parser = DataParser()
                    val jsonContent = parser.exportSelection(selectedPersons)

                    // 2. Préparez le nom de fichier spécifique
                    val timeStamp = SimpleDateFormat(
                        "yyyy_MM_dd_HHmmss",
                        Locale.getDefault()
                    ).format(Calendar.getInstance().time)
                    val fileName = "anniversaires_selection_$timeStamp.json"

                    // 3. Stockez le contenu dans la variable commune
                    jsonContentToSave = jsonContent

                    // 4. Lancez le MÊME launcher mais avec le nom de fichier différent
                    fileSaverLauncher.launch(fileName)
                    dialog.dismiss()

                } catch (e: Exception) {
                    Log.e(TAG, "Erreur préparation export", e)
                    afficherToastPersonnalise("Erreur lors de l'export: ${e.message}")
                    dialog.dismiss()
                }
            }

            ajouterBoutonAction("-> Changer de catégorie") {
                lancerChangementCategorie(ids, this, dialog)
            }
            dialog.show()

        } catch (e: Exception) {
            Log.e(TAG, "Erreur préparation sélection", e)
            afficherToastPersonnalise("Erreur: ${e.message}")
        }
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
        // 1. On s'assure que listeFiltree est bien typée (comme vu avant)
        val listeFiltree: List<LigneAnniversaire> = if (groupe != null && groupe != "null") {
            listeEnfants.filter { it.groupe == groupe }
        } else {
            listeEnfants
        }

// 2. On explicite le type de retour du 'when' pour aider le compilateur
        val listeTriee: List<LigneAnniversaire> = when (colonne) {
            "enfant" -> {
                // Le comparateur est local, c'est OK, mais on retourne directement le résultat
                val comparateur = compareBy<LigneAnniversaire>(
                    { it.prenomEnfant.lowercase() },
                    { it.timestampNaissance }
                )
                if (estTriAscendant) {
                    listeFiltree.sortedWith(comparateur)
                } else {
                    listeFiltree.sortedWith(comparateur.reversed())
                }
                // La dernière ligne du bloc est ce qui est retourné pour cette branche
            }

            "parents" -> {
                val comparateur = compareBy<LigneAnniversaire>(
                    { it.nomsParents.lowercase() },
                    { it.prenomEnfant.lowercase() }
                )
                if (estTriAscendant) listeFiltree.sortedWith(comparateur)
                else listeFiltree.sortedWith(comparateur.reversed())
            }

            "date" -> {
                val comparateurDate = Comparator<LigneAnniversaire> { a, b ->
                    val dateA = calculerProchainAnniversaire(a.timestampNaissance)
                    val dateB = calculerProchainAnniversaire(b.timestampNaissance)
                    dateA.compareTo(dateB)
                }
                if (estTriAscendant) listeFiltree.sortedWith(comparateurDate)
                else listeFiltree.sortedBy { it.timestampNaissance }
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
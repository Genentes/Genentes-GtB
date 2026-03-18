package ch.ecoandco.genentes // <--- IMPORTANT : Vérifiez que ceci correspond à votre vrai package

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.database.Cursor

class MainActivity : AppCompatActivity() {

    // Déclaration des variables
    private lateinit var recyclerView: RecyclerView
    private lateinit var adaptateur: AnniversaireAdapter
    private lateinit var bdd: MaBaseDeDonnees

    // La liste qui va contenir nos objets formatés pour l'affichage
    private val listeEnfants = mutableListOf<LigneAnniversaire>()

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

        // --- BONUS : Tester le bouton "Ajouter" (juste un message pour l'instant) ---
        // Vous pourrez plus tard ajouter la logique d'ouverture de formulaire ici
        /*
        val boutonAjouter = findViewById<Button>(R.id.boutonAjouter)
        boutonAjouter.setOnClickListener {
            Toast.makeText(this, "Fonctionnalité à venir : Ouvrir le formulaire", Toast.LENGTH_SHORT).show()
        }
        */
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
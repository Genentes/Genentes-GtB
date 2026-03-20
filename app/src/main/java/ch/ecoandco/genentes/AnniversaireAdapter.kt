package ch.ecoandco.genentes // Adaptez avec votre vrai nom de package

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import ch.ecoandco.genentes.R.layout.item_ligne_anniversaire

// 1. Une petite classe "modèle" pour transporter les données d'une ligne
// C'est plus propre que de passer un Cursor directement à l'adapter
data class LigneAnniversaire(
    val prenomEnfant: String,
    val nomsParents: String,
    val timestampNaissance: Long // On garde le Long brut pour le trier si besoin
)

// 2. La classe Adapter principale
class AnniversaireAdapter(
    private val listeDonnees: List<LigneAnniversaire> // La liste complète à afficher
) : RecyclerView.Adapter<AnniversaireAdapter.MonViewHolder>() {

    // --- ÉTAPE A : Le ViewHolder ---
    // C'est lui qui "tient" les vues d'une seule ligne (les 3 TextView)
    class MonViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val textEnfant: TextView = itemView.findViewById(R.id.textEnfant)
        val textParents: TextView = itemView.findViewById(R.id.textParents)
        val textDate: TextView = itemView.findViewById(R.id.textDate)
    }

    // --- ÉTAPE B : Création de la vue (Quand on a besoin d'une nouvelle ligne) ---
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MonViewHolder {
        // On transforme le XML "item_ligne_anniversaire.xml" en un objet View Java
        val vueLigne = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_ligne_anniversaire, parent, false)

        return MonViewHolder(vueLigne)
    }

    // --- ÉTAPE C : Remplissage des données (Le cœur du réacteur) ---
    override fun onBindViewHolder(holder: MonViewHolder, position: Int) {
        // On récupère l'objet correspondant à la ligne actuelle (0, 1, 2...)
        val elementActuel = listeDonnees[position]

        // 1. On injecte le texte simple
        holder.textEnfant.text = elementActuel.prenomEnfant
        holder.textParents.text = elementActuel.nomsParents

        // 2. On formate la date (Conversion Long -> "dd/MM/yyyy")
        val format = SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE)
        val dateObjet = Date(elementActuel.timestampNaissance)
        holder.textDate.text = format.format(dateObjet)

        // Astuce : Si vous voulez trier par ordre de date pour les anniversaires à venir,
        // c'est ici qu'on pourrait ajouter de la logique visuelle (ex: couleur différente si c'est bientôt)
    }

    // --- ÉTAPE D : Combien de lignes ? ---
    override fun getItemCount(): Int {
        return listeDonnees.size
    }
}
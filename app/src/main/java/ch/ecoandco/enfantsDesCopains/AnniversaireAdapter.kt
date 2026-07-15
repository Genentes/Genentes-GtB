package ch.ecoandco.enfantsDesCopains // Adaptez avec votre vrai nom de package

import android.app.AlertDialog
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.util.Log
import android.view.HapticFeedbackConstants
import androidx.recyclerview.widget.RecyclerView
import ch.ecoandco.enfantsDesCopains.R.layout.item_ligne_anniversaire
import ch.ecoandco.enfantsDesCopains.utils.DateUtils
import android.text.Spannable
import android.text.SpannableString
import android.text.style.StyleSpan
import android.graphics.Typeface


// 1. Une petite classe "modèle" pour transporter les données d'une ligne
// C'est plus propre que de passer un Cursor directement à l'adapter
data class LigneAnniversaire(
    val idEnfant: Int,
    val prenomEnfant: String,
    val nomsParents: String,
    val timestampNaissance: Long // On garde le Long brut pour le trier si besoin
)

// 2. La classe Adapter principale
class AnniversaireAdapter(
    private val listeDonnees: List<LigneAnniversaire>, // La liste complète à afficher
    private val onSupprimer: (Int) -> Unit         // NOUVEAU : Une fonction qui prend un ID (Int)
) : RecyclerView.Adapter<AnniversaireAdapter.MonViewHolder>() {

    companion object {
        private const val TAG = "AnniversaireAdapter"
    }

    // --- ÉTAPE A : Le ViewHolder ---
    // C'est lui qui "tient" les vues d'une seule ligne (les 3 TextView)
    class MonViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val textEnfant: TextView = itemView.findViewById(R.id.textEnfant)
        val textParents: TextView = itemView.findViewById(R.id.textParents)
        val textDate: TextView = itemView.findViewById(R.id.textDate)
    }

    // --- ÉTAPE B : Création de la vue (Quand on a besoin d'une nouvelle ligne) ---
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MonViewHolder {
        return try {
            // On transforme le XML "item_ligne_anniversaire.xml" en un objet View Java
            val vueLigne = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_ligne_anniversaire, parent, false)

            MonViewHolder(vueLigne)
        } catch (e: Exception) {
            Log.e(TAG, "Erreur dans onCreateViewHolder", e)
            val fallbackView = LinearLayout(parent.context).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(parent.context).apply { id = R.id.textEnfant })
                addView(TextView(parent.context).apply { id = R.id.textParents })
                addView(TextView(parent.context).apply { id = R.id.textDate })
            }
            MonViewHolder(fallbackView)
        }
    }

    // --- ÉTAPE C : Remplissage des données (Le cœur du réacteur) ---
    override fun onBindViewHolder(holder: MonViewHolder, position: Int) {
        try {
            // On récupère l'objet correspondant à la ligne actuelle (0, 1, 2...)
            val elementActuel = listeDonnees[position]

            val prenom = elementActuel.prenomEnfant
            val ageInfo = DateUtils.formatAgeWithQuarters(elementActuel.timestampNaissance)
            val spannableText = SpannableString("$prenom\n$ageInfo")

// Application du style GRAS uniquement sur la longueur du prénom
            spannableText.setSpan(
                StyleSpan(Typeface.BOLD),
                0, // Début : index 0
                prenom.length, // Fin : longueur du prénom
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )

            // 1. On injecte le texte simple
            holder.textEnfant.text = spannableText
            holder.textParents.text = elementActuel.nomsParents
            val texteFormate = DateUtils.formatAge(elementActuel.timestampNaissance)

            holder.textDate.text = texteFormate

            // --- AJOUT DU CLIC LONG ICI ---
            holder.itemView.setOnLongClickListener {
                // 1. Récupérer la position actuelle et sûre
                val position = holder.bindingAdapterPosition

                // 2. Vérification de sécurité CRUCIALE
                // Si la position est NO_POSITION, on ne fait rien (l'élément a peut-être déjà bougé/disparu)
                if (position == RecyclerView.NO_POSITION) {
                    return@setOnLongClickListener true
                }

                // 1. Feedback visuel immédiat (optionnel mais recommandé)
                // Cela fait vibrer le téléphone très brièvement si autorisé
                holder.itemView.isHapticFeedbackEnabled = true
                holder.itemView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)

                // 2. Votre logique de suppression
                // On peut afficher une confirmation avant de supprimer pour éviter les erreurs
                AlertDialog.Builder(holder.itemView.context)
                    .setTitle("Supprimer ?")
                    .setMessage("Voulez-vous vraiment supprimer la ligne de ${elementActuel.prenomEnfant} ?")
                    .setPositiveButton("Oui") { _, _ ->
                            onSupprimer(elementActuel.idEnfant)
                    }
                    .setNegativeButton("Annuler", null)
                    .show()

                // Retourner true pour indiquer qu'on a bien géré l'événement
                // (cela empêche le clic court de se déclencher aussi)
                true
            }
            // Astuce : Si vous voulez trier par ordre de date pour les anniversaires à venir,
            // c'est ici qu'on pourrait ajouter de la logique visuelle (ex: couleur différente si c'est bientôt)
        } catch (e: Exception) {
            Log.e(TAG, "Erreur dans onBindViewHolder", e)
        }
    }

    // --- ÉTAPE D : Combien de lignes ? ---
    override fun getItemCount(): Int {
        return try {
            listeDonnees.size
        } catch (e: Exception) {
            Log.e(TAG, "Erreur dans getItemCount", e)
            0
        }
    }
}
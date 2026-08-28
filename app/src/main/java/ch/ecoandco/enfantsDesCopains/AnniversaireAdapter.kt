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
import android.widget.CheckBox

// 1. Une petite classe "modèle" pour transporter les données d'une ligne
// C'est plus propre que de passer un Cursor directement à l'adapter.
data class LigneAnniversaire(
    val idEnfant: Int,
    val prenomEnfant: String,
    val nomsParents: String,
    val groupe: String,
    val timestampNaissance: Long // On garde le Long brut pour le trier si besoin
)

// 2. La classe Adapter principale
class AnniversaireAdapter(
    // On passe une fonction qui renvoie la liste à jour à chaque fois
    private val getListeDonnees: () -> List<LigneAnniversaire>,
    private val onItemClick: (Int, Int) -> Unit,
    private val onItemLongClick: (Int, Int) -> Unit
) : RecyclerView.Adapter<AnniversaireAdapter.MonViewHolder>() {

    companion object {
        private const val TAG = "AnniversaireAdapter"
    }
    // --- ÉTAT INTERNE DE SÉLECTION ---
    private val selectedIds = mutableSetOf<Int>()
    var isSelectionMode = false

    // --- MÉTHODES PUBLIQUES POUR L'ACTIVITY (Étapes B et C) ---

    fun getSelectedIds(): List<Int> = selectedIds.toList()
    fun getSelectedCount(): Int = selectedIds.size

    fun toggleSelection(id: Int, position: Int) {
        if (selectedIds.contains(id)) {
            selectedIds.remove(id)
        } else {
            selectedIds.add(id)
        }
        notifyItemChanged(position)
    }

    fun clearSelection() {
        selectedIds.clear()
        isSelectionMode = false
        notifyDataSetChanged()
    }

    // --- ÉTAPE A : Le ViewHolder ---
    // C'est lui qui "tient" les vues d'une seule ligne (les 3 TextView)
    inner class MonViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val textEnfant: TextView = itemView.findViewById(R.id.textEnfant)
        val textParents: TextView = itemView.findViewById(R.id.textParents)
        val textDate: TextView = itemView.findViewById(R.id.textDate)
        val checkBoxSelection: CheckBox = itemView.findViewById(R.id.checkBoxSelection)

        // Variables tampons
        var currentId: Int = -1
        var currentPosition: Int = -1

        init {
            // Clic Court sur toute la ligne
            itemView.setOnClickListener {
                if (currentId != -1 && currentPosition != -1) {
                    onItemClick(currentId, currentPosition)
                }
            }

            // Clic Long sur toute la ligne
            itemView.setOnLongClickListener {
                if (currentId != -1 && currentPosition != -1) {
                    onItemLongClick(currentId, currentPosition)
                    true
                } else {
                    false
                }
            }

            // Clic spécifique sur la CheckBox (déclenche la même action que la ligne)
            checkBoxSelection.setOnClickListener {
                if (currentId != -1 && currentPosition != -1) {
                    onItemClick(currentId, currentPosition)
                }
            }
        }

        fun bind(position: Int) {
            try {
                val listeActuelle = getListeDonnees()
                if (position >= listeActuelle.size) return

                val element = listeActuelle[position]

                // Mise à jour des références pour les écouteurs
                currentId = element.idEnfant // Assurez-vous que idEnfant est l'ID unique
                currentPosition = position

                // --- VOTRE LOGIQUE DE FORMATAGE EXISTANTE ---
                val prenom = element.prenomEnfant
                val ageInfo = DateUtils.formatAgeWithQuarters(element.timestampNaissance)
                val spannableText = SpannableString("$prenom\n$ageInfo")

                spannableText.setSpan(
                    StyleSpan(Typeface.BOLD),
                    0,
                    prenom.length,
                    android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )

                textEnfant.text = spannableText
                textParents.text = element.nomsParents
                textDate.text = DateUtils.formatAge(element.timestampNaissance)

                // --- NOUVELLE LOGIQUE DE SÉLECTION VISUELLE ---

                // 1. Afficher/Masquer la CheckBox selon le mode
                checkBoxSelection.visibility = if (isSelectionMode) View.VISIBLE else View.GONE

                // 2. Vérifier si l'item est sélectionné
                val isSelected = selectedIds.contains(element.idEnfant)

                // 3. Cocher/Décocher (sans boucle infinie car on ne change pas l'état ici, on l'applique)
                checkBoxSelection.isChecked = isSelected


            } catch (e: Exception) {
                Log.e(TAG, "Erreur dans bind", e)
            }
        }
    }

// --- MÉTHODES OBLIGATOIRES ---

override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MonViewHolder {
    return try {
        val vueLigne = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_ligne_anniversaire, parent, false) // Vérifiez le nom du fichier XML
        MonViewHolder(vueLigne)
    } catch (e: Exception) {
        Log.e(TAG, "Erreur dans onCreateViewHolder", e)
        // Fallback simplifié (sans checkbox dans le fallback pour éviter crash)
        val fallbackView = LinearLayout(parent.context).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(parent.context).apply { id = R.id.textEnfant })
            addView(TextView(parent.context).apply { id = R.id.textParents })
            addView(TextView(parent.context).apply { id = R.id.textDate })
        }
        MonViewHolder(fallbackView)
    }
}
    override fun onBindViewHolder(holder: MonViewHolder, position: Int) {
        holder.bind(position)
    }

    override fun getItemCount(): Int {
        return try {
            getListeDonnees().size
        } catch (e: Exception) {
            Log.e(TAG, "Erreur dans getItemCount", e)
            0
        }
    }
}
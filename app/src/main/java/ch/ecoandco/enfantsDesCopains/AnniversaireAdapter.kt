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
    private val onSupprimer: (Int) -> Unit
) : RecyclerView.Adapter<AnniversaireAdapter.MonViewHolder>() {

    companion object {
        private const val TAG = "AnniversaireAdapter"
    }

    // --- GESTION DU MODE SÉLECTION ---
    var isSelectionMode = false
        private set // Modifiable uniquement via les méthodes publiques

    private val selectedIds = HashSet<Int>()

    // Callback pour prévenir l'Activity quand le nombre de personnes sélectionnées change.
    var onSelectionChanged: ((Int) -> Unit)? = null

    // --- MÉTHODES DE CONTRÔLE ---

    fun activerModeSelection() {
        isSelectionMode = true
        selectedIds.clear()
        notifyItemRangeChanged(0, itemCount)
    }

    fun desactiverModeSelection() {
        isSelectionMode = false
        selectedIds.clear()
        notifyItemRangeChanged(0, itemCount)
    }

    fun getSelectedIds(): Set<Int> {
        return HashSet(selectedIds)
    }

    fun toggleSelection(id: Int, position: Int) {
        if (selectedIds.contains(id)) {
            selectedIds.remove(id)
        } else {
            selectedIds.add(id)
        }
        notifyItemChanged(position)
        onSelectionChanged?.invoke(selectedIds.size)
    }

    // --- ÉTAPE A : Le ViewHolder ---
    // C'est lui qui "tient" les vues d'une seule ligne (les 3 TextView)
    class MonViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val textEnfant: TextView = itemView.findViewById(R.id.textEnfant)
        val textParents: TextView = itemView.findViewById(R.id.textParents)
        val textDate: TextView = itemView.findViewById(R.id.textDate)
        val checkBox: CheckBox = itemView.findViewById(R.id.checkBoxSelection)
    }

    // --- ÉTAPE B : Création de la vue (Quand on a besoin d'une nouvelle ligne) ---
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MonViewHolder {
        return try {
            // On transforme le XML "item_ligne_anniversaire.xml" en un objet View Java
            val vueLigne = LayoutInflater.from(parent.context)
                .inflate(item_ligne_anniversaire, parent, false)

            MonViewHolder(vueLigne)
        } catch (e: Exception) {
            Log.e(TAG, "Erreur dans onCreateViewHolder", e)
            val fallbackView = LinearLayout(parent.context).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(parent.context).apply { id = R.id.textEnfant })
                addView(TextView(parent.context).apply { id = R.id.textParents })
                addView(TextView(parent.context).apply { id = R.id.textDate })
                // Ajout manuel d'une checkbox pour le fallback si nécessaire,
                // mais ici, on suppose que le XML principal est corrigé.
            }
            MonViewHolder(fallbackView)
        }
    }

    // --- ÉTAPE C : Remplissage des données (Le cœur du réacteur) ---
    override fun onBindViewHolder(holder: MonViewHolder, position: Int) {
        try {

            // On récupère la liste FRAÎCHE à chaque bind
            val listeActuelle = getListeDonnees()

            // Sécurité : si la position est hors limite (cas rare de concurrence)
            if (position >= listeActuelle.size) return

            val elementActuel = listeActuelle[position]

            // 1. Gestion de la visibilité de la CheckBox
            holder.checkBox.visibility = if (isSelectionMode) View.VISIBLE else View.GONE

            // 2. Gestion de l'état et des écouteurs selon le mode
            if (isSelectionMode) {
                // Mode SÉLECTION activé
                holder.checkBox.isChecked = selectedIds.contains(elementActuel.idEnfant)

                // On définit le listener de la checkbox
                holder.checkBox.setOnCheckedChangeListener { _, _ ->
                    toggleSelection(elementActuel.idEnfant, position)
                }

                // Clic sur la ligne = cocher/décocher
                holder.itemView.setOnClickListener {
                    holder.checkBox.isChecked = !holder.checkBox.isChecked
                }

                // On désactive le clic-long en mode sélection
                holder.itemView.setOnLongClickListener(null)

            } else {
                // Mode NORMAL
                holder.checkBox.setOnCheckedChangeListener(null)
                holder.checkBox.isChecked = false

                // Réactivation du Clic Long pour la suppression
                holder.itemView.setOnLongClickListener {
                    val pos = holder.bindingAdapterPosition
                    if (pos == RecyclerView.NO_POSITION) return@setOnLongClickListener true

                    holder.itemView.isHapticFeedbackEnabled = true
                    holder.itemView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)

                    AlertDialog.Builder(holder.itemView.context)
                        .setTitle("Supprimer ?")
                        .setMessage("Voulez-vous vraiment supprimer la ligne de ${elementActuel.prenomEnfant} ?")
                        .setPositiveButton("Oui") { _, _ ->
                            onSupprimer(elementActuel.idEnfant)
                        }
                        .setNegativeButton("Annuler", null)
                        .show()
                    true
                }

                // En mode normal, le clic-court ne fait rien (ou peut lancer un détail si tu veux).
                holder.itemView.setOnClickListener(null)
            }

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
            }
        catch (e: Exception) {
            Log.e(TAG, "Erreur dans onBindViewHolder", e)
        }
    }

    // --- ÉTAPE D : Combien de lignes ? ---
    override fun getItemCount(): Int {
        return try {
            getListeDonnees().size
        } catch (e: Exception) {
            Log.e(TAG, "Erreur dans getItemCount", e)
            0
        }
    }
}
package ch.ecoandco.genentes.utils // <-- Cette ligne doit correspondre à l'emplacement de votre fichier

import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.format.DateTimeFormatter
import java.util.Locale

// Le mot 'object' signifie que ce fichier contient des fonctions statiques accessibles directement
object DateUtils {

    fun formatAgeWithQuarters(timestampMillis: Long): String? {
        val zoneSuisse = ZoneId.of("Europe/Zurich")

        val dateNaissance = java.time.Instant.ofEpochMilli(timestampMillis)
            .atZone(zoneSuisse)
            .toLocalDate()

        val aujourdhui = LocalDate.now(zoneSuisse)

        if (dateNaissance.isAfter(aujourdhui)) return null

        val annees = ChronoUnit.YEARS.between(dateNaissance, aujourdhui)
        val dernierAnniversaire = dateNaissance.plusYears(annees)
        val joursDepuisAnniv = ChronoUnit.DAYS.between(dernierAnniversaire, aujourdhui)
        val dureeAnnee = if (dernierAnniversaire.isLeapYear) 366 else 365
        val fraction = joursDepuisAnniv.toDouble() / dureeAnnee

        val quartText = when {
            fraction < 0.001 -> " \uD83C\uDF82"
            fraction < 0.25 -> " ans"
            fraction < 0.50 -> "¼ ans"
            fraction < 0.75 -> "½ ans"
            fraction < 0.999 -> "¾ ans"
            else -> ""
        }

        return "$annees$quartText"
    }

    fun formatAge(timestampMillis: Long): String? {
        val zoneSuisse = ZoneId.of("Europe/Zurich")
        val dateNaissance = java.time.Instant.ofEpochMilli(timestampMillis)
            .atZone(zoneSuisse)
            .toLocalDate()
        val aujourdhui = LocalDate.now(zoneSuisse)
        if (dateNaissance.isAfter(aujourdhui)) return null

        val annees = ChronoUnit.YEARS.between(dateNaissance, aujourdhui)

        var prochainAnniversaire = dateNaissance.plusYears(annees)
        if (prochainAnniversaire.isBefore(aujourdhui)) {
            prochainAnniversaire = prochainAnniversaire.plusYears(1)
        }
        val joursJusquaAnniv = ChronoUnit.DAYS.between(aujourdhui, prochainAnniversaire)
        val textComplement: String? = if (joursJusquaAnniv < 30) {
            "\n➣ $joursJusquaAnniv jours"
        } else {
            "" // Ou une chaîne vide "" si vous préférez
        }

        val format = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.FRANCE)
        return "${dateNaissance.format(format)}$textComplement"
    }
}
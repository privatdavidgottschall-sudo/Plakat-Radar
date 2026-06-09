package de.bsw.plakatradar.core

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object OfficialExport {
    private val date = SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY)

    fun toCsv(state: LocalTeamState, municipality: String): String {
        val header = listOf(
            "Lfd. Nr.", "Kommune", "Standort", "Breitengrad", "Laengengrad", "Art", "Status",
            "Aufhaengedatum", "Geplantes Entferndatum", "Bemerkung"
        ).joinToString(";")
        val rows = state.posters.mapIndexed { index, poster ->
            listOf(
                (index + 1).toString(), municipality, poster.addressHint, poster.latitude.toString(), poster.longitude.toString(),
                poster.type.name, poster.status.name, date.format(Date(poster.createdAt)),
                poster.plannedRemovalAt?.let { date.format(Date(it)) } ?: "", poster.officialNote
            ).joinToString(";") { it.csv() }
        }
        return (listOf(header) + rows).joinToString("\n")
    }

    private fun String.csv(): String = "\"" + replace("\"", "\"\"") + "\""
}

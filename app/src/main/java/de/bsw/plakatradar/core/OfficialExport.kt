package de.bsw.plakatradar.core

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object OfficialExport {
    private val date = SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY)

    fun toCsv(state: LocalTeamState, municipality: String): String =
        buildCsv(state = state, municipality = municipality, photoPathFor = null)

    fun writeZip(state: LocalTeamState, municipality: String, photosDir: File, outputFile: File): File {
        ZipOutputStream(outputFile.outputStream().buffered()).use { zip ->
            val csv = buildCsv(
                state = state,
                municipality = municipality,
                photoPathFor = { index, poster -> photoEntryName(index, poster, photosDir) ?: "Kein Foto" }
            )

            zip.putNextEntry(ZipEntry("plakatliste.csv"))
            zip.write(csv.toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            state.posters.forEachIndexed { index, poster ->
                val originalName = poster.localPhotoFileName ?: return@forEachIndexed
                val source = File(photosDir, originalName)
                val entryName = photoEntryName(index, poster, photosDir) ?: return@forEachIndexed
                if (!source.isFile) return@forEachIndexed

                zip.putNextEntry(ZipEntry(entryName))
                source.inputStream().use { input -> input.copyTo(zip) }
                zip.closeEntry()
            }
        }
        return outputFile
    }

    private fun buildCsv(
        state: LocalTeamState,
        municipality: String,
        photoPathFor: ((Int, Poster) -> String?)?
    ): String {
        val header = mutableListOf(
            "Nr.",
            "Kommune",
            "Standortbeschreibung",
            "Plakatart",
            "Aktueller Status",
            "Erfasst am",
            "Abnahme geplant am",
            "Bemerkung für Stadtverwaltung",
            "GPS-Breitengrad",
            "GPS-Längengrad",
            "Google-Maps-Link"
        )
        if (photoPathFor != null) header += "Foto-Datei"

        val rows = state.posters.mapIndexed { index, poster ->
            val mapsLink = "https://www.google.com/maps/search/?api=1&query=${poster.latitude},${poster.longitude}"
            val columns = mutableListOf(
                (index + 1).toString(),
                municipality,
                poster.addressHint.ifBlank { "Keine Standortbeschreibung eingetragen" },
                poster.type.toHumanText(),
                poster.status.toHumanText(),
                date.format(Date(poster.createdAt)),
                poster.plannedRemovalAt?.let { date.format(Date(it)) } ?: "Nicht eingetragen",
                poster.officialNote.ifBlank { "Keine Bemerkung" },
                poster.latitude.toString(),
                poster.longitude.toString(),
                mapsLink
            )
            if (photoPathFor != null) columns += (photoPathFor(index, poster) ?: "Kein Foto")
            columns.joinToString(";") { it.csv() }
        }

        // UTF-8 BOM helps older Excel versions open German umlauts correctly.
        // The sep=; hint tells Excel/LibreOffice that this file uses semicolons,
        // so headers like "Aktueller Status" are not split at spaces.
        val separatorHint = "sep=;"
        val headerLine = header.joinToString(";") { it.csv() }
        return "\uFEFF" + (listOf(separatorHint, headerLine) + rows).joinToString("\n")
    }

    private fun photoEntryName(index: Int, poster: Poster, photosDir: File): String? {
        val originalName = poster.localPhotoFileName ?: return null
        val source = File(photosDir, originalName)
        if (!source.isFile) return null
        val rawExtension = source.extension.lowercase(Locale.ROOT)
        val extension = rawExtension.takeIf { it.matches(Regex("[a-z0-9]{1,8}")) } ?: "jpg"
        val number = (index + 1).toString().padStart(3, '0')
        return "fotos/plakat_${number}.${extension}"
    }

    private fun PosterType.toHumanText(): String = when (this) {
        PosterType.LAMP_POST -> "Laternenmast"
        PosterType.FENCE -> "Zaun"
        PosterType.BANNER -> "Banner"
        PosterType.TRIANGLE_STAND -> "Dreieckständer"
        PosterType.LARGE_FORMAT -> "Großformat / Großfläche"
        PosterType.OTHER -> "Sonstiges"
    }

    private fun PosterStatus.toHumanText(): String = when (this) {
        PosterStatus.HANGING -> "Hängt"
        PosterStatus.CHECKED -> "Kontrolliert"
        PosterStatus.DAMAGED -> "Beschädigt"
        PosterStatus.MISSING -> "Fehlt"
        PosterStatus.REPLACED -> "Ersetzt"
        PosterStatus.REMOVED -> "Entfernt"
    }

    private fun String.csv(): String = "\"" + replace("\"", "\"\"") + "\""
}

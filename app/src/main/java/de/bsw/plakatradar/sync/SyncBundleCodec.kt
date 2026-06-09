package de.bsw.plakatradar.sync

import android.content.Context
import android.net.Uri
import de.bsw.plakatradar.core.LocalTeamState
import de.bsw.plakatradar.core.SyncMerge
import de.bsw.plakatradar.core.SyncSnapshot
import de.bsw.plakatradar.data.LocalRepository
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class SyncBundleCodec(private val context: Context, private val repo: LocalRepository) {
    fun createBundle(snapshot: SyncSnapshot, teamSecret: String): File {
        val outFile = File(repo.syncDir, "plakatradar-sync-${System.currentTimeMillis()}.prsync")
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, keyFromTeamSecret(teamSecret), GCMParameterSpec(128, iv))
        cipher.updateAAD(MAGIC)

        FileOutputStream(outFile).use { rawOut ->
            rawOut.write(MAGIC)
            rawOut.write(iv)
            CipherOutputStream(rawOut, cipher).use { encryptedOut ->
                writePlainZip(encryptedOut, snapshot)
            }
        }
        return outFile
    }


    private fun writePlainZip(output: OutputStream, snapshot: SyncSnapshot) {
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("snapshot.json"))
            zip.write(repo.snapshotToJson(snapshot).toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            snapshot.posters.mapNotNull { it.localPhotoFileName }.distinct().forEach { name ->
                if (!isSafeFileName(name)) return@forEach
                val photo = File(repo.photosDir, name)
                if (photo.exists() && photo.isFile && photo.length() <= MAX_SINGLE_PHOTO_BYTES) {
                    zip.putNextEntry(ZipEntry("photos/$name"))
                    FileInputStream(photo).use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }
    }

    /**
     * Sicherer Import:
     * 1. Paket mit Team-Schlüssel entschlüsseln, falls es ein modernes .prsync-Paket ist.
     * 2. Erst snapshot.json lesen.
     * 3. Team-ID und Team-Schlüssel-Hash prüfen.
     * 4. Erst danach Fotos entpacken.
     * 5. Nur Fotos entpacken, die im Snapshot wirklich referenziert werden.
     * 6. Größenlimits gegen ZIP-Bomben und Speicherflutung.
     */
    fun importVerifiedBundle(bundle: File, local: LocalTeamState): SyncSnapshot {
        require(bundle.length() <= MAX_BUNDLE_BYTES) { "Sync-Paket ist zu groß." }
        val localSecret = local.teamSecret ?: error("Kein Team-Schlüssel auf diesem Gerät.")

        val plainBundle = openBundleForReading(bundle, localSecret)
        try {
            val snapshot = readSnapshotOnly(plainBundle)
            require(SyncMerge.verify(snapshot, local)) { "Fremdes oder ungültiges Team-Paket." }

            val allowedPhotos = snapshot.posters
                .mapNotNull { it.localPhotoFileName }
                .filter { isSafeFileName(it) }
                .toSet()

            var extractedTotal = 0L
            ZipInputStream(FileInputStream(plainBundle)).use { zip ->
                generateSequence { zip.nextEntry }.forEach { entry ->
                    try {
                        if (entry.name.startsWith("photos/") && !entry.isDirectory) {
                            val safeName = entry.name.substringAfterLast('/')
                            if (!allowedPhotos.contains(safeName)) return@forEach

                            val target = safePhotoTarget(safeName)
                            var written = 0L
                            FileOutputStream(target).use { output ->
                                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                                while (true) {
                                    val read = zip.read(buffer)
                                    if (read <= 0) break
                                    written += read
                                    extractedTotal += read
                                    require(written <= MAX_SINGLE_PHOTO_BYTES) { "Ein Foto im Sync-Paket ist zu groß." }
                                    require(extractedTotal <= MAX_TOTAL_PHOTO_BYTES) { "Zu viele Fotos im Sync-Paket." }
                                    output.write(buffer, 0, read)
                                }
                            }
                        }
                    } finally {
                        zip.closeEntry()
                    }
                }
            }

            return snapshot
        } finally {
            if (plainBundle != bundle) plainBundle.delete()
        }
    }


    private fun openBundleForReading(bundle: File, teamSecret: String): File {
        if (!isEncryptedPackage(bundle)) {
            // Altkompatibilität: alte Klartext-ZIP-Pakete können noch importiert werden,
            // werden aber erst nach Team-Prüfung ausgewertet.
            return bundle
        }

        val tempPlainZip = File(repo.syncDir, "decrypted-${System.currentTimeMillis()}.zip")
        FileInputStream(bundle).use { input ->
            val magic = input.readNBytesCompat(MAGIC.size)
            require(magic.contentEquals(MAGIC)) { "Ungültiges Sync-Paket." }

            val iv = input.readNBytesCompat(12)
            require(iv.size == 12) { "Sync-Paket ist beschädigt." }

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, keyFromTeamSecret(teamSecret), GCMParameterSpec(128, iv))
            cipher.updateAAD(MAGIC)

            CipherInputStream(input, cipher).use { decrypted ->
                FileOutputStream(tempPlainZip).use { out ->
                    decrypted.copyToWithLimit(out, MAX_BUNDLE_BYTES)
                }
            }
        }
        return tempPlainZip
    }

    private fun isEncryptedPackage(file: File): Boolean =
        FileInputStream(file).use { input ->
            input.readNBytesCompat(MAGIC.size).contentEquals(MAGIC)
        }

    private fun readSnapshotOnly(bundle: File): SyncSnapshot {
        var rawSnapshot: String? = null
        ZipInputStream(FileInputStream(bundle)).use { zip ->
            generateSequence { zip.nextEntry }.forEach { entry ->
                try {
                    if (entry.name == "snapshot.json") {
                        rawSnapshot = readLimitedText(zip, MAX_SNAPSHOT_BYTES)
                    }
                } finally {
                    zip.closeEntry()
                }
            }
        }
        return repo.snapshotFromJson(rawSnapshot ?: error("Sync-Paket enthält keine snapshot.json"))
    }

    private fun readLimitedText(zip: ZipInputStream, maxBytes: Int): String {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0
        while (true) {
            val read = zip.read(buffer)
            if (read <= 0) break
            total += read
            require(total <= maxBytes) { "snapshot.json ist zu groß." }
            output.write(buffer, 0, read)
        }
        return output.toByteArray().toString(Charsets.UTF_8)
    }

    private fun safePhotoTarget(name: String): File {
        require(isSafeFileName(name)) { "Unsicherer Dateiname im Sync-Paket." }
        val target = File(repo.photosDir, name)
        val photosRoot = repo.photosDir.canonicalFile
        val canonicalTarget = target.canonicalFile
        require(canonicalTarget.path.startsWith(photosRoot.path + File.separator)) {
            "Unsicherer Fotopfad im Sync-Paket."
        }
        return canonicalTarget
    }

    private fun isSafeFileName(name: String): Boolean =
        name.matches(Regex("[a-zA-Z0-9._-]{1,120}"))

    fun copyIncomingUriToBundle(uri: Uri): File {
        val out = File(repo.syncDir, "incoming-${System.currentTimeMillis()}.prsync")
        var total = 0L
        context.contentResolver.openInputStream(uri).use { input ->
            FileOutputStream(out).use { output ->
                val stream = input ?: error("Datei konnte nicht gelesen werden")
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = stream.read(buffer)
                    if (read <= 0) break
                    total += read
                    require(total <= MAX_BUNDLE_BYTES) { "Sync-Paket ist zu groß." }
                    output.write(buffer, 0, read)
                }
            }
        }
        return out
    }

    private fun keyFromTeamSecret(teamSecret: String): SecretKeySpec {
        val keyBytes = MessageDigest.getInstance("SHA-256").digest(teamSecret.toByteArray(Charsets.UTF_8))
        return SecretKeySpec(keyBytes, "AES")
    }

    private fun InputStream.readNBytesCompat(length: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(length)
        while (out.size() < length) {
            val read = read(buffer, 0, minOf(buffer.size, length - out.size()))
            if (read <= 0) break
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    private fun InputStream.copyToWithLimit(output: OutputStream, maxBytes: Long) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            val read = read(buffer)
            if (read <= 0) break
            total += read
            require(total <= maxBytes) { "Entschlüsseltes Sync-Paket ist zu groß." }
            output.write(buffer, 0, read)
        }
    }

    companion object {
        private val MAGIC = "PRSYNC2\n".toByteArray(Charsets.UTF_8)
        private const val MAX_SNAPSHOT_BYTES = 2 * 1024 * 1024
        private const val MAX_SINGLE_PHOTO_BYTES = 8L * 1024L * 1024L
        private const val MAX_TOTAL_PHOTO_BYTES = 250L * 1024L * 1024L
        private const val MAX_BUNDLE_BYTES = 300L * 1024L * 1024L
    }
}

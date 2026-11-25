package com.sahyog.server.storage

import com.google.cloud.storage.BlobId
import com.google.cloud.storage.BlobInfo
import com.google.cloud.storage.StorageOptions
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.UUID

/**
 * Private storage for identity documents. Files are addressed by an opaque random key and are
 * only ever read back through the admin API; they are never served from a public URL.
 *
 * Local development keeps them in a folder; production swaps in a private Cloud Storage bucket
 * (encrypted at rest, access limited to the server's service account).
 */
interface DocumentStorage {
    /** Stores [bytes] and returns the key to read them back with. */
    fun save(bytes: ByteArray): String

    fun load(key: String): ByteArray?
}

/** Development and tests: a folder on disk. */
@Component
@Profile("!prod")
class LocalDocumentStorage(
    @Value("\${sahyog.storage.documents-dir}") directory: String,
) : DocumentStorage {

    private val root: Path = Path.of(directory).toAbsolutePath().normalize()

    override fun save(bytes: ByteArray): String {
        Files.createDirectories(root)
        val key = UUID.randomUUID().toString()
        Files.write(root.resolve(key), bytes, StandardOpenOption.CREATE_NEW)
        return key
    }

    override fun load(key: String): ByteArray? {
        // Keys are UUIDs we generated; anything else (e.g. "../") is refused outright.
        val id = runCatching { UUID.fromString(key) }.getOrNull() ?: return null
        val file = root.resolve(id.toString())
        return if (Files.isRegularFile(file)) Files.readAllBytes(file) else null
    }
}

/**
 * Production: a private Cloud Storage bucket (encrypted at rest by Google; public access
 * prevented on the bucket). The server's service account is the only reader and writer.
 */
@Component
@Profile("prod")
class CloudDocumentStorage(@Value("\${sahyog.storage.bucket}") private val bucket: String) : DocumentStorage {
    private val storage = StorageOptions.getDefaultInstance().service

    override fun save(bytes: ByteArray): String {
        val key = UUID.randomUUID().toString()
        storage.create(BlobInfo.newBuilder(BlobId.of(bucket, key)).build(), bytes)
        return key
    }

    override fun load(key: String): ByteArray? {
        val id = runCatching { UUID.fromString(key) }.getOrNull() ?: return null
        return storage.get(BlobId.of(bucket, id.toString()))?.getContent()
    }
}

/** Recognises the image formats phones produce by their first bytes, not by what the client claims. */
object ImageSniffer {
    fun contentType(bytes: ByteArray): String? = when {
        bytes.startsWith(0xFF, 0xD8, 0xFF) -> "image/jpeg"
        bytes.startsWith(0x89, 0x50, 0x4E, 0x47) -> "image/png"
        bytes.size > 12 && bytes.ascii(0, 4) == "RIFF" && bytes.ascii(8, 12) == "WEBP" -> "image/webp"
        // HEIF/HEIC (iPhone and many Androids): "ftyp" box at offset 4 with a heic/heif/mif1 brand.
        bytes.size > 12 && bytes.ascii(4, 8) == "ftyp" && bytes.ascii(8, 12) in HEIF_BRANDS -> "image/heic"
        else -> null
    }

    private val HEIF_BRANDS = setOf("heic", "heix", "heif", "mif1", "msf1", "hevc")

    private fun ByteArray.startsWith(vararg prefix: Int) =
        size >= prefix.size && prefix.indices.all { this[it] == prefix[it].toByte() }

    private fun ByteArray.ascii(from: Int, to: Int) = String(copyOfRange(from, to), Charsets.US_ASCII)
}

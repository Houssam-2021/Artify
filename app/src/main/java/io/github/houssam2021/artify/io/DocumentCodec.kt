package io.github.houssam2021.artify.io

import io.github.houssam2021.artify.model.AnimSettings
import io.github.houssam2021.artify.model.ArtDocument
import io.github.houssam2021.artify.model.BrushSpec
import io.github.houssam2021.artify.model.BrushTip
import io.github.houssam2021.artify.model.Camera
import io.github.houssam2021.artify.model.Layer
import io.github.houssam2021.artify.model.LayerBlend
import io.github.houssam2021.artify.model.Stroke
import io.github.houssam2021.artify.model.SymmetryMode
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.IdentityHashMap
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

class InvalidDocumentException(message: String, cause: Throwable? = null) : IOException(message, cause)

/** Lightweight info read from the start of a file, for the gallery. */
data class DocumentHeader(val name: String, val created: Long, val modified: Long)

/**
 * The `.artify` file format: a gzip-compressed, versioned binary stream.
 *
 * Files may come from untrusted sources (imports), so the decoder treats every
 * value as hostile: magic/version checks, hard limits on every count, a cap on
 * the decompressed size (zip-bomb protection), finite-number checks, and clamping
 * of all ranges. Any violation throws [InvalidDocumentException].
 */
object DocumentCodec {
    private const val MAGIC = 0x41525446 // "ARTF"
    const val VERSION: Short = 1

    const val MAX_DECOMPRESSED_BYTES = 512L * 1024 * 1024
    const val MAX_BRUSHES = 4096
    const val MAX_STROKES_PER_LAYER = 1_000_000
    const val MAX_POINTS_PER_STROKE = 200_000
    const val MAX_TOTAL_POINTS = 20_000_000L
    const val MAX_NAME_LENGTH = 100

    fun encode(doc: ArtDocument, out: OutputStream) {
        val gz = GZIPOutputStream(BufferedOutputStream(out, 1 shl 16), 1 shl 16)
        val d = DataOutputStream(gz)
        writeHeader(d, doc)
        d.writeInt(doc.background)
        d.writeDouble(doc.camera.cx); d.writeDouble(doc.camera.cy)
        d.writeDouble(doc.camera.scale); d.writeDouble(doc.camera.rotation)
        d.writeLong(doc.nextSeq); d.writeLong(doc.nextLayerId); d.writeLong(doc.activeLayerId)
        d.writeBoolean(doc.anim.enabled); d.writeInt(doc.anim.fps)
        d.writeInt(doc.anim.onionFrames); d.writeFloat(doc.anim.onionOpacity)
        d.writeInt(doc.symmetry.ordinal); d.writeDouble(doc.symX); d.writeDouble(doc.symY)

        // Brush table (deduplicated by identity, then by value).
        val brushIndex = IdentityHashMap<BrushSpec, Int>()
        val byValue = HashMap<BrushSpec, Int>()
        val brushes = ArrayList<BrushSpec>()
        for (l in doc.layers) for (s in l.strokes) {
            if (brushIndex.containsKey(s.brush)) continue
            val idx = byValue.getOrPut(s.brush) { brushes.add(s.brush); brushes.size - 1 }
            brushIndex[s.brush] = idx
        }
        d.writeInt(brushes.size)
        for (b in brushes) writeBrush(d, b)

        d.writeInt(doc.layers.size)
        for (l in doc.layers) {
            d.writeLong(l.id); d.writeUTF(l.name.take(MAX_NAME_LENGTH))
            d.writeBoolean(l.visible); d.writeBoolean(l.locked); d.writeBoolean(l.alphaLock)
            d.writeFloat(l.opacity); d.writeInt(l.blend.ordinal)
            d.writeInt(l.strokes.size)
            for (s in l.strokes) {
                d.writeInt(brushIndex[s.brush]!!)
                d.writeInt(s.color); d.writeFloat(s.size); d.writeFloat(s.opacity)
                d.writeBoolean(s.eraser); d.writeInt(s.seed); d.writeLong(s.seq)
                d.writeDouble(s.ox); d.writeDouble(s.oy)
                d.writeInt(s.count)
                for (i in 0 until s.count) { d.writeFloat(s.xs[i]); d.writeFloat(s.ys[i]); d.writeFloat(s.ps[i]) }
            }
        }
        d.flush()
        gz.finish()
        gz.flush()
    }

    private fun writeHeader(d: DataOutputStream, doc: ArtDocument) {
        d.writeInt(MAGIC); d.writeShort(VERSION.toInt())
        d.writeUTF(doc.name.take(MAX_NAME_LENGTH)); d.writeLong(doc.created); d.writeLong(doc.modified)
    }

    private fun writeBrush(d: DataOutputStream, b: BrushSpec) {
        d.writeUTF(b.id.take(BrushSpec.MAX_NAME)); d.writeUTF(b.name.take(BrushSpec.MAX_NAME))
        d.writeUTF(b.category.take(BrushSpec.MAX_NAME)); d.writeInt(b.tip.ordinal)
        for (f in floatArrayOf(b.spacing, b.flow, b.pressureSize, b.pressureFlow, b.sizeJitter, b.flowJitter,
            b.scatter, b.rotationJitter, b.angleDeg, b.roundness, b.taper, b.minSize, b.maxSize, b.defaultSize,
            b.defaultOpacity, b.streamline)) d.writeFloat(f)
        d.writeBoolean(b.followStroke)
    }

    fun readHeader(input: InputStream): DocumentHeader {
        val d = DataInputStream(LimitedInputStream(GZIPInputStream(BufferedInputStream(input)), 1L shl 20))
        return guard { readHeaderFields(d) }
    }

    private fun readHeaderFields(d: DataInputStream): DocumentHeader {
        if (d.readInt() != MAGIC) throw InvalidDocumentException("Not an Artify file")
        val v = d.readShort()
        if (v < 1 || v > VERSION) throw InvalidDocumentException("Unsupported file version $v")
        val name = d.readUTF().take(MAX_NAME_LENGTH).filter { it >= ' ' }.ifBlank { "Untitled" }
        return DocumentHeader(name, d.readLong(), d.readLong())
    }

    /** Decodes a document; [newId] replaces the stored id (ids come from the file name, never the file). */
    fun decode(input: InputStream, newId: String): ArtDocument = guard {
        val d = DataInputStream(LimitedInputStream(GZIPInputStream(BufferedInputStream(input, 1 shl 16), 1 shl 16), MAX_DECOMPRESSED_BYTES))
        val header = readHeaderFields(d)
        val background = d.readInt()
        val camera = Camera(finiteD(d.readDouble()), finiteD(d.readDouble()), finiteD(d.readDouble()), finiteD(d.readDouble())).sanitized()
        val nextSeq = d.readLong().coerceAtLeast(1)
        val nextLayerId = d.readLong().coerceAtLeast(1)
        val activeLayerId = d.readLong()
        val anim = AnimSettings(
            enabled = d.readBoolean(),
            fps = d.readInt().coerceIn(1, 60),
            onionFrames = d.readInt().coerceIn(0, 5),
            onionOpacity = finiteF(d.readFloat()).coerceIn(0f, 1f),
        )
        val sym = SymmetryMode.entries.getOrElse(d.readInt()) { SymmetryMode.OFF }
        val symX = finiteD(d.readDouble()); val symY = finiteD(d.readDouble())

        val brushCount = d.readInt()
        if (brushCount < 0 || brushCount > MAX_BRUSHES) throw InvalidDocumentException("Too many brushes")
        val brushes = List(brushCount) { readBrush(d) }

        val layerCount = d.readInt()
        if (layerCount < 1 || layerCount > ArtDocument.MAX_LAYERS) throw InvalidDocumentException("Bad layer count")
        var totalPoints = 0L
        var maxSeqSeen = 0L
        var maxLayerId = 0L
        val ids = HashSet<Long>()
        val layers = ArrayList<Layer>(layerCount)
        repeat(layerCount) {
            var id = d.readLong()
            if (id <= 0 || !ids.add(id)) { id = (ids.maxOrNull() ?: 0L) + 1; ids.add(id) }
            maxLayerId = maxOf(maxLayerId, id)
            val name = d.readUTF().take(MAX_NAME_LENGTH).filter { it >= ' ' }.ifBlank { "Layer" }
            val visible = d.readBoolean(); val locked = d.readBoolean(); val alphaLock = d.readBoolean()
            val opacity = finiteF(d.readFloat()).coerceIn(0f, 1f)
            val blend = LayerBlend.entries.getOrElse(d.readInt()) { LayerBlend.NORMAL }
            val strokeCount = d.readInt()
            if (strokeCount < 0 || strokeCount > MAX_STROKES_PER_LAYER) throw InvalidDocumentException("Too many strokes")
            val strokes = ArrayList<Stroke>(minOf(strokeCount, 4096))
            repeat(strokeCount) {
                val bi = d.readInt()
                if (bi !in brushes.indices) throw InvalidDocumentException("Bad brush reference")
                val color = d.readInt()
                val size = finiteF(d.readFloat())
                if (size <= 0f) throw InvalidDocumentException("Bad stroke size")
                val sOpacity = finiteF(d.readFloat()).coerceIn(0f, 1f)
                val eraser = d.readBoolean(); val seed = d.readInt(); val seq = d.readLong()
                val ox = finiteD(d.readDouble()); val oy = finiteD(d.readDouble())
                if (kotlin.math.abs(ox) > Camera.MAX_COORD * 10 || kotlin.math.abs(oy) > Camera.MAX_COORD * 10)
                    throw InvalidDocumentException("Coordinates out of range")
                val n = d.readInt()
                if (n < 1 || n > MAX_POINTS_PER_STROKE) throw InvalidDocumentException("Bad point count")
                totalPoints += n
                if (totalPoints > MAX_TOTAL_POINTS) throw InvalidDocumentException("Document too large")
                val xs = FloatArray(n); val ys = FloatArray(n); val ps = FloatArray(n)
                for (i in 0 until n) {
                    xs[i] = finiteF(d.readFloat()); ys[i] = finiteF(d.readFloat())
                    ps[i] = finiteF(d.readFloat()).coerceIn(0f, 1f)
                }
                maxSeqSeen = maxOf(maxSeqSeen, seq)
                strokes += Stroke(brushes[bi], color, size, sOpacity, eraser, seed, seq, ox, oy, xs, ys, ps)
            }
            layers += Layer(id, name, strokes, visible, locked, alphaLock, opacity, blend)
        }
        val active = if (layers.any { it.id == activeLayerId }) activeLayerId else layers.last().id
        ArtDocument(
            id = newId, name = header.name, created = header.created, modified = header.modified,
            background = background or (0xFF shl 24), layers = layers, activeLayerId = active, camera = camera,
            nextSeq = maxOf(nextSeq, maxSeqSeen + 1), nextLayerId = maxOf(nextLayerId, maxLayerId + 1),
            anim = anim, symmetry = sym, symX = symX, symY = symY,
        )
    }

    private fun readBrush(d: DataInputStream): BrushSpec {
        val id = d.readUTF(); val name = d.readUTF(); val cat = d.readUTF()
        val tip = BrushTip.entries.getOrElse(d.readInt()) { BrushTip.ROUND }
        val f = FloatArray(16) { finiteF(d.readFloat()) }
        val follow = d.readBoolean()
        return BrushSpec(
            id = id, name = name, category = cat, tip = tip,
            spacing = f[0], flow = f[1], pressureSize = f[2], pressureFlow = f[3], sizeJitter = f[4],
            flowJitter = f[5], scatter = f[6], rotationJitter = f[7], angleDeg = f[8], roundness = f[9],
            taper = f[10], minSize = f[11], maxSize = f[12], defaultSize = f[13], defaultOpacity = f[14],
            streamline = f[15], followStroke = follow,
        ).sanitized()
    }

    private fun finiteF(v: Float): Float = if (v.isFinite()) v else throw InvalidDocumentException("Non-finite value")
    private fun finiteD(v: Double): Double = if (v.isFinite()) v else throw InvalidDocumentException("Non-finite value")

    private inline fun <T> guard(block: () -> T): T = try {
        block()
    } catch (e: InvalidDocumentException) {
        throw e
    } catch (e: IOException) {
        throw InvalidDocumentException("Corrupt or truncated file", e)
    } catch (e: RuntimeException) {
        throw InvalidDocumentException("Corrupt file", e)
    } catch (e: OutOfMemoryError) {
        throw InvalidDocumentException("File too large for this device", null)
    }
}

/** Fails once more than [limit] bytes have been read (decompression-bomb guard). */
class LimitedInputStream(input: InputStream, private val limit: Long) : FilterInputStream(input) {
    private var count = 0L

    override fun read(): Int {
        val r = super.read()
        if (r >= 0) bump(1)
        return r
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        val r = super.read(b, off, len)
        if (r > 0) bump(r.toLong())
        return r
    }

    override fun skip(n: Long): Long {
        val r = super.skip(n)
        if (r > 0) bump(r)
        return r
    }

    private fun bump(n: Long) {
        count += n
        if (count > limit) throw InvalidDocumentException("File exceeds size limit")
    }
}

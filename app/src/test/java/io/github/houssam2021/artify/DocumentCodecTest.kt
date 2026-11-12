package io.github.houssam2021.artify

import io.github.houssam2021.artify.engine.StampGenerator
import io.github.houssam2021.artify.engine.StampSink
import io.github.houssam2021.artify.io.DocumentCodec
import io.github.houssam2021.artify.io.InvalidDocumentException
import io.github.houssam2021.artify.model.ArtDocument
import io.github.houssam2021.artify.model.BrushLibrary
import io.github.houssam2021.artify.model.Layer
import io.github.houssam2021.artify.model.LayerBlend
import io.github.houssam2021.artify.model.Stroke
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.util.zip.GZIPOutputStream

class DocumentCodecTest {
    private val id = "00000000-0000-4000-8000-000000000000"

    private fun stroke(seq: Long, n: Int = 20) = Stroke(
        BrushLibrary.defaultPaint, 0xFF336699.toInt(), 4f, 0.8f, false, 7, seq, 1e6, -2e6,
        FloatArray(n) { it.toFloat() }, FloatArray(n) { (it * 2).toFloat() }, FloatArray(n) { 0.5f },
    )

    private fun sampleDoc(): ArtDocument {
        val d = ArtDocument.new(id, "Test", 1000L)
        val l2 = Layer(2, "Ink", listOf(stroke(1), stroke(2)), opacity = 0.5f, blend = LayerBlend.MULTIPLY)
        return d.copy(layers = d.layers + l2, activeLayerId = 2, nextLayerId = 3, nextSeq = 3)
    }

    private fun encode(doc: ArtDocument) = ByteArrayOutputStream().also { DocumentCodec.encode(doc, it) }.toByteArray()

    @Test
    fun roundTripPreservesContent() {
        val doc = sampleDoc()
        val back = DocumentCodec.decode(ByteArrayInputStream(encode(doc)), id)
        assertEquals(doc.name, back.name)
        assertEquals(2, back.layers.size)
        val l = back.layers[1]
        assertEquals(LayerBlend.MULTIPLY, l.blend)
        assertEquals(0.5f, l.opacity)
        assertEquals(2, l.strokes.size)
        assertEquals(1e6, l.strokes[0].ox, 0.0)
        assertEquals(19f, l.strokes[0].xs[19])
        assertEquals(BrushLibrary.defaultPaint, l.strokes[0].brush)
        assertEquals(2L, back.activeLayerId)
    }

    @Test
    fun idComesFromCallerNotFile() {
        val other = "11111111-1111-4111-8111-111111111111"
        val back = DocumentCodec.decode(ByteArrayInputStream(encode(sampleDoc())), other)
        assertEquals(other, back.id)
    }

    @Test
    fun rejectsGarbage() {
        expectInvalid(ByteArray(64) { it.toByte() })
    }

    @Test
    fun rejectsTruncatedFile() {
        val bytes = encode(sampleDoc())
        expectInvalid(bytes.copyOf(bytes.size / 2))
    }

    @Test
    fun rejectsWrongMagic() {
        expectInvalid(gzip { it.writeInt(0x12345678); it.writeShort(1) })
    }

    @Test
    fun rejectsHugeCounts() {
        // Valid header, then an absurd brush count: must fail fast without allocating.
        expectInvalid(gzip { d ->
            d.writeInt(0x41525446); d.writeShort(1); d.writeUTF("x"); d.writeLong(0); d.writeLong(0)
            d.writeInt(-1)
            repeat(4) { d.writeDouble(1.0) }
            d.writeLong(1); d.writeLong(1); d.writeLong(1)
            d.writeBoolean(false); d.writeInt(8); d.writeInt(1); d.writeFloat(0.3f)
            d.writeInt(0); d.writeDouble(0.0); d.writeDouble(0.0)
            d.writeInt(Int.MAX_VALUE)
        })
    }

    @Test
    fun rejectsNonFiniteNumbers() {
        val doc = sampleDoc()
        val bad = doc.copy(layers = listOf(Layer(1, "L", listOf(Stroke(BrushLibrary.defaultPaint, 0, 1f, 1f, false, 0, 1,
            Double.NaN, 0.0, floatArrayOf(0f), floatArrayOf(0f), floatArrayOf(1f))))), activeLayerId = 1)
        expectInvalid(encode(bad))
    }

    @Test
    fun stampsAreDeterministic() {
        fun run(): List<Float> {
            val out = ArrayList<Float>()
            val s = stroke(1, 50)
            StampGenerator.generate(BrushLibrary.byId("splatter")!!, 10f, 99, s.xs, s.ys, s.ps, s.count, 0.1,
                StampSink { x, y, d, a, ang -> out += x; out += y; out += d; out += a; out += ang })
            return out
        }
        val a = run()
        assertTrue(a.isNotEmpty())
        assertEquals(a, run())
    }

    @Test
    fun stampCountIsBounded() {
        var count = 0
        val n = 1000
        StampGenerator.generate(BrushLibrary.defaultPaint, 1e-9f, 1, FloatArray(n) { it * 1000f }, FloatArray(n), FloatArray(n) { 1f }, n, 1e-12,
            StampSink { _, _, _, _, _ -> count++ })
        assertTrue(count <= StampGenerator.MAX_STAMPS)
    }

    private fun gzip(block: (DataOutputStream) -> Unit): ByteArray {
        val bos = ByteArrayOutputStream()
        GZIPOutputStream(bos).use { gz -> DataOutputStream(gz).let { block(it); it.flush() } }
        return bos.toByteArray()
    }

    private fun expectInvalid(bytes: ByteArray) {
        try {
            DocumentCodec.decode(ByteArrayInputStream(bytes), id)
            fail("expected InvalidDocumentException")
        } catch (e: InvalidDocumentException) {
            // expected
        }
    }
}

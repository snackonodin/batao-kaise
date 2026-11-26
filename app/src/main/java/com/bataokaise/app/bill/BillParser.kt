package com.bataokaise.app.bill

/** One OCR line with its box in image pixels (left, top, right, bottom). */
data class OcrLine(val text: String, val l: Int, val t: Int, val r: Int, val b: Int) {
    val cx get() = (l + r) / 2
    val cy get() = (t + b) / 2
}

enum class FieldKind(val label: String, val color: Long) {
    ACCOUNT("Account no.", 0xFF1E5AA8), AMOUNT("Rakam", 0xFF1F5E3B), DUE("Aakhri tareekh", 0xFFC0661A)
}

/** A found field. [value] null = unreadable: shown as a grey "Saaf nahi" box, never guessed. */
data class BillField(val kind: FieldKind, val value: String?, val box: IntArray?) {
    val readable get() = value != null
}

data class BillResult(val fields: Map<FieldKind, BillField>, val width: Int, val height: Int) {
    operator fun get(k: FieldKind) = fields[k]
    val account get() = fields[FieldKind.ACCOUNT]?.value
    val amount get() = fields[FieldKind.AMOUNT]?.value?.replace(",", "")?.toDoubleOrNull()?.toLong()
}

/**
 * Template rules for Indian electricity bills (Hindi + English labels).
 * A value must sit on the label's line, or just right of / below it. Otherwise: "Saaf nahi".
 */
object BillParser {
    private val accountLabel = Regex("(account|a/c|consumer|ca\\s*no|k\\.?\\s*no|service\\s*no|खाता|उपभोक्ता|कंज्यूमर|उपभोक्ता\\s*संख्या)", RegexOption.IGNORE_CASE)
    private val amountLabel = Regex("(amount\\s*payable|net\\s*payable|payable|total\\s*amount|bill\\s*amount|amount\\s*due|देय\\s*राशि|कुल\\s*राशि|राशि|रकम)", RegexOption.IGNORE_CASE)
    private val dueLabel = Regex("(due\\s*date|last\\s*date|pay\\s*by|अंतिम\\s*तिथि|देय\\s*तिथि|आखिरी\\s*तारीख|अंतिम\\s*तारीख)", RegexOption.IGNORE_CASE)

    private val accountValue = Regex("(?<![\\d])(\\d[\\d ]{7,18}\\d)(?![\\d])")
    private val amountValue = Regex("(?:₹|rs\\.?|inr|रु\\.?)?\\s*(\\d{1,3}(?:,\\d{2,3})+|\\d{2,7})(?:\\.\\d{1,2})?", RegexOption.IGNORE_CASE)
    private val dateValue = Regex("(\\d{1,2})[/.\\-](\\d{1,2})[/.\\-](\\d{2,4})|(\\d{1,2})\\s*(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*[ ,\\-]*(\\d{2,4})", RegexOption.IGNORE_CASE)

    fun parse(lines: List<OcrLine>, width: Int, height: Int): BillResult {
        val f = mutableMapOf<FieldKind, BillField>()
        f[FieldKind.ACCOUNT] = find(lines, FieldKind.ACCOUNT, accountLabel) { s ->
            accountValue.find(s)?.groupValues?.get(1)?.filter(Char::isDigit)?.takeIf { it.length in 8..18 }
        }
        f[FieldKind.AMOUNT] = find(lines, FieldKind.AMOUNT, amountLabel) { s ->
            // Skip things that look like dates or long account numbers.
            if (dateValue.containsMatchIn(s)) null
            else amountValue.findAll(s).map { it.groupValues[1] }.firstOrNull { v -> v.filter(Char::isDigit).length in 2..7 }
        }
        f[FieldKind.DUE] = find(lines, FieldKind.DUE, dueLabel) { s -> dateValue.find(s)?.value?.trim() }
        return BillResult(f, width, height)
    }

    private fun find(lines: List<OcrLine>, kind: FieldKind, label: Regex, value: (String) -> String?): BillField {
        val labelLine = lines.firstOrNull { label.containsMatchIn(it.text) }
            ?: return BillField(kind, null, null)
        // 1) Same line, after the label.
        val after = labelLine.text.substring(label.find(labelLine.text)!!.range.last + 1)
        value(after)?.let { return BillField(kind, it, labelLine.box()) }
        // 2) Nearest line to the right on the same row, then directly below (within ~3 line heights).
        val h = (labelLine.b - labelLine.t).coerceAtLeast(10)
        val candidates = lines.filter { it !== labelLine }.filter { o ->
            val sameRow = kotlin.math.abs(o.cy - labelLine.cy) < h && o.l >= labelLine.l
            val below = o.t >= labelLine.t && o.t - labelLine.b < h * 3 && o.r > labelLine.l - h * 2 && o.l < labelLine.r + h * 6
            sameRow || below
        }.sortedBy { o -> kotlin.math.abs(o.cy - labelLine.cy) * 3 + kotlin.math.abs(o.l - labelLine.r) }
        for (c in candidates) value(c.text)?.let { return BillField(kind, it, union(labelLine, c)) }
        // Label seen, value unreadable: grey box over the label.
        return BillField(kind, null, labelLine.box())
    }

    private fun OcrLine.box() = intArrayOf(l, t, r, b)
    private fun union(a: OcrLine, b: OcrLine) = intArrayOf(minOf(a.l, b.l), minOf(a.t, b.t), maxOf(a.r, b.r), maxOf(a.b, b.b))

    private val months = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")

    /** "15/12/2026" -> "15 December 2026" for speech. */
    fun spokenDate(s: String): String {
        val m = Regex("(\\d{1,2})[/.\\-](\\d{1,2})[/.\\-](\\d{2,4})").find(s) ?: return s
        val (d, mo, y) = m.destructured
        val name = months.getOrNull(mo.toInt() - 1) ?: return s
        val year = if (y.length == 2) "20$y" else y
        return "${d.toInt()} $name $year"
    }

    /** Where on the page, in Hinglish: "Upar daayein", "Beech mein", "Neeche baayein". */
    fun position(box: IntArray, w: Int, h: Int): String {
        val cx = (box[0] + box[2]) / 2.0 / w
        val cy = (box[1] + box[3]) / 2.0 / h
        val v = when { cy < 0.36 -> "Upar"; cy > 0.66 -> "Neeche"; else -> "Beech mein" }
        val hz = when { cx < 0.38 -> "baayein"; cx > 0.62 -> "daayein"; else -> "" }
        return if (v == "Beech mein" && hz.isEmpty()) "Beech mein" else listOf(v, hz).filter { it.isNotEmpty() }.joinToString(" ").replace("Beech mein ", "Beech mein, ")
    }
}

/*
 * Copyright (c) 2026 SubstratumReborn
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */
package projekt.substratum.reborn.themes

import org.w3c.dom.Element
import java.io.StringReader
import java.util.Locale
import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory
import org.xml.sax.InputSource

/**
 * What a classic theme's own overlays make of the preview roles, read from the XML in its
 * assets/overlays/<package>/ folders. Pure (javax.xml.parsers works on the JVM and on Android),
 * so OverlayPreviewTest runs it on fixture overlays.
 *
 * Where a colour is looked up, first found wins:
 * 1. `type3-common`, then the default type 3 folder, then `res`. The default type 3 folder is
 *    the one a build with the Overlays tab's first choice uses: none when the overlay has `res`
 *    (the default variant is built from it), else its first `type3_*` folder by name.
 * 2. Inside each folder, `values-night*` before `values` (the preview is dark mode).
 * 3. Every XML file of a values folder counts, so the default type 1a file (`values/type1a.xml`)
 *    is merged with the rest.
 *
 * `@color/x` resolves inside the same overlay, `@android:color/x` and `@*android:color/x` through
 * the theme's `android` overlay. Anything that does not end in a colour (a framework colour the
 * theme does not override, an attribute, @null) stays unresolved, so the role keeps its stock
 * colour.
 */
object OverlayPreview {

    /** The overlay of the framework, which `@android:color/` references go through */
    const val ANDROID = "android"

    private const val RES = "res"
    private const val TYPE3_COMMON = "type3-common"
    private const val TYPE3_PREFIX = "type3_"
    private const val MAX_DEPTH = 8

    /** A preview role and the app colour resource that paints it. */
    data class Target(val role: String, val pkg: String, val name: String)

    /**
     * The folders of an overlay (its top-level entries) a colour is looked up in, in order.
     * Encrypted or unknown entries are left out.
     */
    fun layers(entries: Collection<String>): List<String> {
        val folders = entries.toSet()
        val defaultType3 = if (RES in folders) null
        else folders.filter { it.startsWith(TYPE3_PREFIX) && !it.contains('.') }.minOrNull()
        return listOfNotNull(
            TYPE3_COMMON.takeIf { it in folders },
            defaultType3,
            RES.takeIf { it in folders },
        )
    }

    /** The values folders of a layer worth reading, in order: night first. */
    fun valuesFolders(entries: Collection<String>): List<String> {
        val night = entries.filter { it == "values-night" || it.startsWith("values-night-") }.sorted()
        return night + entries.filter { it == "values" }
    }

    /**
     * One overlay's colours as written (name to value text, "#ff000000" or "@color/x"), from
     * [files]: paths relative to the overlay folder ("res/values/colors.xml") to their XML.
     */
    fun rawColors(files: Map<String, String>): Map<String, String> {
        val byFolder = files.keys.groupBy { it.substringBefore('/') }
        val out = LinkedHashMap<String, String>()
        val builder = newBuilder()
        for (layer in layers(byFolder.keys)) {
            val paths = byFolder[layer].orEmpty()
            val folders = paths.mapNotNull { p -> p.split('/').getOrNull(1)?.takeIf { p.count { it == '/' } == 2 } }.distinct()
            for (values in valuesFolders(folders)) {
                for (path in paths.filter { it.startsWith("$layer/$values/") && it.endsWith(".xml") }.sorted()) {
                    val xml = files[path] ?: continue
                    for ((name, value) in parseColors(builder, xml)) out.putIfAbsent(name, value)
                }
            }
        }
        return out
    }

    /**
     * The roles a theme's overlays give a colour, as ARGB. [overlays]: the overlay files by target
     * package (see [rawColors]); the `android` overlay resolves framework references.
     */
    fun resolve(targets: List<Target>, overlays: Map<String, Map<String, String>>): Map<String, Int> {
        val raw = HashMap<String, Map<String, String>>()
        fun colorsOf(pkg: String): Map<String, String> = raw.getOrPut(pkg) { overlays[pkg]?.let(::rawColors).orEmpty() }
        val out = LinkedHashMap<String, Int>()
        for (target in targets) {
            val value = colorsOf(target.pkg)[target.name] ?: continue
            resolveValue(target.pkg, value, ::colorsOf, 0)?.let { out[target.role] = it }
        }
        return out
    }

    private fun resolveValue(pkg: String, value: String, colorsOf: (String) -> Map<String, String>, depth: Int): Int? {
        if (depth > MAX_DEPTH) return null
        val v = value.trim()
        parseHex(v)?.let { return it }
        val (target, name) = when {
            v.startsWith("@color/") -> pkg to v.removePrefix("@color/")
            v.startsWith("@android:color/") -> ANDROID to v.removePrefix("@android:color/")
            v.startsWith("@*android:color/") -> ANDROID to v.removePrefix("@*android:color/")
            else -> return null
        }
        val next = colorsOf(target)[name.trim()] ?: return null
        return resolveValue(target, next, colorsOf, depth + 1)
    }

    /** #RGB, #ARGB, #RRGGBB or #AARRGGBB as ARGB (opaque when no alpha is given), or null. */
    fun parseHex(text: String): Int? {
        val t = text.trim()
        if (!t.startsWith("#")) return null
        val hex = t.substring(1)
        if (hex.isEmpty() || hex.any { Character.digit(it, 16) < 0 }) return null
        val full = when (hex.length) {
            3 -> "ff" + hex.map { "$it$it" }.joinToString("")
            4 -> hex.map { "$it$it" }.joinToString("")
            6 -> "ff$hex"
            8 -> hex
            else -> return null
        }
        return full.lowercase(Locale.ROOT).toLong(16).toInt()
    }

    private fun newBuilder(): DocumentBuilder {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = false
        factory.isValidating = false
        // A theme's XML is not trusted: no DTDs, no external entities
        runCatching { factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        runCatching { factory.setFeature("http://xml.org/sax/features/external-general-entities", false) }
        runCatching { factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        runCatching { factory.isExpandEntityReferences = false }
        return factory.newDocumentBuilder()
    }

    /** `<color name>` and `<item type="color" name>` of one resources file; empty when it does not parse. */
    private fun parseColors(builder: DocumentBuilder, xml: String): List<Pair<String, String>> {
        val doc = try {
            builder.parse(InputSource(StringReader(xml)))
        } catch (e: Exception) {
            return emptyList()
        }
        val root = doc.documentElement ?: return emptyList()
        val out = ArrayList<Pair<String, String>>()
        val children = root.childNodes
        for (i in 0 until children.length) {
            val el = children.item(i) as? Element ?: continue
            val isColor = el.tagName == "color" || (el.tagName == "item" && el.getAttribute("type") == "color")
            if (!isColor) continue
            val name = el.getAttribute("name").trim()
            if (name.isEmpty()) continue
            out += name to el.textContent.orEmpty().trim()
        }
        return out
    }
}

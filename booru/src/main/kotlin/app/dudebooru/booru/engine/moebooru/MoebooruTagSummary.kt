package app.dudebooru.booru.engine.moebooru

import app.dudebooru.booru.model.TagCategory

/**
 * Полный словарь тегов Moebooru из `/tag/summary.json`: тип, каноничное имя, алиасы.
 * Формат `data`: записи через пробел, поля записи через обратный апостроф: ``тип`имя`алиас1`алиас2` ``.
 * Счётчиков постов в нём нет, их даёт `/tag.json`.
 */
data class MoebooruTagSummary(
    val version: Long,
    val entries: List<Entry>,
) {
    data class Entry(val name: String, val category: TagCategory, val aliases: List<String>)

    companion object {
        fun parse(version: Long, data: String): MoebooruTagSummary {
            val entries = ArrayList<Entry>(data.length / 20)
            for (record in data.split(' ')) {
                if (record.isEmpty()) continue
                val fields = record.split('`')
                if (fields.size < 2) continue
                val type = fields[0].toIntOrNull() ?: continue
                val name = fields[1]
                if (name.isEmpty()) continue
                // `/ef` — сокращения для автодополнения сайта, это не алиасы.
                val aliases = fields.subList(2, fields.size).filter { it.isNotEmpty() && !it.startsWith("/") }
                entries += Entry(name, TagCategory.fromMoebooru(type), aliases)
            }
            return MoebooruTagSummary(version, entries)
        }
    }
}

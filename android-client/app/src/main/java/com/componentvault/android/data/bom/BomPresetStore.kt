package com.componentvault.android.data.bom

import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import org.json.JSONArray
import org.json.JSONObject

/** A saved project BOM contains source requirements and choices, never an inventory snapshot or release. */
data class BomPreset(
    val id: String,
    val name: String,
    val parsed: BomParseResult,
    val selections: Map<String, String> = emptyMap(),
    val excludedKeys: Set<String> = emptySet(),
) {
    fun reconfigure(productionSets: Int): BomPreset {
        require(productionSets > 0) { "生产套数必须是正整数。" }
        val requirements = parsed.requirements.map { requirement ->
            val required = try {
                Math.multiplyExact(requirement.quantityPerSet, productionSets)
            } catch (error: ArithmeticException) {
                throw IllegalArgumentException("生产套数过大，BOM 需求数量超过整数范围。", error)
            }
            requirement.copy(requiredQuantity = required)
        }
        return copy(parsed = parsed.copy(productionSets = productionSets, requirements = requirements))
    }
}

class BomPresetStoreException(message: String, cause: Throwable? = null) : IllegalStateException(message, cause)

/** Store in File(context.filesDir, "bom-presets.json"). Deletion is explicit. */
class BomPresetStore(private val file: File) {
    fun list(): List<BomPreset> {
        if (!file.exists()) return emptyList()
        return try {
            BomPresetCodec.decode(file.readText(Charsets.UTF_8))
        } catch (error: Exception) {
            throw BomPresetStoreException("无法读取 BOM 预设 ${file.name}；请检查或备份该文件后重试。", error)
        }
    }

    fun save(preset: BomPreset): BomPreset {
        require(preset.id.isNotBlank()) { "预设 ID 不能为空。" }
        require(preset.name.isNotBlank()) { "预设名称不能为空。" }
        require(preset.parsed.requirements.size <= LocalImportLimits.MAX_DATA_ROWS) { "预设 BOM 数据行超过 5000 行限制。" }
        val existing = list()
        val prior = existing.firstOrNull { it.id == preset.id }
            ?: existing.firstOrNull { it.name.equals(preset.name, ignoreCase = true) }
        val saved = preset.copy(id = prior?.id ?: preset.id, name = preset.name.trim())
        write(existing.filterNot { it.id == saved.id || it.name.equals(saved.name, ignoreCase = true) } + saved)
        return saved
    }

    fun delete(id: String): Boolean {
        val existing = list()
        val remaining = existing.filterNot { it.id == id }
        if (remaining.size == existing.size) return false
        write(remaining)
        return true
    }

    private fun write(presets: List<BomPreset>) {
        val parent = file.absoluteFile.parentFile
        if (!parent.exists() && !parent.mkdirs()) {
            throw BomPresetStoreException("无法创建 BOM 预设目录：${parent.path}")
        }
        val temp = File(parent, "${file.name}.tmp")
        try {
            temp.writeText(BomPresetCodec.encode(presets), Charsets.UTF_8)
            try {
                Files.move(temp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } catch (error: Exception) {
            throw BomPresetStoreException("无法保存 BOM 预设；请检查设备存储空间后重试。", error)
        } finally {
            temp.delete()
        }
    }
}

internal object BomPresetCodec {
    fun encode(presets: List<BomPreset>): String = JSONObject()
        .put("version", 1)
        .put("presets", JSONArray().apply { presets.forEach { put(encodePreset(it)) } })
        .toString()

    fun decode(text: String): List<BomPreset> {
        val root = JSONObject(text)
        require(root.getInt("version") == 1) { "不支持的 BOM 预设格式版本。" }
        val entries = root.getJSONArray("presets")
        return List(entries.length()) { decodePreset(entries.getJSONObject(it)) }
    }

    private fun encodePreset(preset: BomPreset): JSONObject = JSONObject()
        .put("id", preset.id)
        .put("name", preset.name)
        .put("parsed", encodeParsed(preset.parsed))
        .put("selections", JSONObject(preset.selections))
        .put("excludedKeys", JSONArray(preset.excludedKeys.toList()))

    private fun decodePreset(json: JSONObject): BomPreset = BomPreset(
        id = json.getString("id"),
        name = json.getString("name"),
        parsed = decodeParsed(json.getJSONObject("parsed")),
        selections = json.getJSONObject("selections").stringMap(),
        excludedKeys = json.getJSONArray("excludedKeys").strings().toSet(),
    )

    private fun encodeParsed(parsed: BomParseResult): JSONObject = JSONObject()
        .put("projectName", parsed.projectName)
        .put("productionSets", parsed.productionSets)
        .put("availableSheets", JSONArray().apply { parsed.availableSheets.forEach { put(encodeSheet(it)) } })
        .put("selectedSheet", encodeSheet(parsed.selectedSheet))
        .put("requirements", JSONArray().apply { parsed.requirements.forEach { put(encodeRequirement(it)) } })
        .put("fileSha256", parsed.fileSha256)

    private fun decodeParsed(json: JSONObject): BomParseResult = BomParseResult(
        projectName = json.getString("projectName"),
        productionSets = json.getInt("productionSets"),
        availableSheets = json.getJSONArray("availableSheets").objects().map(::decodeSheet),
        selectedSheet = decodeSheet(json.getJSONObject("selectedSheet")),
        requirements = json.getJSONArray("requirements").objects().map(::decodeRequirement),
        fileSha256 = json.getString("fileSha256"),
    )

    private fun encodeSheet(sheet: BomSheet): JSONObject = JSONObject()
        .put("name", sheet.name).put("index", sheet.index).put("hidden", sheet.hidden)

    private fun decodeSheet(json: JSONObject): BomSheet = BomSheet(
        json.getString("name"), json.getInt("index"), json.getBoolean("hidden"),
    )

    private fun encodeRequirement(requirement: BomRequirement): JSONObject = JSONObject()
        .put("identity", requirement.identity.canonicalKey)
        .put("sku", requirement.sku ?: JSONObject.NULL)
        .put("name", requirement.name ?: JSONObject.NULL)
        .put("model", requirement.model ?: JSONObject.NULL)
        .put("packageName", requirement.packageName ?: JSONObject.NULL)
        .put("quantityPerSet", requirement.quantityPerSet)
        .put("requiredQuantity", requirement.requiredQuantity)
        .put("sourceRows", JSONArray().apply { requirement.sourceRows.forEach { put(encodeSourceRow(it)) } })

    private fun decodeRequirement(json: JSONObject): BomRequirement = BomRequirement(
        identity = BomRequirementIdentity(json.getString("identity")),
        sku = json.nullableString("sku"),
        name = json.nullableString("name"),
        model = json.nullableString("model"),
        packageName = json.nullableString("packageName"),
        quantityPerSet = json.getInt("quantityPerSet"),
        requiredQuantity = json.getInt("requiredQuantity"),
        sourceRows = json.getJSONArray("sourceRows").objects().map(::decodeSourceRow),
    )

    private fun encodeSourceRow(source: BomSourceRow): JSONObject = JSONObject()
        .put("sheetName", source.sheetName)
        .put("rowNumber", source.rowNumber)
        .put("quantityPerSet", source.quantityPerSet)
        .put("fields", JSONObject(source.fields))

    private fun decodeSourceRow(json: JSONObject): BomSourceRow = BomSourceRow(
        sheetName = json.getString("sheetName"),
        rowNumber = json.getInt("rowNumber"),
        quantityPerSet = json.getInt("quantityPerSet"),
        fields = json.getJSONObject("fields").stringMap(),
    )

    private fun JSONObject.nullableString(key: String): String? = if (isNull(key)) null else getString(key)
    private fun JSONObject.stringMap(): Map<String, String> = keys().asSequence().associateWith { getString(it) }
    private fun JSONArray.objects(): List<JSONObject> = List(length()) { getJSONObject(it) }
    private fun JSONArray.strings(): List<String> = List(length()) { getString(it) }
}

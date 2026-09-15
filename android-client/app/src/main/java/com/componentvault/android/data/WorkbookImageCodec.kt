package com.componentvault.android.data

import android.content.Context
import android.graphics.BitmapFactory
import java.io.ByteArrayInputStream
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipInputStream

internal object WorkbookImageCodec {
    private const val maxImage = 5 * 1024 * 1024
    private const val maxTotal = 25 * 1024 * 1024
    private val anchor = Regex("<xdr:(?:oneCellAnchor|twoCellAnchor)[\\s\\S]*?<xdr:from>[\\s\\S]*?<xdr:col>(\\d+)</xdr:col>[\\s\\S]*?<xdr:row>(\\d+)</xdr:row>[\\s\\S]*?<a:blip[^>]*r:embed=\"([^\"]+)\"[\\s\\S]*?</xdr:(?:oneCellAnchor|twoCellAnchor)>")
    private val rel = Regex("<Relationship[^>]*Id=\"([^\"]+)\"[^>]*Target=\"([^\"]+)\"[^>]*/?>")

    fun extract(bytes: ByteArray, components: List<WorkbookComponent>, vararg acceptedColumns: Int): Map<String, ByteArray> {
        val entries = linkedMapOf<String, ByteArray>()
        var total = 0
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip -> while (true) {
            val e = zip.nextEntry ?: break
            if (!e.isDirectory && (e.name.startsWith("xl/drawings/") || e.name.startsWith("xl/media/"))) {
                val data = zip.readBytes(); total = Math.addExact(total, data.size)
                require(total <= maxTotal) { "工作簿图片解压总量超过 25 MiB。" }
                entries[e.name.replace('\\','/')] = data
            }
        } }
        val drawing = entries.entries.firstOrNull { it.key.matches(Regex("xl/drawings/drawing\\d+\\.xml")) } ?: return emptyMap()
        val relsName = drawing.key.substringBeforeLast('/') + "/_rels/" + drawing.key.substringAfterLast('/') + ".rels"
        val targets = entries[relsName]?.toString(Charsets.UTF_8)?.let { xml -> rel.findAll(xml).associate { it.groupValues[1] to normalize(drawing.key.substringBeforeLast('/') + "/" + it.groupValues[2]) } }.orEmpty()
        val result = linkedMapOf<String, ByteArray>()
        anchor.findAll(drawing.value.toString(Charsets.UTF_8)).forEach { match ->
            val col = match.groupValues[1].toInt(); val row = match.groupValues[2].toInt()
            if (col in acceptedColumns && row in 1..components.size) {
                val data = entries[targets[match.groupValues[3]]] ?: return@forEach
                validateImage(data)
                result[components[row - 1].id] = data
            }
        }
        return result
    }

    fun validateImage(bytes: ByteArray): String {
        require(bytes.size <= maxImage) { "单张图片超过 5 MiB。" }
        val ext = when {
            bytes.size >= 8 && bytes.copyOfRange(0,8).contentEquals(byteArrayOf(0x89.toByte(),0x50,0x4e,0x47,0x0d,0x0a,0x1a,0x0a)) -> "png"
            bytes.size >= 3 && bytes[0]==0xff.toByte() && bytes[1]==0xd8.toByte() && bytes[2]==0xff.toByte() -> "jpg"
            else -> error("图片必须是 PNG 或 JPEG。")
        }
        val o=BitmapFactory.Options().apply{inJustDecodeBounds=true};BitmapFactory.decodeByteArray(bytes,0,bytes.size,o)
        require(o.outWidth in 1..4096 && o.outHeight in 1..4096){"图片尺寸超过 4096×4096 或无法解码。"}
        return ext
    }
    fun marker(bytes:ByteArray):String { val ext=validateImage(bytes); return "本地图片："+MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it)}+"."+ext }
    private fun normalize(path:String):String { val out=mutableListOf<String>();path.split('/').forEach{if(it=="..")out.removeLastOrNull() else if(it.isNotBlank()&&it!=".")out+=it};return out.joinToString("/") }
}

internal class InventoryImageStore(context:Context) {
    private val root=File(context.filesDir,"images").apply{mkdirs()}.canonicalFile
    private val marker=Regex("(?:^|\\n)本地图片：([0-9a-f]{64}\\.(?:png|jpg))(?:$|\\n)")
    fun persist(bytes:ByteArray):String { val value=WorkbookImageCodec.marker(bytes);val name=value.substringAfter('：');val file=safe(name);if(!file.exists()){val temp=File(root,name+".tmp");temp.writeBytes(bytes);check(temp.renameTo(file)){"图片持久化失败。"}};return value }
    fun exists(value:String)=runCatching{safe(value.substringAfter('：')).isFile}.getOrDefault(false)
    fun delete(value:String){runCatching{safe(value.substringAfter('：')).delete()}}
    fun resolve(description:String):ByteArray? { val name=marker.find(description)?.groupValues?.get(1)?:return null;val file=safe(name);return file.takeIf{it.isFile}?.readBytes()?.also{WorkbookImageCodec.validateImage(it)} }
    private fun safe(name:String):File { require(name.matches(Regex("[0-9a-f]{64}\\.(png|jpg)")));val f=File(root,name).canonicalFile;require(f.parentFile==root){"图片路径越界。"};return f }
}

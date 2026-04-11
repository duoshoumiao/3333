package com.pcrjjc.app.util  
  
import android.util.Log  
import okhttp3.OkHttpClient  
import okhttp3.Request  
import java.io.ByteArrayInputStream  
import java.io.ByteArrayOutputStream  
import java.nio.ByteBuffer  
import java.nio.ByteOrder  
import java.util.concurrent.TimeUnit  
  
/**  
 * manifest 中每条记录  
 */  
data class AssetEntry(  
    val bundleName: String,  
    val hash: String,  
    val category: Int,  
    val size: Long  
)  
  
/**  
 * 角色图标信息  
 */  
data class UnitIconInfo(  
    val baseId: Int,  
    val star: Int,  
    val hash: String  
)  
  
/**  
 * 从游戏 CDN 下载资源清单和 Unity asset bundle  
 */  
class AssetDownloader {  
  
    companion object {  
        private const val TAG = "AssetDownloader"  
  
        // PCR CN CDN 根地址（B服）  
        private val CDN_ROOTS = listOf(  
            "https://le1-prod-all-gs-gzlj.bilibiligame.net",  
            "https://l2-prod-all-gs-gzlj.bilibiligame.net",  
            "https://l3-prod-all-gs-gzlj.bilibiligame.net"  
        )  
  
        // 维护状态 API（用于获取 manifest_ver）  
        private const val MAINTENANCE_API =  
            "/source_ini/get_maintenance_status?format=json"  
    }  
  
    private val httpClient = OkHttpClient.Builder()  
        .connectTimeout(15, TimeUnit.SECONDS)  
        .readTimeout(30, TimeUnit.SECONDS)  
        .writeTimeout(10, TimeUnit.SECONDS)  
        .build()  
  
    private fun getCdnRoot(): String = CDN_ROOTS.random()  
  
    /**  
     * 获取当前 manifest 版本号  
     */  
    fun getManifestVer(): String {  
        val url = getCdnRoot() + MAINTENANCE_API  
        val request = Request.Builder().url(url).build()  
        val response = httpClient.newCall(request).execute()  
        val body = response.body?.string()  
            ?: throw Exception("Empty maintenance response")  
  
        val json = org.json.JSONObject(body)  
        return json.optString("required_manifest_ver", "")  
            .ifEmpty { throw Exception("No manifest_ver in response") }  
    }  
  
    /**  
     * 下载并解析 manifest，返回 bundleName -> AssetEntry 的映射  
     */  
    fun downloadManifest(manifestVer: String): Map<String, AssetEntry> {  
        // manifest 本身是一个 Unity asset bundle  
        val url = "${getCdnRoot()}/dl/pool/AssetBundles" +  
                "/${manifestVer}/manifest/manifest_assetmanifest"  
        Log.i(TAG, "Downloading manifest from: $url")  
  
        val data = downloadRaw(url)  
  
        // 解析 UnityFS bundle  
        val files = UnityBundleParser.parse(data)  
        if (files.isEmpty()) throw Exception("Manifest bundle has no files")  
  
        // manifest 内部是一个 TextAsset，内容是 CSV 格式  
        // 尝试从序列化文件中提取文本内容  
        val textContent = extractTextFromSerializedFile(files[0].data)  
            ?: throw Exception("Cannot extract text from manifest")  
  
        return parseManifestText(textContent)  
    }  
  
    /**  
     * 从序列化文件中提取 TextAsset 的文本内容  
     * manifest 的 TextAsset 格式比较简单，直接搜索文本内容  
     */  
    private fun extractTextFromSerializedFile(data: ByteArray): String? {  
        // 简化解析：在数据中搜索 CSV 格式的文本  
        // manifest TextAsset 的内容以 "a/" 开头（bundle 路径前缀）  
        val str = try {  
            // 尝试用 SerializedFileParser 正式解析  
            SerializedFileParser.extractTextAsset(data)  
        } catch (e: Exception) {  
            Log.w(TAG, "Formal parse failed, trying heuristic: ${e.message}")  
            null  
        }  
  
        if (str != null) return str  
  
        // 启发式方法：在二进制数据中搜索 "a/" 开头的文本块  
        val dataStr = String(data, Charsets.UTF_8)  
        val startIdx = dataStr.indexOf("a/")  
        if (startIdx >= 0) {  
            // 向前找到长度字段（4字节 little-endian int）  
            return dataStr.substring(startIdx)  
        }  
        return null  
    }  
  
    /**  
     * 解析 manifest 文本内容  
     * 格式：bundleName,hash,category,size  
     */  
    private fun parseManifestText(text: String): Map<String, AssetEntry> {  
        val entries = mutableMapOf<String, AssetEntry>()  
        for (line in text.lines()) {  
            val parts = line.trim().split(",")  
            if (parts.size >= 4) {  
                val entry = AssetEntry(  
                    bundleName = parts[0],  
                    hash = parts[1],  
                    category = parts[2].toIntOrNull() ?: 0,  
                    size = parts[3].toLongOrNull() ?: 0  
                )  
                entries[entry.bundleName] = entry  
            }  
        }  
        Log.i(TAG, "Parsed ${entries.size} manifest entries")  
        return entries  
    }  
  
    /**  
     * 从 manifest 中筛选出所有角色图标  
     * bundle 名格式：a/unit_icon_unit_{unitKey}.unity3d  
     * unitKey 格式：{baseId}{star}1，如 100161（baseId=1001, star=6）  
     */  
    fun getAvailableUnitIcons(  
        manifest: Map<String, AssetEntry>  
    ): List<UnitIconInfo> {  
        val icons = mutableListOf<UnitIconInfo>()  
        val regex = Regex("""a/unit_icon_unit_(\d+)\.unity3d""")  
  
        for ((name, entry) in manifest) {  
            val match = regex.matchEntire(name) ?: continue  
            val unitKey = match.groupValues[1].toIntOrNull() ?: continue  
  
            // unitKey 格式：baseId * 100 + star * 10 + 1  
            // 例如 100161 -> baseId=1001, star=6  
            val baseId = unitKey / 100  
            val starDigit = (unitKey % 100) / 10  
  
            // 只要 1星、3星、6星  
            if (starDigit !in listOf(1, 3, 6)) continue  
  
            icons.add(UnitIconInfo(baseId, starDigit, entry.hash))  
        }  
  
        Log.i(TAG, "Found ${icons.size} unit icons in manifest")  
        return icons  
    }  
  
    /**  
     * 下载指定 hash 的 bundle  
     */  
    fun downloadBundle(manifestVer: String, hash: String): ByteArray {  
        val prefix = hash.substring(0, 2)  
        val url = "${getCdnRoot()}/dl/pool/AssetBundles" +  
                "/${manifestVer}/${prefix}/${hash}"  
        return downloadRaw(url)  
    }  
  
    private fun downloadRaw(url: String): ByteArray {  
        val request = Request.Builder().url(url).build()  
        val response = httpClient.newCall(request).execute()  
        if (!response.isSuccessful) {  
            throw Exception("HTTP ${response.code}: $url")  
        }  
        return response.body?.bytes()  
            ?: throw Exception("Empty response: $url")  
    }  
}
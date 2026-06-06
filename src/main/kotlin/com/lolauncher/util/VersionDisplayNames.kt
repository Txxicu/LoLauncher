package com.lolauncher.util

import com.lolauncher.data.models.VersionType
import com.lolauncher.service.OptiFineService

/**
 * Человекочитаемые названия версий для UI.
 */
object VersionDisplayNames {

  private val POPULAR_FORGE = listOf(
      "1.21.4", "1.21.1", "1.21", "1.20.6", "1.20.4", "1.20.2", "1.20.1",
      "1.19.4", "1.19.2", "1.18.2", "1.17.1", "1.16.5", "1.15.2", "1.14.4",
      "1.13.2", "1.12.2", "1.11.2", "1.10.2", "1.9.4", "1.8.9", "1.7.10"
  )

  private val POPULAR_FABRIC = listOf(
      "1.21.4", "1.21.1", "1.21", "1.20.6", "1.20.4", "1.20.2", "1.20.1",
      "1.19.4", "1.19.2", "1.18.2", "1.17.1", "1.16.5", "1.15.2", "1.14.4",
      "1.13.2", "1.12.2", "1.11.2", "1.10.2", "1.9.4", "1.8.9", "1.7.10"
  )

  fun popularForgeMcVersions(): List<String> = POPULAR_FORGE

  fun popularFabricMcVersions(): List<String> = POPULAR_FABRIC

  fun format(versionId: String, type: VersionType): String = when {
    versionId.startsWith("forge-") -> "Forge ${versionId.removePrefix("forge-")}"
    versionId.startsWith("fabric-loader-") -> formatFabricId(versionId)
    versionId.startsWith(OptiFineService.OPTIFINE_PREFIX) -> formatOptiFineId(versionId)
    type == VersionType.SNAPSHOT && !versionId.contains("forge", true) ->
      "Snapshot $versionId"
    type == VersionType.OLD_BETA -> "Beta $versionId"
    type == VersionType.OLD_ALPHA -> "Alpha $versionId"
    else -> versionId
  }

  private fun formatFabricId(versionId: String): String {
    val rest = versionId.removePrefix("fabric-loader-")
    val mcVersion = rest.substringAfterLast('-', missingDelimiterValue = rest)
    return if (mcVersion.isNotBlank() && mcVersion != rest) "Fabric $mcVersion" else "Fabric $rest"
  }

  private fun formatOptiFineId(versionId: String): String {
    val parts = versionId.removePrefix(OptiFineService.OPTIFINE_PREFIX).split(OptiFineService.SEP)
    if (parts.size >= 3) {
      val (mc, kind, patch) = parts
      val kindLabel = when (kind.lowercase()) {
        "hd_u" -> "HD U"
        "hd_u_i2" -> "HD U I2"
        "hd_u_i3" -> "HD U I3"
        "hd_u_i4" -> "HD U I4"
        "hd_u_i5" -> "HD U I5"
        "hd_u_i6" -> "HD U I6"
        else -> kind.uppercase()
      }
      return "OptiFine $mc $kindLabel C$patch"
    }
    return versionId
  }
}

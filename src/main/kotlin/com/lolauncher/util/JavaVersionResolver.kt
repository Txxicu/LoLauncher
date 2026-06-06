package com.lolauncher.util



import com.lolauncher.data.models.VersionJson

import com.lolauncher.service.OptiFineService



/**

 * Определяет требуемую версию Java для Minecraft.

 */

object JavaVersionResolver {



    data class JavaRequirement(

        val requiredMajor: Int,

        /** true — только Java 7/8 (LaunchWrapper) */

        val legacyOnly: Boolean

    )



    fun resolve(versionId: String, versionJson: VersionJson? = null): JavaRequirement {

        versionJson?.javaVersion?.majorVersion?.let { major ->

            return when {

                major <= 8 -> JavaRequirement(8, legacyOnly = usesLaunchWrapper(versionJson))

                major == 16 -> JavaRequirement(16, legacyOnly = false)

                major >= 21 -> JavaRequirement(21, legacyOnly = false)

                major >= 17 -> JavaRequirement(17, legacyOnly = false)

                else -> JavaRequirement(major, legacyOnly = false)

            }

        }



        if (usesLaunchWrapper(versionJson)) {

            return JavaRequirement(8, legacyOnly = true)

        }



        return fromVersionString(resolveBaseId(versionId))

    }



    private fun usesLaunchWrapper(versionJson: VersionJson?): Boolean =

        versionJson?.mainClass?.contains("launchwrapper", ignoreCase = true) == true



    private fun resolveBaseId(versionId: String): String = VersionJsonResolver.resolveBaseMcVersion(versionId)



    private fun fromVersionString(version: String): JavaRequirement {

        val numeric = version.filter { it.isDigit() || it == '.' }

        val parts = numeric.split(".").filter { it.isNotEmpty() }

        if (parts.isEmpty()) return JavaRequirement(17, legacyOnly = false)



        val major = parts[0].toIntOrNull() ?: 1

        val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0

        val patch = parts.getOrNull(2)?.toIntOrNull() ?: 0



        return when {

            major == 1 && minor <= 12 -> JavaRequirement(8, legacyOnly = true)

            major == 1 && minor in 13..16 -> JavaRequirement(8, legacyOnly = false)

            major == 1 && minor == 17 -> JavaRequirement(16, legacyOnly = false)

            major == 1 && minor == 20 && patch >= 5 -> JavaRequirement(21, legacyOnly = false)

            major == 1 && minor >= 21 -> JavaRequirement(21, legacyOnly = false)

            major == 1 && minor >= 18 -> JavaRequirement(17, legacyOnly = false)

            else -> JavaRequirement(17, legacyOnly = false)

        }

    }

}



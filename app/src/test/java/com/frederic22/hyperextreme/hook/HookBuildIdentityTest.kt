package com.frederic22.hyperextreme.hook

import com.frederic22.hyperextreme.BuildConfig
import org.junit.Assert.*
import org.junit.Test

class HookBuildIdentityTest {
    @Test fun logsTheVersionOfThisBuild() {
        val tag=HookEntry::class.java.getDeclaredField("BUILD_TAG").apply { isAccessible=true }.get(null) as String
        assertTrue(tag.contains(BuildConfig.VERSION_NAME))
        assertTrue(tag.contains("(${BuildConfig.VERSION_CODE})"))
    }
}

package com.roadsearch.openeditvideo.render

import com.roadsearch.openeditvideo.model.BlendMode
import org.junit.Assert.assertTrue
import org.junit.Test

class BlendShaderCatalogTest {
    @Test fun everyBlendModeHasShaderAndExpression() {
        BlendMode.entries.forEach { mode ->
            assertTrue(BlendShaderCatalog.function(mode).isNotBlank())
            assertTrue(BlendShaderCatalog.applyExpression(mode).contains("base"))
        }
    }
}

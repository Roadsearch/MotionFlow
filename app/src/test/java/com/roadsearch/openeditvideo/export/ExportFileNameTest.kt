package com.roadsearch.openeditvideo.export

import org.junit.Assert.assertEquals
import org.junit.Test

class ExportFileNameTest {
    @Test fun addsMp4WhenMissing() {
        assertEquals("mon_projet.mp4", ExportFileName.normalize("mon projet"))
    }

    @Test fun replacesUnsafeCharacters() {
        assertEquals("Projet_2026.mp4", ExportFileName.normalize("Projet/2026.mp4"))
    }

    @Test fun defaultsBlankName() {
        assertEquals("OpenEditVideo.mp4", ExportFileName.normalize("   "))
    }
}

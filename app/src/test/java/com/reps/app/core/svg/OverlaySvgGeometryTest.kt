package com.reps.app.core.svg

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlaySvgGeometryTest {

    @Test
    fun `extracts translate, size and path data from an overlay-shaped svg`() {
        val svg = """
            <?xml version="1.0" encoding="UTF-8" standalone="no"?>
            <svg width="200" height="362" id="svg2" version="1.1">
              <g id="layer1" transform="translate(-395.71431,-323.50506)">
                <path d="m 496.71569,400.4595 c -1.55,1.52 -1.10,3.85 -1.42,5.80 z" id="p1" />
                <path d="m 492.21306,400.04042 c 1.55,1.52 1.10,3.85 1.42,5.80 z" id="p2" />
              </g>
            </svg>
        """.trimIndent()

        val geometry = OverlaySvg.geometry(svg)

        assertNotNull(geometry)
        assertEquals(200f, geometry!!.canvasWidth)
        assertEquals(362f, geometry.canvasHeight)
        assertEquals(-395.71431f, geometry.translateX)
        assertEquals(-323.50506f, geometry.translateY)
        assertEquals(2, geometry.pathData.size)
        assertTrue(geometry.pathData[0].startsWith("m 496.71569"))
    }

    @Test
    fun `missing translate defaults to zero and missing size to the standard canvas`() {
        val svg = """<svg><path d="M 10,10 L 20,20 Z"/></svg>"""

        val geometry = OverlaySvg.geometry(svg)

        assertNotNull(geometry)
        assertEquals(0f, geometry!!.translateX)
        assertEquals(0f, geometry.translateY)
        assertEquals(200f, geometry.canvasWidth)
        assertEquals(369f, geometry.canvasHeight)
    }

    @Test
    fun `svg without path data is rejected`() {
        assertNull(OverlaySvg.geometry("""<svg width="200"><rect/></svg>"""))
    }

    @Test
    fun `empty d attributes are dropped`() {
        val svg = """<svg><path d=""/><path d="M 0,0"/></svg>"""

        val geometry = OverlaySvg.geometry(svg)

        assertEquals(1, geometry!!.pathData.size)
    }
}

package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.game.model.Catalog
import com.example.game.model.Vector2D
import com.example.game.model.Worm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Nana Tayantuh", appName)
  }

  @Test
  fun `vector math calculations`() {
    val v1 = Vector2D(3f, 4f)
    assertEquals(5f, v1.length(), 0.001f)
    val v2 = Vector2D(1f, 1f)
    val sum = v1 + v2
    assertEquals(4f, sum.x, 0.001f)
    assertEquals(5f, sum.y, 0.001f)
  }

  @Test
  fun `worm grows with points`() {
    val worm = Worm(
      name = "Nana",
      isPlayer = true,
      skin = Catalog.SKINS[0]
    )
    val initialSegs = worm.segments.size
    worm.grow(200)
    assertTrue(worm.score > 100)
    assertTrue(worm.segments.size >= initialSegs)
  }
}

package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
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
    assertEquals("Athan Alarm", appName)
  }

  @Test
  fun `verify both widget layouts inflate without RemoteViews InflateException`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    
    // Test small widget
    val smallViews = android.widget.RemoteViews(context.packageName, R.layout.widget_next_prayer)
    val smallView = smallViews.apply(context, null)
    org.junit.Assert.assertNotNull(smallView)

    // Test large schedule widget (must not throw InflateException!)
    val largeViews = android.widget.RemoteViews(context.packageName, R.layout.widget_prayer_schedule)
    val largeView = largeViews.apply(context, null)
    org.junit.Assert.assertNotNull(largeView)
  }
}

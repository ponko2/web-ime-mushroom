package jp.ponko2.android.webime

import _root_.android.app.Activity
import _root_.android.os.Bundle
import _root_.android.widget.TextView

class MushroomActivity extends Activity {
  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)
    setContentView(new TextView(this) {
      setText("hello, world")
    })
  }
}

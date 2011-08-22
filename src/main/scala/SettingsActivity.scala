package jp.ponko2.android.webime

import _root_.android.os.Bundle
import _root_.android.preference.PreferenceActivity
import _root_.android.content.{Context, Intent}

class SettingsActivity extends PreferenceActivity {
  override protected def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)

    getPreferenceManager.setSharedPreferencesName(Preferences.NAME)
    addPreferencesFromResource(R.xml.preferences)
  }
}

object SettingsActivity {
  def show(context: Context) {
    val intent = new Intent(context, classOf[SettingsActivity])
    context.startActivity(intent)
  }
}

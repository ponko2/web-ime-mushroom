package jp.ponko2.android.webime

import _root_.android.os.Bundle
import _root_.android.app.Activity
import _root_.android.preference.PreferenceActivity
import _root_.android.content.Intent

class SettingsActivity extends PreferenceActivity {
  override protected def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)

    getPreferenceManager.setSharedPreferencesName(Preferences.NAME)
    addPreferencesFromResource(R.xml.preferences)
  }
}

object SettingsActivity {
  val REQUEST_CODE = 42

  def show(activity: Activity) {
    val intent = new Intent(activity, classOf[SettingsActivity])
    activity.startActivityForResult(intent, REQUEST_CODE)
  }
}

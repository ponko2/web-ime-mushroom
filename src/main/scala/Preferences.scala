package jp.ponko2.android.webime

object Preferences {
  final val NAME = "WebIME"

  final val KEY_GOOGLE_JAPANESE_INPUT = "webime.google_japanese_input"
  final val KEY_GOOGLE_SUGGEST        = "webime.google_suggest"
  final val KEY_SOCIAL_IME            = "webime.social_ime"
  final val KEY_SOCIAL_IME_PREDICT    = "webime.social_ime_predict"

  def apiSettings(): Map[String, WebIME] = {
    Map(Preferences.KEY_GOOGLE_JAPANESE_INPUT -> GoogleJapaneseInput
       ,Preferences.KEY_GOOGLE_SUGGEST        -> GoogleSuggest
       ,Preferences.KEY_SOCIAL_IME            -> SocialIME
       ,Preferences.KEY_SOCIAL_IME_PREDICT    -> SocialImePredict)
  }
}

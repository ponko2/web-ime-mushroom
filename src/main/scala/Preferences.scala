package jp.ponko2.android.webime

object Preferences {
  final val NAME = "WebIME"

  final val KEY_GOOGLE_JAPANESE_INPUT = "webime.google_japanese_input"
  final val KEY_GOOGLE_SUGGEST        = "webime.google_suggest"
  final val KEY_SOCIAL_IME            = "webime.social_ime"
  final val KEY_SOCIAL_IME_PREDICT    = "webime.social_ime_predict"
  final val KEY_INPUT_EQUAL_RESULT    = "webime.input_equal_result"

  def apiSettings(): Map[String, WebIME] = {
    Map(KEY_GOOGLE_JAPANESE_INPUT -> GoogleJapaneseInput
       ,KEY_GOOGLE_SUGGEST        -> GoogleSuggest
       ,KEY_SOCIAL_IME            -> SocialIME
       ,KEY_SOCIAL_IME_PREDICT    -> SocialImePredict)
  }
}

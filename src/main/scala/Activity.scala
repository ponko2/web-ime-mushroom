package jp.ponko2.android.webime

import _root_.android.app.ListActivity
import _root_.android.os.Bundle
import _root_.android.widget.ArrayAdapter

class MushroomActivity extends ListActivity {
  import MushroomActivity._

  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.main)
    setListAdapter(new ArrayAdapter[String](this, R.layout.list_item, mStrings))
    getListView.setTextFilterEnabled(true)
  }
}

object MushroomActivity {
  private val mStrings = Array("ショボーン"
                              ,"ｷﾀ━━━━━━━━(ﾟ∀ﾟ)━━━━━━━━!!"
                              ,"(´゜'ω゜`)ショボーン"
                              ,"(´・ω・｀)"
                              ,"(´・ω・｀)ショボーン"
                              ,"| 　 ´ω｀|φｼｮﾎﾞﾘ～ﾅ"
                              ,"(´･ω･｀)"
                              ,"( ´・◡・｀)"
                              ,"(*´･ ω ･`*)ﾉ"
                              ,"（´・ω・`）"
                              ,"(＝ω＝｀)"
                              ,"（◞.‸.◟）"
                              ,"(´･ェ･`)"
                              ,"(´・ω・`)"
                              ,"（´・ω・`）ｼｮﾎﾞ-ﾝ"
                              ,"（´・ω・｀）ショボーン"
                              ,"(´-ω-｀)"
                              ,"(´・∀・｀)"
                              ,"( ´･ω･)つ〃∩　ｼｮﾎﾞｰﾝ"
                              ,"(´・ε・｀)"
                              ,"（´･ω･`）ｼｮﾝﾎﾞﾘ"
                              ,"（ ◞‸◟）"
                              ,"(；一_一)"
                              ,"(´･ω･`)ｶﾞｯｶﾘ…"
                              ,"（　･ิω･ิ）"
                              ,"(´･ω･`)"
                              ,"(´・ω❤｀)"
                              ,"（´・ω・｀）"
                              ,"（´・ω・‘）"
                              ,"(´・ω:;.:..."
                              ,"（´・ω:;.:..."
                              ,"( ・´ω・｀)"
                              ,"(´・ω・`)"
                              ,"(´－ω－)"
                              ,"(´･ω･`)ｼｮﾎﾞｰﾝ"
                              ,"(・ω・`)"
                              ,"(･ω`･)"
                              ,"(･ω･｀)"
                              ,"o( _ _ )o ショボーン"
                              ,"ヽ(  ´・ω・)ﾉ"
                              ,"ヽ(´・ω・｀)ﾉ"
                              ,"ヽ(･ω･｀  )ﾉ"
                              ,"（´・ω・｀)ショボ～ン"
                              ,"（´･ω･｀)"
                              ,"｜柱｜ω・｀)"
                              ,"｜柱｜ω・｀) o O （）"
                              ,"｜柱｜ω－｀) o O （）"
                              ,"｜柱｜ω－｀) z Z"
                              ,"しょぼーん")
}

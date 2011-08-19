package jp.ponko2.android.webime

import _root_.android.app.ListActivity
import _root_.android.os.{Bundle, AsyncTask}
import _root_.android.content.{Context, ContentValues, Intent}
import _root_.android.database.Cursor
import _root_.android.database.sqlite.SQLiteDatabase
import _root_.android.view.{Window, View, ViewGroup, LayoutInflater}
import _root_.android.widget.{CursorAdapter, TextView}

import dispatch.Http
import dispatch.Threads

class MushroomActivity extends ListActivity {
  import MushroomActivity._

  private var mDatabase: SQLiteDatabase = _
  private var mAdapter:  WordsAdapter   = _
  private var mTask:     AsyncTask[String, Nothing, Int] = _
  private val mHttp   = new Http with Threads
  private val mWebIME = Seq(GoogleSuggest, GoogleJapaneseInput, SocialIME)

  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)

    val word = "しょぼーん"

    mDatabase = new WordDatabase(this).getWritableDatabase
    mAdapter  = new WordsAdapter(this, initializeCursor(word))

    setupProgress()
    setContentView(R.layout.main)
    setListAdapter(mAdapter)

    mTask = new TransliterateTask().execute(word)
  }

  override protected def onDestroy() {
    super.onDestroy()

    if (mTask != null && mTask.getStatus == AsyncTask.Status.RUNNING) {
      mTask cancel true
    }

    mDatabase.close()
  }

  private def setupProgress() {
    requestWindowFeature(Window.FEATURE_PROGRESS)
    requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS)
  }

  private def showProgress() = setProgressBarIndeterminateVisibility(true)
  private def hideProgress() = setProgressBarIndeterminateVisibility(false)

  private def initializeCursor(word: String): Cursor = {
    val cursor = mDatabase.query(
      WordDatabase.TABLE_WORDS,
      Array(WordDatabase._ID, WordDatabase.COLUMN_API, WordDatabase.COLUMN_RESULT, WordDatabase.COLUMN_SEPARATOR),
      "input = ?", Array(word), null, null, WordDatabase.SORT_DEFAULT)

    startManagingCursor(cursor)

    cursor
  }

  private class TransliterateTask extends AsyncTask1[String, Nothing, Int] {
    override def onPreExecute() = showProgress()

    def doInBackground(param: String): Int = {
      mWebIME./:(0) {
        (sum, webime) => {
          val words = mHttp(webime.transliterate(param))
          sum + WordDatabase.addWords(mDatabase, webime.tag, param, words)
        }
      }
    }

    override def onPostExecute(result: Int) {
      hideProgress()
      if (result > 0) {
        mAdapter.refresh()
      }
    }
  }

  private class WordsAdapter(context: Context, cursor: Cursor) extends CursorAdapter(context, cursor, true) {
    private final val mInflater      = LayoutInflater.from(context)
    private final val mId            = cursor.getColumnIndexOrThrow(WordDatabase._ID)
    private final val mApi           = cursor.getColumnIndexOrThrow(WordDatabase.COLUMN_API)
    private final val mResult        = cursor.getColumnIndexOrThrow(WordDatabase.COLUMN_RESULT)
    private final val mSeparator     = cursor.getColumnIndexOrThrow(WordDatabase.COLUMN_SEPARATOR)
    private final val TYPE_COUNT     = 2
    private final val TYPE_SEPARATOR = 0
    private final val TYPE_ITEM      = 1

    def newView(context: Context, cursor: Cursor, parent: ViewGroup): View = {
      val view = if (cursor.getInt(mSeparator) == TYPE_SEPARATOR) {
        mInflater.inflate(R.layout.list_separator, parent, false)
      } else {
        mInflater.inflate(R.layout.list_item, parent, false)
      }

      val description = new WordDescription()
      view setTag description
      view
    }

    def bindView(view: View, context: Context, cursor: Cursor) {
      val description = view.getTag.asInstanceOf[WordDescription]
      description.id = cursor.getString(mId)

      val textView = view.asInstanceOf[TextView]
      if (cursor.getInt(mSeparator) == TYPE_SEPARATOR) {
        textView.setText(cursor.getString(mApi))
      } else {
        textView.setText(cursor.getString(mResult))
      }
    }

    override def getViewTypeCount(): Int = TYPE_COUNT

    override def getItemViewType(position: Int): Int = {
      val cursor = this.getCursor()
      cursor.moveToPosition(position)

      if (cursor.getInt(mSeparator) == TYPE_SEPARATOR) TYPE_SEPARATOR else TYPE_ITEM
    }

    override def isEnabled(position: Int): Boolean = {
      getItemViewType(position) == TYPE_ITEM
    }

    def refresh() {
      getCursor.requery()
    }
  }
}

object MushroomActivity {
  private class WordDescription {
    var id: String = _
  }
}

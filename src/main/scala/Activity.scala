package jp.ponko2.android.webime

import _root_.android.app.ListActivity
import _root_.android.os.{Bundle, AsyncTask}
import _root_.android.content.{Context, ContentValues, Intent}
import _root_.android.database.Cursor
import _root_.android.database.sqlite.SQLiteDatabase
import _root_.android.view.{Window, ContextMenu, Menu, MenuItem, View, ViewGroup, LayoutInflater}
import _root_.android.widget.{AdapterView, CursorAdapter, ListView, TextView}
import _root_.android.text.ClipboardManager
import _root_.android.content.Context.CLIPBOARD_SERVICE

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

    val word = "きたー"

    mDatabase = new WordDatabase(this).getWritableDatabase

    setupProgress()
    setupViews(word)

    onTransliterate(word)
  }

  private def setupViews(word: String) {
    setContentView(R.layout.main)

    mAdapter  = new WordsAdapter(this, initializeCursor(word))
    setListAdapter(mAdapter)

    registerForContextMenu(getListView())
  }

  override def onCreateOptionsMenu(menu: Menu): Boolean = {
    getMenuInflater.inflate(R.menu.main, menu)
    super.onCreateOptionsMenu(menu)
  }

  override def onMenuItemSelected(featureId: Int, item: MenuItem): Boolean = {
    item.getItemId match {
      case R.id.menu_item_delete =>
        onRemoveAllWord()
        true
      case _ =>
        super.onMenuItemSelected(featureId, item)
    }
  }

  override def onCreateContextMenu(menu: ContextMenu, view: View, menuInfo: ContextMenu.ContextMenuInfo) {
    super.onCreateContextMenu(menu, view, menuInfo)

    val info = menuInfo.asInstanceOf[AdapterView.AdapterContextMenuInfo]
    menu.setHeaderTitle(info.targetView.asInstanceOf[TextView].getText)

    menu.add(0, MENU_ID_COPY,   0, R.string.context_menu_copy_word)
    menu.add(0, MENU_ID_DELETE, 0, R.string.context_menu_delete_word)
  }

  override def onContextItemSelected(item: MenuItem): Boolean = {
    val info = item.getMenuInfo.asInstanceOf[AdapterView.AdapterContextMenuInfo]
    val description = info.targetView.getTag.asInstanceOf[WordDescription]

    item.getItemId match {
      case MENU_ID_COPY =>
        val word = info.targetView.asInstanceOf[TextView].getText.toString
        onCopyWord(word)
        true
      case MENU_ID_DELETE =>
        onRemoveWord(description.id)
        true
      case _ =>
        super.onContextItemSelected(item)
    }
  }

  override protected def onDestroy() {
    super.onDestroy()

    if (mTask != null && mTask.getStatus == AsyncTask.Status.RUNNING) {
      mTask cancel true
    }

    mDatabase.close()
    mHttp.shutdown()
  }


  override protected def onListItemClick(listView: ListView, view: View, position: Int, id: Long) {
    super.onListItemClick(listView, view, position, id);
    val description = view.getTag.asInstanceOf[WordDescription]

    onAddHistory(description.id)
  }


  private def onTransliterate(word: String) {
    if (word.nonEmpty) mTask = new TransliterateTask().execute(word)
  }

  private def onCopyWord(word: String) {
    val cm = getSystemService(CLIPBOARD_SERVICE).asInstanceOf[ClipboardManager]
    cm.setText(word)
  }

  private def onAddHistory(id: String) {
    val values = new ContentValues()
    values.put(WordDatabase.COLUMN_HISTORY, 0.0)

    mDatabase.update(WordDatabase.TABLE_WORDS, values,
                     WordDatabase._ID + "=?", Array(id))
  }

  private def onRemoveWord(id: String) {
    val rows = mDatabase.delete(WordDatabase.TABLE_WORDS, WordDatabase._ID + "=?", Array(id))
    if (rows > 0) mAdapter.refresh()
  }

  private def onRemoveAllWord() {
    val rows = mDatabase.delete(WordDatabase.TABLE_WORDS, null, null)
    if (rows > 0) mAdapter.refresh()
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
      WordDatabase.COLUMN_INPUT + "=?", Array(word), null, null, WordDatabase.SORT_DEFAULT)

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
  private final val MENU_ID_COPY   = 1
  private final val MENU_ID_DELETE = 2

  private class WordDescription {
    var id: String = _
  }
}

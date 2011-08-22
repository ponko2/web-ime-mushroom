package jp.ponko2.android.webime

import _root_.android.app.{Activity, ListActivity, Dialog, AlertDialog, SearchManager}
import _root_.android.os.{Bundle, AsyncTask}
import _root_.android.content.{Context, ContentValues, Intent, DialogInterface, SharedPreferences}
import _root_.android.database.Cursor
import _root_.android.database.sqlite.SQLiteDatabase
import _root_.android.view.{Window, ContextMenu, Menu, MenuItem, View, ViewGroup, LayoutInflater}
import _root_.android.widget.{AdapterView, CursorAdapter, ListView, TextView, EditText, Toast}
import _root_.android.text.ClipboardManager

import scala.util.control.Exception._
import scala.collection.mutable.ArrayBuffer

import dispatch.Http
import dispatch.Threads
import dispatch.StatusCode

class MushroomActivity extends ListActivity {
  import MushroomActivity._

  private var mWord:        String            = _
  private var mReplaceKey:  Boolean           = _
  private var mDatabase:    SQLiteDatabase    = _
  private var mAdapter:     WordsAdapter      = _
  private var mClipboard:   ClipboardManager  = _
  private var mPreferences: SharedPreferences = _
  private var mAPI:         ArrayBuffer[WebIME] = ArrayBuffer()
  private var mTasks:       ArrayBuffer[AsyncTask[String, Nothing, Either[Throwable, Int]]] = ArrayBuffer()
  private val mHttp = new Http with Threads

  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)

    mDatabase    = new WordDatabase(this).getWritableDatabase
    mPreferences = getSharedPreferences(Preferences.NAME, Context.MODE_PRIVATE)
    mClipboard   = getSystemService(Context.CLIPBOARD_SERVICE).asInstanceOf[ClipboardManager]

    setupProgress()
    setupReplaceKey()
    setupAPI()
    setContentView(R.layout.main)
    setupViews()
  }

  private def setupReplaceKey() {
    val intent = getIntent()
    val action = intent.getAction()
    if (action != null && ACTION_INTERCEPT == action) {
      mWord = intent.getStringExtra(REPLACE_KEY)
      mReplaceKey = true
    } else {
      mReplaceKey = false
    }
  }

  private def setupAPI() {
    if (mPreferences.getBoolean(Preferences.KEY_GOOGLE_JAPANESE_INPUT, true)) {
      mAPI += GoogleJapaneseInput
    }
    if (mPreferences.getBoolean(Preferences.KEY_GOOGLE_SUGGEST, true)) {
      mAPI += GoogleSuggest
    }
    if (mPreferences.getBoolean(Preferences.KEY_SOCIAL_IME, true)) {
      mAPI += SocialIME
    }
    if (mPreferences.getBoolean(Preferences.KEY_SOCIAL_IME_PREDICT, true)) {
      mAPI += SocialImePredict
    }
  }

  private def setupViews() {
    if (mWord != null && mWord.nonEmpty) {
      mAdapter = new WordsAdapter(this, initializeCursor())
      setListAdapter(mAdapter)
      registerForContextMenu(getListView())
      onTransliterate()
    } else {
      showDialog(INPUT_DIALOG)
    }
  }

  override def onCreateOptionsMenu(menu: Menu): Boolean = {
    getMenuInflater.inflate(R.menu.main, menu)
    super.onCreateOptionsMenu(menu)
  }

  override def onMenuItemSelected(featureId: Int, item: MenuItem): Boolean = {
    item.getItemId match {
      case R.id.menu_item_settings =>
        SettingsActivity.show(this)
        true
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
    menu.add(0, MENU_ID_SEARCH, 0, R.string.context_menu_search_word)
  }

  override def onContextItemSelected(item: MenuItem): Boolean = {
    val info = item.getMenuInfo.asInstanceOf[AdapterView.AdapterContextMenuInfo]
    val description = info.targetView.getTag.asInstanceOf[WordDescription]
    val word = info.targetView.asInstanceOf[TextView].getText.toString

    item.getItemId match {
      case MENU_ID_COPY =>
        onCopyWord(word)
        true
      case MENU_ID_DELETE =>
        onRemoveWord(description.id)
        true
      case MENU_ID_SEARCH =>
        onSearchWord(word)
        true
      case _ =>
        super.onContextItemSelected(item)
    }
  }

  override protected def onDestroy() {
    super.onDestroy()

    def cancel(task: AsyncTask[_, _, _]) {
      if (task != null && task.getStatus == AsyncTask.Status.RUNNING) {
        task cancel true
      }
    }

    if (mTasks != null) mTasks.foreach(cancel)

    mDatabase.close()
    mHttp.shutdown()
  }

  override protected def onListItemClick(listView: ListView, view: View, position: Int, id: Long) {
    super.onListItemClick(listView, view, position, id)
    val description = view.getTag.asInstanceOf[WordDescription]
    val result      = view.asInstanceOf[TextView].getText.toString

    onAddHistory(description.id)

    val intent = new Intent()
    if (mReplaceKey) {
      intent.putExtra(REPLACE_KEY, result)
    } else {
      onCopyWord(result)
    }
    setResult(Activity.RESULT_OK, intent)
    finish()
  }

  override protected def onCreateDialog(id: Int): Dialog = {
    id match {
      case INPUT_DIALOG => {
        val inflater = LayoutInflater.from(this)
        val view     = inflater.inflate(R.layout.input_dialog, null)
        val editText = view.findViewById(R.id.input_text).asInstanceOf[EditText]
        editText.setText(mClipboard.getText)
        new AlertDialog.Builder(MushroomActivity.this)
           .setIcon(android.R.drawable.ic_dialog_info)
           .setTitle(R.string.input_dialog_title)
           .setView(view)
           .setPositiveButton("OK", new DialogInterface.OnClickListener() {
              def onClick(dialog: DialogInterface, which: Int) {
                mWord = editText.getText.toString
                setupViews()
              }
           })
           .create()
      }
      case _ => null
    }
  }

  private def onTransliterate() {
    mAPI.foreach(api => mTasks += new TransliterateTask(api).execute(mWord))
  }

  private def onCopyWord(word: String) {
    mClipboard.setText(word)
    Toast.makeText(this, R.string.toast_copy_word, Toast.LENGTH_LONG).show()
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

  private def onSearchWord(word: String) {
    val intent = new Intent(Intent.ACTION_WEB_SEARCH)
    intent.putExtra(SearchManager.QUERY, word)
    startActivity(intent)
  }

  private def onRemoveAllWord() {
    val rows = mDatabase.delete(WordDatabase.TABLE_WORDS, null, null)
    if (rows > 0) {
      Toast.makeText(this, R.string.toast_remove_cache, Toast.LENGTH_LONG).show()
      if (mAdapter != null) mAdapter.refresh()
    }
  }

  private def setupProgress() {
    requestWindowFeature(Window.FEATURE_PROGRESS)
    requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS)
  }

  private def showProgress() = setProgressBarIndeterminateVisibility(true)

  private def hideProgress() = {
    if (mTasks.count(task => task != null && task.getStatus == AsyncTask.Status.RUNNING) <= 1)
      setProgressBarIndeterminateVisibility(false)
  }

  private def initializeCursor(): Cursor = {
    val selection = if (mAPI.nonEmpty) {
      WordDatabase.COLUMN_INPUT + "=? and " +
        mAPI.map(api => WordDatabase.COLUMN_API + "=?").mkString("("," or ",")")
    } else { "0 = 1" }
    val selectionArgs = if (mAPI.nonEmpty) {
      mAPI./:(ArrayBuffer(mWord)) { (buffer, api) => buffer += api.tag }.toArray
    } else { null }

    val cursor = mDatabase.query(
      WordDatabase.TABLE_WORDS,
      Array(WordDatabase._ID, WordDatabase.COLUMN_API, WordDatabase.COLUMN_RESULT, WordDatabase.COLUMN_SEPARATOR),
      selection, selectionArgs, null, null, WordDatabase.SORT_DEFAULT)

    startManagingCursor(cursor)
    cursor
  }

  private class TransliterateTask(webIME: WebIME) extends AsyncTask1[String, Nothing, Either[Throwable, Int]] {
    override def onPreExecute() = showProgress()

    def doInBackground(param: String): Either[Throwable, Int] = {
      allCatch either WordDatabase.addWords(mDatabase, webIME.tag, param, mHttp(webIME.transliterate(param)))
    }

    override def onPostExecute(result: Either[Throwable, Int]) {
      hideProgress()
      result match {
        case Right(count) => if (count > 0) mAdapter.refresh()
        case Left(error) => {
          val message = webIME.tag + " - " + (error match {
            case StatusCode(code, _) => "Http Status: " + code
            case other => other.toString
          })
          Toast.makeText(MushroomActivity.this, message, Toast.LENGTH_LONG).show()
        }
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
  private final val ACTION_INTERCEPT = "com.adamrocker.android.simeji.ACTION_INTERCEPT"
  private final val REPLACE_KEY      = "replace_key"
  private final val MENU_ID_COPY     = 1
  private final val MENU_ID_DELETE   = 2
  private final val MENU_ID_SEARCH   = 3
  private final val INPUT_DIALOG     = 42

  private class WordDescription {
    var id: String = _
  }
}

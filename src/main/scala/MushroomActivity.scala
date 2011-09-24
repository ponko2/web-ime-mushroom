package jp.ponko2.android.webime

import _root_.android.app.{Activity, ListActivity, Dialog, AlertDialog, ProgressDialog, SearchManager}
import _root_.android.os.{Bundle, AsyncTask}
import _root_.android.content.{Context, ContentValues, Intent, DialogInterface, SharedPreferences}
import _root_.android.database.Cursor
import _root_.android.database.sqlite.SQLiteDatabase
import _root_.android.view.{Window, ContextMenu, Menu, MenuItem, View, ViewGroup, LayoutInflater}
import _root_.android.widget.{AdapterView, CursorAdapter, ListView, TextView, EditText, Toast}
import _root_.android.text.ClipboardManager
import _root_.android.util.Log

import scala.util.control.Exception._

import dispatch.Http
import dispatch.Threads
import dispatch.StatusCode

import jp.ponko2.android.os.AsyncTask1

class MushroomActivity extends ListActivity {
  import MushroomActivity._

  private var mInputWord = ""
  private var mTasks: Seq[AsyncTask[String, Int, Either[Throwable, Int]]] = Seq()

  private lazy val mReplaceWord = getReplaceWord()
  private lazy val mPreferences = getSharedPreferences(Preferences.NAME, Context.MODE_PRIVATE)
  private lazy val mClipboard   = getSystemService(Context.CLIPBOARD_SERVICE).asInstanceOf[ClipboardManager]
  private lazy val mHttp     = new Http with Threads
  private lazy val mProgress = new ProgressDialog(this)
  private lazy val mDatabase = new WordDatabase(this).getWritableDatabase
  private lazy val mAdapter  = new WordsAdapter(this, initializeCursor(mReplaceWord))
  private lazy val mAddDictionaryTask = new AddDictionaryTask

  override protected def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)

    setupProgress()
    setContentView(R.layout.main)
    setupViews()

    onTransliterate(mReplaceWord)
  }

  override protected def onActivityResult(requestCode: Int, resultCode: Int, data: Intent) {
    super.onActivityResult(requestCode, resultCode, data)

    if (requestCode == SettingsActivity.REQUEST_CODE) {
      val word = if (mInputWord.nonEmpty) mInputWord else mReplaceWord
      mAdapter.changeCursor(initializeCursor(word))
      onTransliterate(word)
    }
  }

  private def getReplaceWord(): String = {
    val intent = getIntent()
    Option(intent.getAction()) match {
      case Some(action) if action == ACTION_INTERCEPT => intent.getStringExtra(REPLACE_KEY)
      case _ => ""
    }
  }

  private def apiSettings(): Seq[WebIME] = {
    Preferences.apiSettings.filterKeys(key => mPreferences.getBoolean(key, true)).values.toSeq
  }

  private def setupViews() {
    setListAdapter(mAdapter)
    registerForContextMenu(getListView())
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
      case R.id.menu_item_dictionary =>
        showDialog(DICTIONARY_DIALOG)
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
      if (task.getStatus == AsyncTask.Status.RUNNING) {
        task cancel true
      }
    }

    mTasks.foreach(cancel)
    cancel(mAddDictionaryTask)

    mAdapter.close()
    mDatabase.close()
    mHttp.shutdown()
  }

  override protected def onListItemClick(listView: ListView, view: View, position: Int, id: Long) {
    super.onListItemClick(listView, view, position, id)
    val description = view.getTag.asInstanceOf[WordDescription]
    val result      = view.asInstanceOf[TextView].getText.toString

    onAddHistory(description.id)

    val intent = new Intent()
    if (mReplaceWord.nonEmpty) {
      intent.putExtra(REPLACE_KEY, result)
    } else {
      onCopyWord(result)
    }
    setResult(Activity.RESULT_OK, intent)
    finish()
  }

  override protected def onCreateDialog(id: Int): Dialog = {
    id match {
      case INPUT_DIALOG      => createInputDialog()
      case DICTIONARY_DIALOG => createDictionaryDialog()
      case _                 => null
    }
  }

  private def createInputDialog(): Dialog = {
    val inflater = LayoutInflater.from(this)
    val view     = inflater.inflate(R.layout.input_dialog, null)
    val editText = view.findViewById(R.id.input_text).asInstanceOf[EditText]
    editText.setText(mClipboard.getText)
    new AlertDialog.Builder(MushroomActivity.this)
       .setIcon(android.R.drawable.ic_dialog_info)
       .setTitle(R.string.input_dialog_title)
       .setView(view)
       .setPositiveButton(R.string.positive_button, new DialogInterface.OnClickListener() {
          def onClick(dialog: DialogInterface, which: Int) {
            mInputWord = editText.getText.toString
            mAdapter.changeCursor(initializeCursor(mInputWord))
            onTransliterate(mInputWord)
          }
       })
       .create()
  }

  private def createDictionaryDialog(): Dialog = {
    val inflater = LayoutInflater.from(this)
    val view = inflater.inflate(R.layout.dictionary_dialog, null)
    val yomi = view.findViewById(R.id.dictionary_yomi).asInstanceOf[EditText]
    val word = view.findViewById(R.id.dictionary_word).asInstanceOf[EditText]
    yomi.setText(if (mInputWord.nonEmpty) mInputWord else mReplaceWord)
    new AlertDialog.Builder(MushroomActivity.this)
       .setIcon(android.R.drawable.ic_dialog_alert)
       .setTitle(R.string.dictionary_dialog_title)
       .setView(view)
       .setPositiveButton(R.string.positive_button, new DialogInterface.OnClickListener() {
          def onClick(dialog: DialogInterface, which: Int) {
            mAddDictionaryTask.execute((yomi.getText.toString, word.getText.toString))
          }
       })
       .setNegativeButton(R.string.negative_button, new DialogInterface.OnClickListener() {
          def onClick(dialog: DialogInterface, which: Int) { }
       })
       .create()
  }

  private def onTransliterate(word: String) {
    if (word.nonEmpty) {
      mTasks = apiSettings.map(api => new TransliterateTask(api).execute(word))
    } else {
      showDialog(INPUT_DIALOG)
    }
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
      mAdapter.refresh()
    }
  }

  private def setupProgress() {
    requestWindowFeature(Window.FEATURE_PROGRESS)
    requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS)
  }

  private def showProgress() {
    setProgressBarsVisibility(true)
  }

  private def hideProgress() {
    setProgressBarsVisibility(false)
  }

  private def setProgressBarsVisibility(flag: Boolean) {
    setProgressBarVisibility(flag)
    setProgressBarIndeterminateVisibility(flag)
  }

  private def initializeCursor(word: String): Cursor = {
    val api = apiSettings()
    val selection = if (api.nonEmpty && word.nonEmpty) {
      WordDatabase.COLUMN_INPUT + "=? and " +
        api.map(_ => WordDatabase.COLUMN_API + "=?").mkString("("," or ",")") +
        (if (mPreferences.getBoolean(Preferences.KEY_INPUT_EQUAL_RESULT, true)) {
          "and not (" +
          WordDatabase.COLUMN_INPUT + " = " + WordDatabase.COLUMN_RESULT + " and " +
          WordDatabase.COLUMN_SEPARATOR + " != 0)"
        } else { "" })
    } else { "0 = 1" }
    val selectionArgs = if (api.nonEmpty && word.nonEmpty) {
      Array(word) ++ api.map(_.tag)
    } else { null }

    mDatabase.query(
      WordDatabase.TABLE_WORDS,
      Array(WordDatabase._ID, WordDatabase.COLUMN_API, WordDatabase.COLUMN_RESULT, WordDatabase.COLUMN_SEPARATOR),
      selection, selectionArgs, null, null, WordDatabase.SORT_DEFAULT)
  }

  private class TransliterateTask(webIME: WebIME) extends AsyncTask1[String, Int, Either[Throwable, Int]] {
    override protected def onPreExecute() = showProgress()
    override protected def onCancelled()  = hideProgress()

    override protected def doInBackground(param: String): Either[Throwable, Int] = {
      try {
        allCatch either WordDatabase.addWords(mDatabase, webIME.tag, param, mHttp(webIME.transliterate(param)))
      } finally {
        publishProgress(mTasks.count(task => task.getStatus == AsyncTask.Status.FINISHED) + 1)
      }
    }

    override protected def onProgressUpdate(progress: Int) {
      val task = mTasks.size
      if (progress > 0 && task > 0) {
        setProgress(((progress / task.toFloat) * 10000).toInt)
      }
    }

    override protected def onPostExecute(result: Either[Throwable, Int]) {
      if (mTasks.count(task => task.getStatus == AsyncTask.Status.RUNNING) <= 1) {
        hideProgress()
      }

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

  private class AddDictionaryTask extends AsyncTask1[(String, String), Nothing, Either[Throwable, String]] {
    override protected def onPreExecute() {
      mProgress.setProgressStyle(ProgressDialog.STYLE_SPINNER)
      mProgress.setMessage(getString(R.string.progress_add_dictionary_task))
      mProgress.setCancelable(true)
      mProgress.show()
    }

    override protected def onCancelled() = mProgress.dismiss()

    override protected def doInBackground(param: (String, String)): Either[Throwable, String] = {
      val (yomi, word) = param
      allCatch either mHttp(SocialIME.addDictionary(yomi, word))
    }

    override protected def onPostExecute(result: Either[Throwable, String]) {
      mProgress.dismiss()

      result match {
        case Right(word) if word.nonEmpty =>
          Toast.makeText(MushroomActivity.this,
            R.string.complete_add_dictionary_task, Toast.LENGTH_LONG).show()
          if (word != mInputWord && word != mReplaceWord) {
            mAdapter.changeCursor(initializeCursor(word))
          }
          onTransliterate(word)
        case Left(StatusCode(code, _)) =>
          Toast.makeText(MushroomActivity.this,
            SocialIME.tag + " - Http Status: " + code, Toast.LENGTH_LONG).show()
        case Left(e) if e.isInstanceOf[IllegalArgumentException] =>
          Toast.makeText(MushroomActivity.this,
            R.string.failure_length_add_dictionary_task, Toast.LENGTH_LONG).show()
        case _ =>
          Toast.makeText(MushroomActivity.this,
            R.string.failure_add_dictionary_task, Toast.LENGTH_LONG).show()
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
      val cursor = getCursor()
      cursor.moveToPosition(position)

      if (cursor.getInt(mSeparator) == TYPE_SEPARATOR) TYPE_SEPARATOR else TYPE_ITEM
    }

    override def isEnabled(position: Int): Boolean = {
      getItemViewType(position) == TYPE_ITEM
    }

    override def changeCursor(cursor: Cursor) {
      getCursor.close()
      super.changeCursor(cursor)
    }

    def close() {
      getCursor.close()
    }

    def refresh() {
      getCursor.requery()
    }
  }
}

object MushroomActivity {
  private final val ACTION_INTERCEPT  = "com.adamrocker.android.simeji.ACTION_INTERCEPT"
  private final val REPLACE_KEY       = "replace_key"
  private final val MENU_ID_COPY      = 1
  private final val MENU_ID_DELETE    = 2
  private final val MENU_ID_SEARCH    = 3
  private final val INPUT_DIALOG      = 42
  private final val DICTIONARY_DIALOG = 54

  private class WordDescription {
    var id: String = _
  }
}

package jp.ponko2.android.webime

import _root_.android.database.sqlite.{SQLiteOpenHelper, SQLiteDatabase}
import _root_.android.content.{Context, ContentValues}
import _root_.android.provider.BaseColumns

class WordDatabase(mContext: Context)
  extends SQLiteOpenHelper(mContext, WordDatabase.DATABASE_NAME, null, WordDatabase.DATABASE_VERSION) {
  import WordDatabase._

  override def onCreate(db: SQLiteDatabase) {
    db.execSQL("""|CREATE TABLE words (
                  |   _id       INTEGER PRIMARY KEY,
                  |   api       TEXT NOT NULL,
                  |   input     TEXT NOT NULL,
                  |   result    TEXT NOT NULL DEFAULT "-",
                  |   separator INTEGER DEFAULT 1 CHECK(separator in (0,1)),
                  |   history   INTEGER DEFAULT 1 CHECK(history   in (0,1)),
                  |   UNIQUE(api, input, result, separator));""".stripMargin)
  }

  override def onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    db execSQL "DROP TABLE IF EXISTS words"
    onCreate(db)
  }
}

object WordDatabase {
  final val _ID = BaseColumns._ID

  private final val DATABASE_NAME    = "webime"
  private final val DATABASE_VERSION = 1

  final val TABLE_WORDS      = "words"
  final val COLUMN_API       = "api"
  final val COLUMN_INPUT     = "input"
  final val COLUMN_RESULT    = "result"
  final val COLUMN_SEPARATOR = "separator"
  final val COLUMN_HISTORY   = "history"

  final val SORT_DEFAULT = Seq(COLUMN_HISTORY, COLUMN_API, COLUMN_SEPARATOR).mkString(", ")

  private def addSeparator(db: SQLiteDatabase, api: String, input: String): Long = {
    val values = new ContentValues()
    values.put(COLUMN_API, api)
    values.put(COLUMN_INPUT, input)
    values.put(COLUMN_SEPARATOR, 0.0)

    db.insert(TABLE_WORDS, null, values)
  }

  private def addWord(db: SQLiteDatabase, api: String, input: String, word: String): Long = {
    val values = new ContentValues()
    values.put(COLUMN_API, api)
    values.put(COLUMN_INPUT, input)
    values.put(COLUMN_RESULT, word)

    db.insert(TABLE_WORDS, null, values)
  }

  def addWords(db: SQLiteDatabase, api: String, input: String, words: Seq[String]): Int = {
    addSeparator(db, api, input)
    words.map(word => addWord(db, api, input, word)).count(result => result != -1L)
  }
}

package zlc.season.rxdownload3.database

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import io.reactivex.Maybe
import io.reactivex.schedulers.Schedulers.newThread
import zlc.season.rxdownload3.core.Mission
import zlc.season.rxdownload3.core.RealMission
import zlc.season.rxdownload3.helper.loge


open class SQLiteActor(context: Context) : DbActor {
    private val DATABASE_NAME = "RxDownload.db"
    private val DATABASE_VERSION = 1

    private val RANGE_FLAG_NULL = 0
    private val RANGE_FLAG_FALSE = 1
    private val RANGE_FLAG_TRUE = 2

    protected val TABLE_NAME = "missions"

    protected val TAG = "tag"
    protected val URL = "url"
    protected val SAVE_NAME = "save_name"
    protected val SAVE_PATH = "save_path"
    protected val RANGE_FLAG = "range_flag"
    protected val TOTAL_SIZE = "total_size"

    private val sqLiteOpenHelper = object : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {
        override fun onCreate(db: SQLiteDatabase?) {
            if (db == null) return
            execSql(db, provideCreateSql())
        }

        override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
            if (db == null) return
            if (newVersion > oldVersion) {
                execSql(db, provideUpdateSql())
            }
        }

        private fun execSql(db: SQLiteDatabase, sql: String) {
            db.beginTransaction()
            try {
                db.execSQL(sql)
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
    }


    open fun provideCreateSql(): String {
        return """
            CREATE TABLE $TABLE_NAME (
                $TAG TEXT PRIMARY KEY NOT NULL,
                $URL TEXT NOT NULL,
                $SAVE_NAME TEXT,
                $SAVE_PATH TEXT,
                $RANGE_FLAG INTEGER,
                $TOTAL_SIZE TEXT)
            """
    }

    open fun provideUpdateSql(): String {
        return ""
    }

    override fun isExists(mission: RealMission): Boolean {
        val actual = mission.actual
        val readableDatabase = sqLiteOpenHelper.readableDatabase
        val cursor = readableDatabase.rawQuery(
                "SELECT $TAG FROM $TABLE_NAME where $TAG = ?",
                arrayOf(actual.tag)
        )
        cursor.use {
            cursor.moveToFirst()
            return cursor.count != 0
        }
    }

    override fun create(mission: RealMission) {
        val writableDatabase = sqLiteOpenHelper.writableDatabase
        val cv = onCreate(mission)
        writableDatabase.insert(TABLE_NAME, null, cv)
    }

    open fun onCreate(mission: RealMission): ContentValues {
        val actual = mission.actual
        val cv = ContentValues()
        cv.put(TAG, actual.tag)
        cv.put(URL, actual.url)
        cv.put(SAVE_NAME, actual.saveName)
        cv.put(SAVE_PATH, actual.savePath)

        cv.put(RANGE_FLAG, transformFlagToInt(actual.rangeFlag))
        cv.put(TOTAL_SIZE, mission.totalSize)
        return cv
    }

    override fun update(mission: RealMission) {
        val writableDatabase = sqLiteOpenHelper.writableDatabase
        val cv = onUpdate(mission)
        writableDatabase.update(TABLE_NAME, cv, "$TAG=?", arrayOf(mission.actual.tag))
    }

    open fun onUpdate(mission: RealMission): ContentValues {
        val actual = mission.actual
        val cv = ContentValues()
        cv.put(SAVE_NAME, actual.saveName)
        cv.put(SAVE_PATH, actual.savePath)
        cv.put(RANGE_FLAG, transformFlagToInt(actual.rangeFlag))
        cv.put(TOTAL_SIZE, mission.totalSize)
        return cv
    }

    override fun read(mission: RealMission) {
        val readableDatabase = sqLiteOpenHelper.readableDatabase
        val cursor = readableDatabase.rawQuery("SELECT * FROM $TABLE_NAME where $TAG = ?",
                arrayOf(mission.actual.tag)
        )

        cursor.use {
            cursor.moveToFirst()
            if (cursor.count == 0) {
                return
            }
            onRead(cursor, mission)
        }
    }

    open fun onRead(cursor: Cursor, mission: RealMission) {
        val saveName = cursor.getString(cursor.getColumnIndexOrThrow(SAVE_NAME))
        val savePath = cursor.getString(cursor.getColumnIndexOrThrow(SAVE_PATH))
        val rangeFlag = cursor.getInt(cursor.getColumnIndexOrThrow(RANGE_FLAG))
        val totalSize = cursor.getLong(cursor.getColumnIndexOrThrow(TOTAL_SIZE))

        val actual = mission.actual
        actual.saveName = saveName
        actual.savePath = savePath
        actual.rangeFlag = transformFlagToBool(rangeFlag)
        mission.totalSize = totalSize
    }

    override fun delete(mission: RealMission) {
        val actual = mission.actual
        val writableDatabase = sqLiteOpenHelper.writableDatabase
        writableDatabase.delete(TABLE_NAME, "$TAG=?", arrayOf(actual.tag))
    }

    override fun getAllMission(): Maybe<List<Mission>> {
        return Maybe.create<List<Mission>> { emitter ->
            val readableDatabase = sqLiteOpenHelper.readableDatabase
            val cursor = readableDatabase.rawQuery("SELECT * FROM $TABLE_NAME", null)

            cursor.use {
                val list = mutableListOf<Mission>()
                while (cursor.moveToNext()) {
                    list.add(onGetAllMission(cursor))
                }
                emitter.onSuccess(list)
            }
        }.subscribeOn(newThread()).doOnError { loge("get all mission error", it) }
    }

    open fun onGetAllMission(cursor: Cursor): Mission {
        val tag = cursor.getString(cursor.getColumnIndexOrThrow(TAG))
        val url = cursor.getString(cursor.getColumnIndexOrThrow(URL))
        val saveName = cursor.getString(cursor.getColumnIndexOrThrow(SAVE_NAME))
        val savePath = cursor.getString(cursor.getColumnIndexOrThrow(SAVE_PATH))
        val rangeFlag = cursor.getInt(cursor.getColumnIndexOrThrow(RANGE_FLAG))
        val totalSize = cursor.getLong(cursor.getColumnIndexOrThrow(TOTAL_SIZE))

        return Mission(url, saveName, savePath, transformFlagToBool(rangeFlag), tag)
    }

    private fun transformFlagToInt(rangeFlag: Boolean?): Int {
        return when (rangeFlag) {
            true -> RANGE_FLAG_TRUE
            false -> RANGE_FLAG_FALSE
            else -> -RANGE_FLAG_NULL
        }
    }

    private fun transformFlagToBool(flag: Int): Boolean? {
        return when (flag) {
            RANGE_FLAG_TRUE -> true
            RANGE_FLAG_FALSE -> false
            else -> null
        }
    }
}
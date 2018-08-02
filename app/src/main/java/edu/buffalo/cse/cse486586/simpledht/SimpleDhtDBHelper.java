package edu.buffalo.cse.cse486586.simpledht;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * Created by swati on 4/4/18.
 *
 * SimpleDhtDBHelper manages database creation and version management
 */

//Citation: https://developer.android.com/training/data-storage/sqlite.html#java

public class SimpleDhtDBHelper extends SQLiteOpenHelper {
    private static final String TAG = "SimpleDhtDBHelper";
    private static final String TABLE_NAME = "content"; //table name
    private static final String COLUMN_KEY = "key";
    private static final String COLUMN_VALUE = "value";

    private static final String SQL_CREATE_TABLE = "CREATE TABLE " + TABLE_NAME + " (" + COLUMN_KEY + " TEXT PRIMARY KEY, "
            + COLUMN_VALUE + " TEXT)";

    public SimpleDhtDBHelper(Context context, String dbName, SQLiteDatabase.CursorFactory factory, int dbVersion){
        super(context, dbName, null, dbVersion);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
     //   Log.d(TAG, "Inside onCreate: "+SQL_CREATE_TABLE);
        db.execSQL(SQL_CREATE_TABLE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

    }
}

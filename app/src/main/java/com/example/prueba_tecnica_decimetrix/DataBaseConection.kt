package com.example.prueba_tecnica_decimetrix

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.prueba_tecnica_decimetrix.model.FavoritePoint

private const val DATABASE_NAME = "favorite_points.db"
private const val DATABASE_VERSION = 1
private const val TABLE_NAME = "favorites"

private const val COLUMN_ID = "id"
private const val COLUMN_NAME = "name"
private const val COLUMN_LONGITUDE = "longitude"
private const val COLUMN_LATITUDE = "latitude"
private const val COLUMN_IS_ALERT_POINT = "is_alert_point"

class DataBaseConection (context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION){

        override fun onCreate(db: SQLiteDatabase) {
        val createTableQuery = "CREATE TABLE $TABLE_NAME ($COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT, $COLUMN_NAME TEXT, $COLUMN_LONGITUDE REAL, $COLUMN_LATITUDE REAL, $COLUMN_IS_ALERT_POINT INTEGER)"
        db.execSQL(createTableQuery)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_NAME")
        onCreate(db)
    }

    fun saveFavoritePoint(place: FavoritePoint) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_NAME, place.name)
            put(COLUMN_LONGITUDE, place.longitude)
            put(COLUMN_LATITUDE, place.latitude)
            put(COLUMN_IS_ALERT_POINT, if (place.isAlertPoint) 1 else 0)
        }
        db.insert(TABLE_NAME, null, values)
        db.close()
    }

    fun getAllFavorites(): MutableList<FavoritePoint> {
        val favoriteList = mutableListOf<FavoritePoint>()
        val db = readableDatabase
        val cursor: Cursor = db.query(TABLE_NAME, null, null, null, null, null, null)
        cursor.use {
            if (it.moveToFirst()) {
                while (!it.isAfterLast) {
                    val id = it.getInt(it.getColumnIndexOrThrow(COLUMN_ID))
                    val name = it.getString(it.getColumnIndexOrThrow(COLUMN_NAME))
                    val longitude = it.getDouble(it.getColumnIndexOrThrow(COLUMN_LONGITUDE))
                    val latitude = it.getDouble(it.getColumnIndexOrThrow(COLUMN_LATITUDE))
                    val isAlertPoint  = it.getInt(it.getColumnIndexOrThrow(COLUMN_IS_ALERT_POINT)) == 1
                    favoriteList.add(FavoritePoint(id, name, longitude, latitude, isAlertPoint))
                    it.moveToNext()
                }
            }
        }
        cursor.close()
        db.close()
        return favoriteList
    }
}
package com.qmurzik.tokskip.core.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(entities = [VideoEntry::class], version = 1, exportSchema = true)
@TypeConverters(Converters::class)
abstract class TokSkipDb : RoomDatabase() {
    abstract fun videos(): VideoDao

    companion object {
        fun create(context: Context): TokSkipDb =
            Room.databaseBuilder(context.applicationContext, TokSkipDb::class.java, "tokskip.db").build()
    }
}

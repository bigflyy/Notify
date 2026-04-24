package com.example.notify.db;

import androidx.room.Database;
import androidx.room.RoomDatabase;

@Database(entities = {NoteEntity.class, EntryEntity.class, TagEntity.class, NoteTagEntity.class}, version = 2)
public abstract class AppDatabase extends RoomDatabase {
    public abstract NoteDao noteDao();
}

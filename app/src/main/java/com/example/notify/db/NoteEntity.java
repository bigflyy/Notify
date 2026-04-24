package com.example.notify.db;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "notes")
public class NoteEntity {
    @PrimaryKey(autoGenerate = true)
    public int id;
    public String title;
    public long createdAt;
}

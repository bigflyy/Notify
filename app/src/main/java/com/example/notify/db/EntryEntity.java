package com.example.notify.db;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
    tableName = "entries",
    foreignKeys = @ForeignKey(
        entity = NoteEntity.class,
        parentColumns = "id",
        childColumns = "noteId",
        onDelete = ForeignKey.CASCADE
    ),
    indices = {@Index("noteId"), @Index("sortOrder")}
)
public class EntryEntity {
    @PrimaryKey(autoGenerate = true)
    public int id;
    public int noteId;
    public int sortOrder;
    public long createdAt;
    
    // Type: 0 for Text, 1 for Audio
    public int type;
    
    // Text Entry fields
    public String textContent;
    
    // Audio Entry fields
    public String audioPath;
    public String transcription;
    public Double duration;
}

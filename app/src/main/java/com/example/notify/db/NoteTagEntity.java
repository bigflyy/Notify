package com.example.notify.db;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey; // Add this import

@Entity(
        tableName = "note_tags",
        foreignKeys = {
                @ForeignKey(
                        entity = NoteEntity.class,
                        parentColumns = "id",
                        childColumns = "noteId",
                        onDelete = ForeignKey.CASCADE
                ),
                @ForeignKey(
                        entity = TagEntity.class,
                        parentColumns = "id",
                        childColumns = "tagId",
                        onDelete = ForeignKey.CASCADE
                )
        },
        indices = {@Index("noteId"), @Index("tagId")}
)
public class NoteTagEntity {
    @PrimaryKey(autoGenerate = true) // Add this primary key
    public int id;

    public int noteId;
    public int tagId;
}
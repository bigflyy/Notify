package com.example.notify.db;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;

@Entity(
    tableName = "note_tags",
    primaryKeys = {"noteId", "tagId"},
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
    public int noteId;
    public int tagId;
}

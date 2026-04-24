package com.example.notify.db;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.Update;

import java.util.List;

@Dao
public interface NoteDao {
    @Insert
    long insertNote(NoteEntity note);

    @Update
    void updateNote(NoteEntity note);

    @Delete
    void deleteNote(NoteEntity note);

    @Query("SELECT * FROM notes WHERE id = :id")
    NoteEntity getNoteById(int id);

    @Query("SELECT * FROM tags WHERE id = :id")
    TagEntity getTagById(int id);

    @Query("SELECT * FROM notes ORDER BY createdAt DESC")
    List<NoteEntity> getAllNotes();

    @Insert
    long insertEntry(EntryEntity entry);

    @Update
    void updateEntry(EntryEntity entry);

    @Delete
    void deleteEntry(EntryEntity entry);

    @Query("SELECT * FROM entries WHERE noteId = :noteId ORDER BY sortOrder ASC")
    List<EntryEntity> getEntriesForNote(int noteId);

    @Query("DELETE FROM entries WHERE noteId = :noteId")
    void deleteEntriesForNote(int noteId);

    @Insert
    void insertTag(TagEntity tag);

    @Query("SELECT * FROM tags WHERE name = :name")
    TagEntity getTagByName(String name);

    @Insert
    void insertNoteTag(NoteTagEntity noteTag);
    @Delete
    void deleteTag(TagEntity tag);

    @Query("SELECT t.* FROM tags t " +
            "LEFT JOIN note_tags nt ON t.id = nt.tagId " +
            "GROUP BY t.id " +
            "ORDER BY COUNT(nt.tagId) DESC")
    List<TagEntity> getAllTagsSortedByFrequency();

    @Query("SELECT t.* FROM tags t " +
            "INNER JOIN note_tags nt ON t.id = nt.tagId " +
            "WHERE nt.noteId = :noteId " +
            "ORDER BY nt.id ASC") // Sort by the link's creation order
    List<TagEntity> getTagsForNote(int noteId);

    @Query("DELETE FROM note_tags WHERE noteId = :noteId")
    void deleteNoteTagsForNote(int noteId);



    @Query("DELETE FROM note_tags WHERE tagId = :tagId")
    void deleteLinksByTagId(int tagId);
}

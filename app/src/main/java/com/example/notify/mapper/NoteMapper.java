package com.example.notify.mapper;

import com.example.notify.db.EntryEntity;
import com.example.notify.db.NoteDao;
import com.example.notify.db.NoteEntity;
import com.example.notify.db.NoteTagEntity;
import com.example.notify.db.TagEntity;
import com.example.notify.domain.AudioEntry;
import com.example.notify.domain.Entry;
import com.example.notify.domain.Note;
import com.example.notify.domain.Tag;
import com.example.notify.domain.TextEntry;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class NoteMapper {
    private final NoteDao noteDao;

    public NoteMapper(NoteDao noteDao) {
        this.noteDao = noteDao;
    }

    public NoteDao getDao() {
        return noteDao;
    }

    public Note get(int id) {
        NoteEntity noteEntity = noteDao.getNoteById(id);
        if (noteEntity == null) return null;

        Note note = new Note(noteEntity.id, noteEntity.title, new Date(noteEntity.createdAt), new Date(noteEntity.lastModifiedAt));

        List<EntryEntity> entryEntities = noteDao.getEntriesForNote(id);
        for (EntryEntity ee : entryEntities) {
            if (ee.type == 0) {
                note.addEntry(new TextEntry(ee.id, ee.sortOrder, new Date(ee.createdAt), ee.textContent));
            } else {
                note.addEntry(new AudioEntry(ee.id, ee.sortOrder, new Date(ee.createdAt), ee.audioPath, ee.transcription, ee.duration));
            }
        }

        List<TagEntity> tagEntities = noteDao.getTagsForNote(id);
        for (TagEntity te : tagEntities) {
            note.addTag(new Tag(te.id, te.name));
        }

        return note;
    }

    public void insert(Note note) {
        NoteEntity ne = new NoteEntity();
        ne.title = note.getTitle();
        ne.createdAt = note.getCreatedAt().getTime();
        ne.lastModifiedAt = note.getLastModifiedAt() != null ? note.getLastModifiedAt().getTime() : ne.createdAt;
        int noteId = (int) noteDao.insertNote(ne);
        note.setId(noteId);

        for (Entry entry : note.getEntries()) {
            insertEntry(noteId, entry);
        }

        for (Tag tag : note.getTags()) {
            int tagId = getOrInsertTag(tag);
            NoteTagEntity nte = new NoteTagEntity();
            nte.noteId = noteId;
            nte.tagId = tagId;
            noteDao.insertNoteTag(nte);
        }
    }

    public void update(Note note) {
        NoteEntity ne = new NoteEntity();
        ne.id = note.getId();
        ne.title = note.getTitle();
        ne.createdAt = note.getCreatedAt().getTime();
        ne.lastModifiedAt = note.getLastModifiedAt() != null ? note.getLastModifiedAt().getTime() : System.currentTimeMillis();
        noteDao.updateNote(ne);

        // Instead of deleting everything, we handle entries more carefully
        List<EntryEntity> existingEntities = noteDao.getEntriesForNote(note.getId());
        
        // Track which IDs we still need
        List<Integer> currentEntryIds = new ArrayList<>();
        for (Entry entry : note.getEntries()) {
            if (entry.getId() != null) {
                currentEntryIds.add(entry.getId());
                updateEntry(note.getId(), entry);
            } else {
                int newId = (int) insertEntry(note.getId(), entry);
                entry.setId(newId);
                currentEntryIds.add(newId);
            }
        }

        // Delete entries that are no longer in the note
        for (EntryEntity ee : existingEntities) {
            if (!currentEntryIds.contains(ee.id)) {
                noteDao.deleteEntry(ee);
            }
        }

        noteDao.deleteNoteTagsForNote(note.getId());
        for (Tag tag : note.getTags()) {
            int tagId = getOrInsertTag(tag);
            NoteTagEntity nte = new NoteTagEntity();
            nte.noteId = note.getId();
            nte.tagId = tagId;
            noteDao.insertNoteTag(nte);
        }
    }

    public void delete(Note note) {
        NoteEntity ne = new NoteEntity();
        ne.id = note.getId();
        noteDao.deleteNote(ne);
    }

    private void updateEntry(int noteId, Entry entry) {
        EntryEntity ee = new EntryEntity();
        ee.id = entry.getId();
        ee.noteId = noteId;
        ee.sortOrder = entry.getSortOrder();
        ee.createdAt = entry.getCreatedAt().getTime();
        if (entry instanceof TextEntry) {
            ee.type = 0;
            ee.textContent = ((TextEntry) entry).getText();
        } else if (entry instanceof AudioEntry) {
            ee.type = 1;
            ee.audioPath = ((AudioEntry) entry).getAudioPath();
            ee.transcription = ((AudioEntry) entry).getTranscription();
            ee.duration = ((AudioEntry) entry).getDuration();
        }
        noteDao.updateEntry(ee);
    }

    public long insertEntry(int noteId, Entry entry) {
        EntryEntity ee = new EntryEntity();
        ee.noteId = noteId;
        ee.sortOrder = entry.getSortOrder();
        ee.createdAt = entry.getCreatedAt().getTime();
        if (entry instanceof TextEntry) {
            ee.type = 0;
            ee.textContent = ((TextEntry) entry).getText();
        } else if (entry instanceof AudioEntry) {
            ee.type = 1;
            ee.audioPath = ((AudioEntry) entry).getAudioPath();
            ee.transcription = ((AudioEntry) entry).getTranscription();
            ee.duration = ((AudioEntry) entry).getDuration();
        }
        return noteDao.insertEntry(ee);
    }

    private int getOrInsertTag(Tag tag) {
        TagEntity existing = noteDao.getTagByName(tag.getName());
        if (existing != null) {
            return existing.id;
        }
        TagEntity te = new TagEntity();
        te.name = tag.getName();
        noteDao.insertTag(te);
        return noteDao.getTagByName(tag.getName()).id;
    }
}

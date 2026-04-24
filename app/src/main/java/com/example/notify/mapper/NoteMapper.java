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
    // Шлюз, который инкапсулирует доступ к источнику данных (БД)
    private final NoteDao noteDao;
    public NoteMapper(NoteDao noteDao) {
        this.noteDao = noteDao;
    }

    public Note get(int id) {
        NoteEntity noteEntity = noteDao.getNoteById(id);
        if (noteEntity == null) return null;

        Note note = new Note(noteEntity.id, noteEntity.title, new Date(noteEntity.createdAt));

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

    public List<Note> getAllNotes() {
        // 1. Получаем список всех базовых сущностей из базы
        List<NoteEntity> entities = noteDao.getAllNotes();

        List<Note> notes = new ArrayList<>();
        for (NoteEntity entity : entities) {
            // 2. Вызываем внутренний метод get(id), который уже умеет
            // подтягивать записи (entries) и теги (tags).
            notes.add(this.get(entity.id));
        }
        return notes;
    }
    public List<Tag> getAllTags() {
        // 1Обращаемся к Шлюзу (DAO) за сырыми данными
        List<TagEntity> entities = noteDao.getAllTagsSortedByFrequency();

        List<Tag> tags = new ArrayList<>();
        for (TagEntity entity : entities) {
            // Вызываем внутренний метод get(id), который уже умеет
            // подтягивать записи (entries) и теги (tags).
            tags.add(this.getTag(entity.id));
        }
        return tags;
    }
    public Tag getTag(int id) {
        TagEntity te = noteDao.getTagById(id);
        if (te == null) return null;
        return new Tag(te.id, te.name);
    }
    public void deleteTag(Tag tag) {
        TagEntity te = new TagEntity();
        te.id = tag.getId(); // Нам нужен только ID
        // Удалить все связи
        noteDao.deleteLinksByTagId(te.id);
        // Поле name можно даже не заполнять, если в DAO @Delete настроен по PrimaryKey
        // Удалить сам тэг
        noteDao.deleteTag(te);
    }
    public void insertTag(Tag tag) {
        TagEntity te = new TagEntity();
        // Если ID автогенерируемый (0), Room сам присвоит новый
        te.id = tag.getId();
        te.name = tag.getName();

        noteDao.insertTag(te);
    }
    // Вспомогательные методы
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
    private long insertEntry(int noteId, Entry entry) {
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

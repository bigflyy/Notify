package com.example.notify.ui;

import android.app.Application;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.room.Room;

import com.example.notify.db.AppDatabase;
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
import com.example.notify.stt.SpeechTranscriber;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainViewModelNoPattern extends AndroidViewModel {
    private final MutableLiveData<Boolean> isRecording = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> isEngineReady = new MutableLiveData<>(false);
    private final AtomicBoolean isTranscribing = new AtomicBoolean(false);

    private final NoteDao noteDao;
    private final SpeechTranscriber transcriber;

    private final MutableLiveData<List<Note>> allNotes = new MutableLiveData<>();
    private final MutableLiveData<List<Tag>> allTags = new MutableLiveData<>();
    private final MutableLiveData<Note> currentNote = new MutableLiveData<>();
    private final Handler debounceHandler = new Handler(Looper.getMainLooper());
    private Runnable saveRunnable;

    private AudioTrack currentAudioTrack;
    private volatile boolean isPlaying = false;
    private final MutableLiveData<String> playingAudioPath = new MutableLiveData<>(null);
    private final MutableLiveData<Integer> playbackPosition = new MutableLiveData<>(0);
    private final MutableLiveData<Integer> playbackDuration = new MutableLiveData<>(0);
    private volatile int seekToSeconds = -1;

    private Integer pendingAnchorId = null;
    private Entry pendingAnchorEntry = null;

    public MainViewModelNoPattern(Application application) {
        super(application);
        AppDatabase db = Room.databaseBuilder(application, AppDatabase.class, "notify-db")
                .fallbackToDestructiveMigration()
                .allowMainThreadQueries()
                .build();
        this.noteDao = db.noteDao();
        this.transcriber = new SpeechTranscriber(null);

        new Thread(() -> {
            if (transcriber.init(application)) {
                isEngineReady.postValue(true);
            }
        }).start();
        loadNotes();
        loadTags();
    }

    // --- INTERNAL DB LOGIC (THE INLINE MAPPER) ---

    private Note convertToDomain(int noteId) {
        NoteEntity ne = noteDao.getNoteById(noteId);
        if (ne == null) return null;
        Note note = new Note(ne.id, ne.title, new Date(ne.createdAt));

        List<EntryEntity> entries = noteDao.getEntriesForNote(noteId);
        for (EntryEntity ee : entries) {
            if (ee.type == 0) {
                note.addEntry(new TextEntry(ee.id, ee.sortOrder, new Date(ee.createdAt), ee.textContent));
            } else {
                note.addEntry(new AudioEntry(ee.id, ee.sortOrder, new Date(ee.createdAt), ee.audioPath, ee.transcription, ee.duration));
            }
        }
        List<TagEntity> tags = noteDao.getTagsForNote(noteId);
        for (TagEntity te : tags) {
            note.addTag(new Tag(te.id, te.name));
        }
        return note;
    }

    private void saveNoteToDb(Note note) {
        NoteEntity ne = new NoteEntity();
        ne.id = (note.getId() == null) ? 0 : note.getId();
        ne.title = note.getTitle();
        ne.createdAt = note.getCreatedAt().getTime();

        if (note.getId() == null) {
            note.setId((int) noteDao.insertNote(ne));
        } else {
            noteDao.updateNote(ne);
        }

        List<EntryEntity> existing = noteDao.getEntriesForNote(note.getId());
        List<Integer> currentIds = new ArrayList<>();

        for (Entry entry : note.getEntries()) {
            EntryEntity ee = new EntryEntity();
            ee.id = (entry.getId() == null) ? 0 : entry.getId();
            ee.noteId = note.getId();
            ee.sortOrder = entry.getSortOrder();
            ee.createdAt = entry.getCreatedAt().getTime();

            if (entry instanceof TextEntry) {
                ee.type = 0;
                ee.textContent = ((TextEntry) entry).getText();
            } else {
                ee.type = 1;
                AudioEntry ae = (AudioEntry) entry;
                ee.audioPath = ae.getAudioPath();
                ee.transcription = ae.getTranscription();
                ee.duration = ae.getDuration();
            }

            if (entry.getId() == null) {
                entry.setId((int) noteDao.insertEntry(ee));
            } else {
                noteDao.updateEntry(ee);
            }
            currentIds.add(entry.getId());
        }

        for (EntryEntity old : existing) {
            if (!currentIds.contains(old.id)) noteDao.deleteEntry(old);
        }

        noteDao.deleteNoteTagsForNote(note.getId());
        for (Tag tag : note.getTags()) {
            TagEntity te = noteDao.getTagByName(tag.getName());
            int tagId;
            if (te == null) {
                TagEntity newTe = new TagEntity();
                newTe.name = tag.getName();
                noteDao.insertTag(newTe);
                tagId = noteDao.getTagByName(tag.getName()).id;
            } else {
                tagId = te.id;
            }
            NoteTagEntity nte = new NoteTagEntity();
            nte.noteId = note.getId();
            nte.tagId = tagId;
            noteDao.insertNoteTag(nte);
        }
    }

    // --- PUBLIC METHODS (EXACT COPY OF ORIGINAL) ---

    public void loadNotes() {
        new Thread(() -> {
            List<NoteEntity> entities = noteDao.getAllNotes();
            List<Note> domainNotes = new ArrayList<>();
            for (NoteEntity ne : entities) domainNotes.add(convertToDomain(ne.id));
            allNotes.postValue(domainNotes);
        }).start();
    }

    public void loadTags() {
        new Thread(() -> {
            List<TagEntity> entities = noteDao.getAllTagsSortedByFrequency();
            List<Tag> domainTags = new ArrayList<>();
            for (TagEntity te : entities) domainTags.add(new Tag(te.id, te.name));
            allTags.postValue(domainTags);
        }).start();
    }

    public void selectNote(Note note) {
        if (note == null) {
            if (Boolean.TRUE.equals(isRecording.getValue())) stopAudioRecording();
            stopAudio();
        }
        currentNote.setValue(note);
    }

    public void addTextEntry() {
        Note note = currentNote.getValue();
        if (note != null) {
            note.addEntry(new TextEntry(null, note.getEntries().size(), new Date(), ""));
            new Thread(() -> {
                saveNoteToDb(note);
                currentNote.postValue(convertToDomain(note.getId()));
                loadNotes();
            }).start();
        }
    }

    public void updateNoteTitle(String title) {
        Note note = currentNote.getValue();
        if (note != null) {
            note.setTitle(title);
            scheduleSave(note);
        }
    }

    public void updateEntryContent(int index, String content) {
        Note note = currentNote.getValue();
        if (note != null && index >= 0 && index < note.getEntries().size()) {
            Entry entry = note.getEntries().get(index);
            if (entry instanceof TextEntry) {
                ((TextEntry) entry).setText(content);
                scheduleSave(note);
            }
        }
    }

    private void scheduleSave(Note note) {
        if (saveRunnable != null) debounceHandler.removeCallbacks(saveRunnable);
        saveRunnable = () -> new Thread(() -> {
            saveNoteToDb(note);
            loadNotes();
        }).start();
        debounceHandler.postDelayed(saveRunnable, 500);
    }

    public void deleteNote(Note note) {
        new Thread(() -> {
            for (Entry e : note.getEntries()) {
                if (e instanceof AudioEntry) new File(((AudioEntry) e).getAudioPath()).delete();
            }
            NoteEntity ne = new NoteEntity();
            ne.id = note.getId();
            noteDao.deleteNote(ne);
            loadNotes();
        }).start();
    }

    public void deleteEntry(int index) {
        Note note = currentNote.getValue();
        if (note != null && index >= 0 && index < note.getEntries().size()) {
            Entry entry = note.getEntries().remove(index);
            if (entry instanceof AudioEntry) new File(((AudioEntry) entry).getAudioPath()).delete();
            for (int i = 0; i < note.getEntries().size(); i++) note.getEntries().get(i).setSortOrder(i);
            new Thread(() -> {
                saveNoteToDb(note);
                currentNote.postValue(convertToDomain(note.getId()));
                loadNotes();
            }).start();
        }
    }

    public void moveEntry(int fromIndex, int toIndex) {
        Note note = currentNote.getValue();
        if (note != null && fromIndex >= 0 && toIndex >= 0 && fromIndex < note.getEntries().size() && toIndex < note.getEntries().size()) {
            List<Entry> entries = new ArrayList<>(note.getEntries());
            Entry moved = entries.remove(fromIndex);
            entries.add(toIndex, moved);
            for (int i = 0; i < entries.size(); i++) entries.get(i).setSortOrder(i);
            note.setEntries(entries);

            Note updated = new Note(note.getId(), note.getTitle(), note.getCreatedAt());
            updated.setEntries(entries);
            currentNote.setValue(updated);
        }
    }

    public void commitMove() {
        Note note = currentNote.getValue();
        if (note != null) new Thread(() -> {
            saveNoteToDb(note);
            loadNotes();
        }).start();
    }

    public void addTag(String name) {
        Note note = currentNote.getValue();
        if (note != null && name != null && !name.trim().isEmpty()) {
            String cleanName = name.trim();
            for (Tag t : note.getTags()) if (t.getName().equalsIgnoreCase(cleanName)) return;
            new Thread(() -> {
                note.getTags().add(new Tag(null, cleanName));
                saveNoteToDb(note);
                currentNote.postValue(convertToDomain(note.getId()));
                loadNotes();
                loadTags();
            }).start();
        }
    }

    public void removeTag(Note note, Tag tag) {
        note.getTags().removeIf(t -> Objects.equals(t.getId(), tag.getId()));
        new Thread(() -> {
            saveNoteToDb(note);
            currentNote.postValue(convertToDomain(note.getId()));
            loadNotes();
            loadTags();
        }).start();
    }

    public void deleteTagFromDatabase(Tag tag) {
        if (tag == null || tag.getId() == null) return;
        new Thread(() -> {
            noteDao.deleteLinksByTagId(tag.getId());
            TagEntity te = new TagEntity();
            te.id = tag.getId();
            noteDao.deleteTag(te);
            loadTags();
            loadNotes();
            Note cur = currentNote.getValue();
            if (cur != null) currentNote.postValue(convertToDomain(cur.getId()));
        }).start();
    }

    public void createNewTextNote() {
        Date now = new Date();
        Note note = new Note(null, "New Note", now);
        note.addEntry(new TextEntry(null, 0, now, ""));
        new Thread(() -> {
            saveNoteToDb(note);
            currentNote.postValue(convertToDomain(note.getId()));
            loadNotes();
        }).start();
    }

    public void splitAndInsertAudio(int entryIndex, int cursorPosition) {
        Note note = currentNote.getValue();
        if (note == null || entryIndex < 0 || entryIndex >= note.getEntries().size()) return;
        Entry entry = note.getEntries().get(entryIndex);
        pendingAnchorId = entry.getId();

        if (entry instanceof AudioEntry) {
            int target = entryIndex;
            while (target + 1 < note.getEntries().size() && note.getEntries().get(target + 1) instanceof AudioEntry) target++;
            pendingAnchorEntry = note.getEntries().get(target);
            startAudioRecording();
            return;
        }

        if (entry instanceof TextEntry) {
            TextEntry te = (TextEntry) entry;
            String text = te.getText();
            String before = text.substring(0, Math.min(cursorPosition, text.length()));
            String after = text.substring(Math.min(cursorPosition, text.length())).trim();
            te.setText(before);
            if (!after.isEmpty()) {
                TextEntry next = new TextEntry(null, entryIndex + 1, new Date(), after);
                note.getEntries().add(entryIndex + 1, next);
                pendingAnchorEntry = te;
            } else {
                int target = entryIndex;
                while (target + 1 < note.getEntries().size() && note.getEntries().get(target + 1) instanceof AudioEntry) target++;
                pendingAnchorEntry = note.getEntries().get(target);
            }
            startAudioRecording();
        }
    }

    public void startAudioRecording() {
        if (!Boolean.TRUE.equals(isEngineReady.getValue()) || isTranscribing.get() || Boolean.TRUE.equals(isRecording.getValue())) return;
        isRecording.setValue(true);
        String path = getApplication().getFilesDir().getAbsolutePath() + "/audio_" + System.currentTimeMillis() + ".pcm";
        transcriber.startRecording(path);
    }

    public void stopAudioRecording() {
        stopAudioRecording(pendingAnchorEntry);
        pendingAnchorEntry = null;
    }

    public void stopAudioRecording(Entry anchor) {
        if (!Boolean.TRUE.equals(isRecording.getValue()) || isTranscribing.getAndSet(true)) {
            isRecording.setValue(false);
            return;
        }
        isRecording.setValue(false);

        String audioPath = transcriber.getLastAudioPath();
        Note note = currentNote.getValue();
        if (note != null) {
            List<Entry> newEntries = new ArrayList<>(note.getEntries());
            int anchorIndex = (anchor != null) ? newEntries.indexOf(anchor) : -1;
            int index;

            if (anchorIndex != -1) {
                index = anchorIndex + 1;
                while (index < newEntries.size() && newEntries.get(index) instanceof AudioEntry) {
                    index++;
                }
            } else {
                index = newEntries.size();
            }

            // 1. Create and add placeholder
            AudioEntry placeholder = new AudioEntry(null, index, new Date(), audioPath, "", 0.0);
            placeholder.setTranscribing(true);
            newEntries.add(index, placeholder);

            for (int i = 0; i < newEntries.size(); i++) {
                newEntries.get(i).setSortOrder(i);
            }

            // Update local state IMMEDIATELY for the UI
            Note immediateNote = new Note(note.getId(), note.getTitle(), note.getCreatedAt());
            immediateNote.setEntries(newEntries);
            immediateNote.setTags(note.getTags());
            currentNote.setValue(immediateNote);

            new Thread(() -> {
                try {
                    // Save the placeholder to DB
                    saveNoteToDb(immediateNote);

                    // 2. Perform transcription (the heavy work)
                    String transcription = transcriber.stopRecording();

                    // 3. Re-fetch from DB to get a FRESH instance and ensure IDs are synced
                    Note finalNote = convertToDomain(note.getId());
                    if (finalNote != null) {
                        for (Entry e : finalNote.getEntries()) {
                            if (e instanceof AudioEntry && audioPath.equals(((AudioEntry) e).getAudioPath())) {
                                ((AudioEntry) e).setTranscription(transcription);
                                ((AudioEntry) e).setTranscribing(false);
                                break;
                            }
                        }
                        // Save the final text to DB
                        saveNoteToDb(finalNote);

                        // Post the fresh object to UI - Compose will see a new reference and re-render
                        debounceHandler.post(() -> {
                            currentNote.setValue(finalNote);
                            loadNotes();
                        });
                    }
                } catch (Exception e) {
                    Log.e("MainViewModelNoPattern", "Error in transcription thread", e);
                } finally {
                    isTranscribing.set(false);
                }
            }).start();
        } else {
            isTranscribing.set(false);
        }
    }

    public void playAudio(String audioPath) {
        if (audioPath.equals(playingAudioPath.getValue())) { stopAudio(); return; }
        stopAudio();
        new Thread(() -> runPlayback(audioPath)).start();
    }

    private void runPlayback(String audioPath) {
        File file = new File(audioPath);
        if (!file.exists()) return;
        isPlaying = true;
        playingAudioPath.postValue(audioPath);
        int sampleRate = 16000;
        int bytesPerSec = sampleRate * 2;
        int duration = (int) (file.length() / bytesPerSec);
        playbackDuration.postValue(duration);
        playbackPosition.postValue(0); // Reset position for new audio

        while (isPlaying) {
            int startSec = (seekToSeconds != -1) ? seekToSeconds : 0;
            seekToSeconds = -1;
            if (currentAudioTrack != null) {
                try { currentAudioTrack.stop(); } catch (Exception e) {}
                currentAudioTrack.release();
            }
            currentAudioTrack = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build())
                    .setAudioFormat(new AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(sampleRate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                    .setBufferSizeInBytes(AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT))
                    .build();
            currentAudioTrack.play();
            try (FileInputStream fis = new FileInputStream(file)) {
                fis.skip((long) startSec * bytesPerSec);
                byte[] buf = new byte[2048];
                int read = 0;
                long total = (long) startSec * bytesPerSec;
                while (isPlaying) {
                    if (seekToSeconds != -1) break;
                    read = fis.read(buf);
                    if (read == -1) {
                        isPlaying = false;
                        break;
                    }
                    currentAudioTrack.write(buf, 0, read);
                    total += read;
                    playbackPosition.postValue((int) (total / bytesPerSec));
                }
            } catch (IOException e) { isPlaying = false; }
        }
        stopAudio();
    }

    public void stopAudio() {
        isPlaying = false;
        seekToSeconds = -1;
        if (currentAudioTrack != null) {
            try { currentAudioTrack.stop(); } catch (Exception e) {}
            currentAudioTrack.release();
            currentAudioTrack = null;
        }
        playingAudioPath.postValue(null);
        playbackPosition.postValue(0);
        playbackDuration.postValue(0);
    }

    public void seekTo(int seconds) { seekToSeconds = seconds; }
    public void skipForward() { Integer cur = playbackPosition.getValue(); if (cur != null) seekToSeconds = cur + 10; }
    public void skipBackward() { Integer cur = playbackPosition.getValue(); if (cur != null) seekToSeconds = Math.max(0, cur - 10); }

    @Override protected void onCleared() { super.onCleared(); stopAudio(); transcriber.free(); }

    // --- GETTERS ---
    public LiveData<List<Note>> getAllNotes() { return allNotes; }
    public LiveData<List<Tag>> getAllTags() { return allTags; }
    public LiveData<Note> getCurrentNote() { return currentNote; }
    public LiveData<Boolean> getIsRecording() { return isRecording; }
    public LiveData<Boolean> getIsEngineReady() { return isEngineReady; }
    public LiveData<String> getPlayingAudioPath() { return playingAudioPath; }
    public LiveData<Integer> getPlaybackPosition() { return playbackPosition; }
    public LiveData<Integer> getPlaybackDuration() { return playbackDuration; }
}
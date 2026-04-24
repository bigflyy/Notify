package com.example.notify.ui;

import android.app.Application;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.media.AudioManager;
import android.util.Log;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.room.Room;

import com.example.notify.db.AppDatabase;
import com.example.notify.db.NoteDao;
import com.example.notify.db.NoteEntity;
import com.example.notify.db.TagEntity;
import com.example.notify.domain.AudioEntry;
import com.example.notify.domain.Entry;
import com.example.notify.domain.Note;
import com.example.notify.domain.Tag;
import com.example.notify.domain.TextEntry;
import com.example.notify.mapper.NoteMapper;
import com.example.notify.stt.SherpaOnnxEngine;
import com.example.notify.stt.SpeechTranscriber;
import com.example.notify.utils.AssetUtils;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;

public class MainViewModel extends AndroidViewModel {
    private final MutableLiveData<Boolean> isRecording = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> isEngineReady = new MutableLiveData<>(false);
    private final NoteMapper noteMapper;
    private final SpeechTranscriber transcriber;
    private final SherpaOnnxEngine sttEngine;

    private final MutableLiveData<List<Note>> allNotes = new MutableLiveData<>();
    private final MutableLiveData<Note> currentNote = new MutableLiveData<>();
    private final Handler debounceHandler = new Handler(Looper.getMainLooper());
    private Runnable saveRunnable;

    // Playback state
    private AudioTrack currentAudioTrack;
    private volatile boolean isPlaying = false;
    private final MutableLiveData<String> playingAudioPath = new MutableLiveData<>(null);
    private final MutableLiveData<Integer> playbackPosition = new MutableLiveData<>(0);
    private final MutableLiveData<Integer> playbackDuration = new MutableLiveData<>(0);
    private volatile int seekToSeconds = -1;
    private Integer pendingAnchorId = null;
    private final LiveData<List<Tag>> allTags;


    public MainViewModel(Application application) {
        super(application);
        AppDatabase db = Room.databaseBuilder(application, AppDatabase.class, "notify-db")
                .fallbackToDestructiveMigration()
                .allowMainThreadQueries()
                .build();
        noteMapper = new NoteMapper(db.noteDao());
        sttEngine = new SherpaOnnxEngine();
        transcriber = new SpeechTranscriber(sttEngine);
        
        new Thread(() -> {
            String modelDir = application.getFilesDir().getAbsolutePath() + "/model";
            try {
                AssetUtils.copyAssets(application, "sherpa-onnx-nemo-transducer-punct-giga-am-v3-russian-2025-12-16", modelDir);
                if (sttEngine.init(application, modelDir)) {
                    isEngineReady.postValue(true);
                }
            } catch (IOException e) {
                Log.e("MainViewModel", "Error loading model", e);
            }
        }).start();


        this.allTags = Transformations.map(
                db.noteDao().getAllTagsSortedByFrequency(),
                noteMapper::toDomainTags
        );
        loadNotes();
    }

    public LiveData<List<Note>> getAllNotes() { return allNotes; }
    public LiveData<List<Tag>> getAllTags() {return  allTags; }
    public LiveData<Note> getCurrentNote() { return currentNote; }
    public LiveData<Boolean> getIsRecording() { return isRecording; }
    public LiveData<Boolean> getIsEngineReady() { return isEngineReady; }
    public LiveData<String> getPlayingAudioPath() { return playingAudioPath; }
    public LiveData<Integer> getPlaybackPosition() { return playbackPosition; }
    public LiveData<Integer> getPlaybackDuration() { return playbackDuration; }

    public void removeTag(Note note, Tag tag) {
        // 1. Remove from the local list
        note.getTags().removeIf(t -> Objects.equals(t.getId(), tag.getId()));

        new Thread(() -> {
            // 2. Update the DB (NoteMapper handles the junction table sync)
            noteMapper.update(note);

            // 3. Refresh UI
            Note updated = noteMapper.get(note.getId());
            currentNote.postValue(updated);
            loadNotes();
        }).start();
    }
    public void deleteTagFromDatabase(Tag tag) {
        if (tag == null || tag.getId() == null) return;

        new Thread(() -> {
            // Access the DAO through the existing mapper's getter
            NoteDao dao = noteMapper.getDao();

            // 1. Remove all associations from the junction table
            dao.deleteLinksByTagId(tag.getId());

            // 2. Delete the actual tag from the tags table
            TagEntity te = new TagEntity();
            te.id = tag.getId();
            te.name = tag.getName();
            dao.deleteTag(te);

            // 3. Refresh the main screen and current note
            loadNotes();
            Note current = currentNote.getValue();
            if (current != null) {
                currentNote.postValue(noteMapper.get(current.getId()));
            }
        }).start();
    }

    public void loadNotes() {
        new Thread(() -> {
            try {
                List<NoteEntity> entities = noteMapper.getDao().getAllNotes();
                List<Note> notes = new ArrayList<>();
                for (NoteEntity entity : entities) {
                    notes.add(noteMapper.get(entity.id));
                }
                // Use postValue to update the LiveData from a background thread
                allNotes.postValue(notes);
            } catch (Exception e) {
                Log.e("MainViewModel", "Error loading notes", e);
            }
        }).start();
    }
    public void selectNote(Note note) {
        if (note == null) {
            if (Boolean.TRUE.equals(isRecording.getValue())) {
                stopAudioRecording();
            }
            stopAudio();
        }
        currentNote.setValue(note);
    }

    public void deleteEntry(int index) {
        Note note = currentNote.getValue();
        if (note != null && index >= 0 && index < note.getEntries().size()) {
            Entry entry = note.getEntries().remove(index);
            if (entry instanceof AudioEntry) {
                String path = ((AudioEntry) entry).getAudioPath();
                if (path != null) {
                    new File(path).delete();
                }
            }
            // Re-calculate sort orders
            for (int i = 0; i < note.getEntries().size(); i++) {
                note.getEntries().get(i).setSortOrder(i);
            }
            noteMapper.update(note);
            currentNote.setValue(noteMapper.get(note.getId()));
            loadNotes();
        }
    }

    public void addTextEntry() {
        Note note = currentNote.getValue();
        if (note != null) {
            TextEntry entry = new TextEntry(null, note.getEntries().size(), new Date(), "");
            note.getEntries().add(entry);
            noteMapper.update(note);
            currentNote.setValue(noteMapper.get(note.getId()));
            loadNotes();
        }
    }

    public void addTag(String name) {
        Note note = currentNote.getValue();
        if (note != null && name != null && !name.trim().isEmpty()) {
            String trimmedName = name.trim();

            // Check if the note already has this tag (case-insensitive)
            for (Tag existingTag : note.getTags()) {
                if (existingTag.getName().equalsIgnoreCase(trimmedName)) {
                    return; // Tag already exists on this note, do nothing
                }
            }

            // 1. Update the in-memory object immediately for the Editor UI
            note.getTags().add(new Tag(null, trimmedName));

            // This force-updates the LiveData so the UI shows the new tag immediately
            currentNote.setValue(note);

            new Thread(() -> {
                // 2. Perform the heavy DB lift
                noteMapper.update(note);

                // 3. Re-fetch the full note to keep the Editor consistent
                Note updatedNote = noteMapper.get(note.getId());
                currentNote.postValue(updatedNote);

                // 4. Trigger the main list reload
                loadNotes();
            }).start();
        }
    }

    public void moveEntry(int fromIndex, int toIndex) {
        Note note = currentNote.getValue();
        if (note != null && fromIndex >= 0 && fromIndex < note.getEntries().size()
                && toIndex >= 0 && toIndex < note.getEntries().size()) {

            // 1. Create a copy of the list
            List<Entry> newEntries = new ArrayList<>(note.getEntries());

            // 2. Perform the swap
            Entry entry = newEntries.remove(fromIndex);
            newEntries.add(toIndex, entry);

            // 3. Update sort orders
            for (int i = 0; i < newEntries.size(); i++) {
                newEntries.get(i).setSortOrder(i);
            }

            // 4. Update the current note instance
            note.setEntries(newEntries);

            // 5. CRITICAL FIX: Create a NEW Note reference for the LiveData
            // If your Note class doesn't have a copy constructor,
            // you can manually create a new one or re-set fields.
            Note updatedNote = new Note(
                    note.getId(),
                    note.getTitle(),
                    note.getCreatedAt(),
                    new Date() // updated timestamp
            );
            updatedNote.setEntries(newEntries);

            currentNote.setValue(updatedNote);
        }
    }

    // Inside moveEntry: No changes needed to your NEW Note logic, it's correct.

    public void commitMove() {
        Note note = currentNote.getValue();
        if (note != null) {
            new Thread(() -> {
                noteMapper.update(note);
                // REMOVE loadNotes() from here.
                // The UI already has the correct state in currentNote.
                // Just sync the background list for the main screen.
                syncMainListOnly();
            }).start();
        }
    }
    private void syncMainListOnly() {
        new Thread(() -> {
            List<NoteEntity> entities = noteMapper.getDao().getAllNotes();
            List<Note> notes = new ArrayList<>();
            for (NoteEntity entity : entities) {
                notes.add(noteMapper.get(entity.id));
            }
            allNotes.postValue(notes);
        }).start();
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

    public void updateNoteTitle(String title) {
        Note note = currentNote.getValue();
        if (note != null) {
            note.setTitle(title);
            scheduleSave(note);
        }
    }

    private void scheduleSave(Note note) {
        if (saveRunnable != null) debounceHandler.removeCallbacks(saveRunnable);
        saveRunnable = () -> {
            noteMapper.update(note);
            loadNotes();
        };
        debounceHandler.postDelayed(saveRunnable, 500);
    }

    public void deleteNote(Note note) {
        if (note.getEntries() != null) {
            for (Entry entry : note.getEntries()) {
                if (entry instanceof AudioEntry) {
                    String path = ((AudioEntry) entry).getAudioPath();
                    if (path != null) {
                        try {
                            File file = new File(path);
                            if (file.exists()) {
                                file.delete();
                            }
                        } catch (Exception e) {
                            Log.e("MainViewModel", "Failed to delete audio file: " + path, e);
                        }
                    }
                }
            }
        }
        noteMapper.delete(note);
        loadNotes();
    }

    public void startAudioRecording() {
        if (!Boolean.TRUE.equals(isEngineReady.getValue()) || isTranscribing.get() || Boolean.TRUE.equals(isRecording.getValue())) return;
        isRecording.setValue(true);
        String audioPath = getApplication().getFilesDir().getAbsolutePath() + "/audio_" + System.currentTimeMillis() + ".pcm";
        transcriber.startRecording(audioPath);
    }

    private final java.util.concurrent.atomic.AtomicBoolean isTranscribing = new java.util.concurrent.atomic.AtomicBoolean(false);

    public void stopAudioRecording(Entry anchor) {
        if (!Boolean.TRUE.equals(isRecording.getValue()) || isTranscribing.getAndSet(true)) {
            isRecording.setValue(false);
            return;
        }
        isRecording.setValue(false);
        new Thread(() -> {
            try {
                String transcription = transcriber.stopRecording();
                String audioPath = transcriber.getLastAudioPath();

                Note note = currentNote.getValue();
                if (note != null) {
                    List<Entry> newEntries = new ArrayList<>(note.getEntries());

                    int anchorIndex = (anchor != null) ? newEntries.indexOf(anchor) : -1;
                    int index;

                    if (anchorIndex != -1) {
                        // Jump to the end of the current audio group
                        index = anchorIndex + 1;
                        while (index < newEntries.size() && newEntries.get(index) instanceof AudioEntry) {
                            index++;
                        }
                    } else {
                        index = newEntries.size();
                    }

                    AudioEntry newEntry = new AudioEntry(null, index, new Date(), audioPath, transcription, 0.0);
                    newEntries.add(index, newEntry);

                    // Re-calculate sort orders
                    for (int i = 0; i < newEntries.size(); i++) {
                        newEntries.get(i).setSortOrder(i);
                    }

                    // Update the note object with the new list
                    note.setEntries(newEntries);

                    // Save to DB on this background thread
                    noteMapper.update(note);

                    // CRITICAL: Re-fetch a FRESH Note object from the DB to ensure a new reference
                    // This forces Compose to see a different object ID and trigger a full re-render
                    Note updatedNote = noteMapper.get(note.getId());

                    debounceHandler.post(() -> {
                        currentNote.setValue(updatedNote);
                        loadNotes();
                    });
                }
            } catch (Exception e) {
                Log.e("MainViewModel", "Error in stopAudioRecording thread", e);
            } finally {
                isTranscribing.set(false);
            }
        }).start();
    }

    public void splitAndInsertAudio(int entryIndex, int cursorPosition) {
        Note note = currentNote.getValue();
        if (note == null || entryIndex < 0 || entryIndex >= note.getEntries().size()) return;

        Entry entry = note.getEntries().get(entryIndex);
        pendingAnchorId = entry.getId();
        if (entry instanceof AudioEntry) {
            // SCENARIO: Recording while an audio block is selected.
            // Find the end of the current audio stack.
            int targetIndex = entryIndex;
            while (targetIndex + 1 < note.getEntries().size() && note.getEntries().get(targetIndex + 1) instanceof AudioEntry) {
                targetIndex++;
            }
            pendingAnchorEntry = note.getEntries().get(targetIndex);
            startAudioRecording();
            return;
        }

        if (!(entry instanceof TextEntry)) return;

        TextEntry textEntry = (TextEntry) entry;
        String fullText = textEntry.getText();
        String textBefore = fullText.substring(0, Math.min(cursorPosition, fullText.length()));
        String textAfter = fullText.substring(Math.min(cursorPosition, fullText.length())).trim();

        // Update current block with textBefore
        textEntry.setText(textBefore);

        if (!textAfter.isEmpty()) {
            // SCENARIO: Splitting in the middle.
            // Put audio exactly between textBefore and textAfter.
            TextEntry nextTextEntry = new TextEntry(null, entryIndex + 1, new Date(), textAfter);
            note.getEntries().add(entryIndex + 1, nextTextEntry);
            pendingAnchorEntry = textEntry; // Insert after the first half
        } else {
            // SCENARIO: Recording at the end of a block.
            // Find the end of the current audio stack below this text block.
            int targetIndex = entryIndex;
            while (targetIndex + 1 < note.getEntries().size() && note.getEntries().get(targetIndex + 1) instanceof AudioEntry) {
                targetIndex++;
            }
            pendingAnchorEntry = note.getEntries().get(targetIndex);
        }

        startAudioRecording();
    }

    private Entry pendingAnchorEntry = null;

    public void stopAudioRecording() {
        stopAudioRecording(pendingAnchorEntry);
        pendingAnchorEntry = null;
    }

    public void stopAudio() {
        isPlaying = false;
        seekToSeconds = -1; // Reset seek state
        if (currentAudioTrack != null) {
            try {
                currentAudioTrack.stop();
                currentAudioTrack.release();
            } catch (Exception ignored) {}
            currentAudioTrack = null;
        }
        playingAudioPath.postValue(null);
        playbackPosition.postValue(0);
    }

    public void playAudio(String audioPath) {
        if (audioPath.equals(playingAudioPath.getValue())) {
            stopAudio();
            return;
        }
        stopAudio();
        new Thread(() -> runPlayback(audioPath)).start();
    }

    private void runPlayback(String audioPath) {
        File file = new File(audioPath);
        if (!file.exists()) return;

        isPlaying = true;
        playingAudioPath.postValue(audioPath);

        int sampleRate = 16000;
        int bytesPerSecond = sampleRate * 2;
        int duration = (int) (file.length() / bytesPerSecond);
        playbackDuration.postValue(duration);

        int bufferSize = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);

        while (isPlaying) {
            int currentStartSecond = (seekToSeconds != -1) ? seekToSeconds : (playbackPosition.getValue() != null ? playbackPosition.getValue() : 0);
            if (seekToSeconds != -1) seekToSeconds = -1;

            if (currentAudioTrack != null) {
                try {
                    currentAudioTrack.stop();
                    currentAudioTrack.release();
                } catch (Exception ignored) {}
            }

            currentAudioTrack = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build())
                    .setAudioFormat(new AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build())
                    .setBufferSizeInBytes(bufferSize)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build();

            currentAudioTrack.play();

            try (FileInputStream fis = new FileInputStream(file)) {
                fis.skip((long) currentStartSecond * bytesPerSecond);
                DataInputStream dis = new DataInputStream(new BufferedInputStream(fis));
                byte[] buffer = new byte[bufferSize];
                long bytesReadTotal = (long) currentStartSecond * bytesPerSecond;
                int read;

                while (isPlaying) {
                    if (seekToSeconds != -1) {
                        break; // Inner loop break to re-initialize playback from new position
                    }
                    read = dis.read(buffer);
                    if (read == -1) {
                        isPlaying = false; // Finished naturally
                        break;
                    }
                    currentAudioTrack.write(buffer, 0, read);
                    bytesReadTotal += read;
                    playbackPosition.postValue((int) (bytesReadTotal / bytesPerSecond));
                }
            } catch (IOException e) {
                Log.e("MainViewModel", "Playback error", e);
                isPlaying = false;
            }
        }

        // Final cleanup
        if (audioPath.equals(playingAudioPath.getValue())) {
            stopAudio();
        }
    }

    public void seekTo(int seconds) {
        seekToSeconds = seconds;
    }

    public void skipForward() {
        Integer cur = playbackPosition.getValue();
        Integer dur = playbackDuration.getValue();
        if (cur != null && dur != null) seekToSeconds = Math.min(dur, cur + 10);
    }

    public void skipBackward() {
        Integer cur = playbackPosition.getValue();
        if (cur != null) seekToSeconds = Math.max(0, cur - 10);
    }

    public void createNewTextNote() {
        Date now = new Date();
        Note note = new Note(null, "New Note", now, now);
        note.addEntry(new TextEntry(null, 0, now, ""));
        noteMapper.insert(note);
        currentNote.setValue(note);
        loadNotes();
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        stopAudio();
        sttEngine.free();
    }
}

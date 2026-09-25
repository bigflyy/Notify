package com.example.notify.stt;

import android.util.AtomicFile;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** App-private text history; separate from the notes database and source media. */
public final class TranscriptHistory {
    public static final class Entry {
        public final String id;
        public final String fileName;
        public final String text;
        public final long createdAt;
        public final boolean complete;

        public Entry(String id, String fileName, String text, long createdAt, boolean complete) {
            this.id = id;
            this.fileName = fileName;
            this.text = text;
            this.createdAt = createdAt;
            this.complete = complete;
        }
    }

    private final File directory;

    public TranscriptHistory(File filesDirectory) {
        directory = new File(filesDirectory, "transcript_history");
    }

    public synchronized void save(Entry entry) throws IOException {
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IOException("Could not create transcript history directory");
        }
        JSONObject json = new JSONObject();
        try {
            json.put("id", entry.id);
            json.put("fileName", entry.fileName);
            json.put("text", entry.text);
            json.put("createdAt", entry.createdAt);
            json.put("complete", entry.complete);
        } catch (JSONException error) {
            throw new IOException("Could not encode transcript", error);
        }

        AtomicFile file = new AtomicFile(new File(directory, entry.id + ".json"));
        FileOutputStream output = file.startWrite();
        try {
            output.write(json.toString().getBytes(StandardCharsets.UTF_8));
            file.finishWrite(output);
        } catch (IOException | RuntimeException error) {
            file.failWrite(output);
            throw error;
        }
    }

    public synchronized List<Entry> list() throws IOException {
        List<Entry> result = new ArrayList<>();
        if (!directory.exists()) return result;
        File[] files = directory.listFiles((parent, name) -> name.endsWith(".json"));
        if (files == null) throw new IOException("Could not read transcript history");
        for (File file : files) {
            try (FileInputStream input = new AtomicFile(file).openRead()) {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) bytes.write(buffer, 0, count);
                JSONObject json = new JSONObject(bytes.toString(StandardCharsets.UTF_8.name()));
                result.add(new Entry(json.getString("id"), json.getString("fileName"),
                        json.getString("text"), json.getLong("createdAt"),
                        json.getBoolean("complete")));
            } catch (JSONException error) {
                throw new IOException("Could not read transcript history", error);
            }
        }
        result.sort(Comparator.comparingLong((Entry entry) -> entry.createdAt).reversed());
        return result;
    }

    public synchronized void delete(String id) {
        UUID.fromString(id);
        new AtomicFile(new File(directory, id + ".json")).delete();
    }
}

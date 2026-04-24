package com.example.notify.domain;

import java.util.Date;

public class AudioEntry extends Entry {
    private String audioPath;
    private String transcription;
    private Double duration;

    public AudioEntry(Integer id, Integer sortOrder, Date createdAt, String audioPath, String transcription, Double duration) {
        super(id, sortOrder, createdAt);
        this.audioPath = audioPath;
        this.transcription = transcription;
        this.duration = duration;
    }

    public String getAudioPath() { return audioPath; }
    public void setAudioPath(String audioPath) { this.audioPath = audioPath; }
    public String getTranscription() { return transcription; }
    public void setTranscription(String transcription) { this.transcription = transcription; }
    public Double getDuration() { return duration; }
    public void setDuration(Double duration) { this.duration = duration; }
}

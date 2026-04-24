package com.example.notify.domain;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class Note {
    private Integer id;
    private String title;
    private Date createdAt;
    private Date lastModifiedAt;
    private List<Entry> entries;
    private List<Tag> tags;

    public Note(Integer id, String title, Date createdAt, Date lastModifiedAt) {
        this.id = id;
        this.title = title;
        this.createdAt = createdAt;
        this.lastModifiedAt = lastModifiedAt;
        this.entries = new ArrayList<>();
        this.tags = new ArrayList<>();
    }

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
    public Date getLastModifiedAt() { return lastModifiedAt; }
    public void setLastModifiedAt(Date lastModifiedAt) { this.lastModifiedAt = lastModifiedAt; }
    public List<Entry> getEntries() { return entries; }
    public void setEntries(List<Entry> entries) { this.entries = entries; }
    public List<Tag> getTags() { return tags; }
    public void setTags(List<Tag> tags) { this.tags = tags; }

    public void addEntry(Entry entry) {
        this.entries.add(entry);
    }

    public void addTag(Tag tag) {
        this.tags.add(tag);
    }
}

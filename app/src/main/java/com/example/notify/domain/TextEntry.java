package com.example.notify.domain;

import java.util.Date;

public class TextEntry extends Entry {
    private String text;

    public TextEntry(Integer id, Integer sortOrder, Date createdAt, String text) {
        super(id, sortOrder, createdAt);
        this.text = text;
    }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
}

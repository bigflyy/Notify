package com.example.notify.domain;

import java.util.Date;

public abstract class Entry {
    private Integer id;
    private Integer sortOrder;
    private Date createdAt;

    public Entry(Integer id, Integer sortOrder, Date createdAt) {
        this.id = id;
        this.sortOrder = sortOrder;
        this.createdAt = createdAt;
    }

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
}

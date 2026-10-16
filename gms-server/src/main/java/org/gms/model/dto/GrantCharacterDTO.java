package org.gms.model.dto;

import lombok.Data;

@Data
public class GrantCharacterDTO {
    private int world;
    private int id;
    private String name;
    private int map;
    private int job;
    private String jobName;
    private int level;
    private int gm;
    private boolean online;
    private int pendingCount;
}

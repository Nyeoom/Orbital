package com.appbrim.model;

import com.google.gson.annotations.SerializedName;

/** One lineup result row from lineupsvalorant.com's get_results endpoint. */
public class Lineup {

    private long id;
    private String title;
    private String map;
    private String start;
    private String end;

    @SerializedName("abilities")
    private String abilitiesRaw;

    @SerializedName("num_images")
    private int numImages;

    private long score;

    @SerializedName("unlisted")
    private int unlisted;

    private String type;

    public Lineup() {
    }

    /** For tests and for building rows without going through JSON. */
    public Lineup(long id, String title, String map, String start, String end,
                  String abilities, int numImages, long score) {
        this.id = id;
        this.title = title;
        this.map = map;
        this.start = start;
        this.end = end;
        this.abilitiesRaw = abilities;
        this.numImages = numImages;
        this.score = score;
    }

    public long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getMap() {
        return map;
    }

    public String getStart() {
        return start;
    }

    public String getEnd() {
        return end;
    }

    /** Ability names joined with ':', e.g. "Incendiary". */
    public String getAbilitiesRaw() {
        return abilitiesRaw;
    }

    public int getNumImages() {
        return numImages;
    }

    public long getScore() {
        return score;
    }

    public String getType() {
        return type;
    }
}

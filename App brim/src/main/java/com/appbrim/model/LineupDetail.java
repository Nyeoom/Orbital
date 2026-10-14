package com.appbrim.model;

import com.google.gson.annotations.SerializedName;

/** Full lineup detail from lineupsvalorant.com's get_lineup endpoint. */
public class LineupDetail {

    private long id;

    @SerializedName("lineup_id")
    private long lineupId;

    private String title;
    private String map;
    private String start;
    private String end;
    private String description;
    private String username;

    @SerializedName("num_images")
    private int numImagesAlt;

    private int images;
    private long views;

    @SerializedName("like_count")
    private long likeCount;

    @SerializedName("date_uploaded")
    private double dateUploaded;

    private String error;

    public long getId() {
        return id;
    }

    public long getLineupId() {
        return lineupId != 0 ? lineupId : id;
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

    /** Step-by-step text. May contain {@code <br>} separators and HTML entities. */
    public String getDescription() {
        return description == null ? "" : description;
    }

    public String getUsername() {
        return username;
    }

    public int getImageCount() {
        return images > 0 ? images : numImagesAlt;
    }

    public long getViews() {
        return views;
    }

    public long getLikeCount() {
        return likeCount;
    }

    public String getError() {
        return error;
    }
}

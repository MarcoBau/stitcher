package com.stitchit.sticher;

public class StitchRequest {
    String oauth;
    String folder;

    public String getFolder() {
        return folder;
    }

    public String getOauth() {
        return oauth;
    }

    public void setFolder(final String folder) {
        this.folder = folder;
    }

    public void setOauth(final String oauth) {
        this.oauth = oauth;
    }

    @Override
    public String toString() {
        return "StitchRequest [oauth=" + oauth + ", folder=" + folder + "]";
    }

}

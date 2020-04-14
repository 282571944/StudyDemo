package com.luomin.dubbing;

public class DubbingTimeObj {
    private long start;
    private long end;
    private String sourcePcmFile;

    public long getStart() {
        return start;
    }

    public void setStart(long start) {
        this.start = start;
    }

    public long getEnd() {
        return end;
    }

    public void setEnd(long end) {
        this.end = end;
    }

    public String getSourcePcmFile() {
        return sourcePcmFile;
    }

    public void setSourcePcmFile(String sourcePcmFile) {
        this.sourcePcmFile = sourcePcmFile;
    }
}

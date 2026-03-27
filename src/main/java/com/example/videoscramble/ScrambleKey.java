package com.example.videoscramble;

public record ScrambleKey(int offset, int step) {
    public ScrambleKey {
        if (offset < 0 || offset > 255) {
            throw new IllegalArgumentException("offset r doit être entre 0 et 255");
        }
        if (step < 0 || step > 127) {
            throw new IllegalArgumentException("step s doit être entre 0 et 127");
        }
    }

    public int oddMultiplier() {
        return 2 * step + 1;
    }

    @Override
    public String toString() {
        return "(r=" + offset + ", s=" + step + ")";
    }
}

package com.lcd.edgegest;

/**
 * Phân loại cử chỉ ở mép, thuần Java (không phụ thuộc Android) để unit-test được trên PC.
 * Toạ độ tính theo đơn vị thô của màn cảm ứng.
 */
public final class Gestures {
    public static final String BACK = "BACK";
    public static final String HOME = "HOME";
    public static final String RECENTS = "RECENTS";
    public static final String SWITCH = "SWITCH";

    public int maxX = 121999, maxY = 265599;
    public long holdMs = 350;

    // Tỉ lệ bề rộng dải mép trái/phải (phần của maxX). Chỉnh được qua --edge=.
    public float edgeFrac = 0.05f;

    // Ngưỡng (tính trong compute()).
    public int edgeX, bottomZone, backDist, homeDist, switchDist, topGuard;

    public Gestures() {
        compute();
    }

    public void compute() {
        edgeX = Math.round(maxX * edgeFrac);
        bottomZone = Math.round(maxY * 0.028f);
        backDist = Math.round(maxX * 0.075f);
        homeDist = Math.round(maxY * 0.045f);
        switchDist = Math.round(maxX * 0.28f);
        topGuard = Math.round(maxY * 0.08f);
    }

    /** Trả về tên cử chỉ, hoặc null nếu không khớp. */
    public String classify(int sx, int sy, int ex, int ey, long dur) {
        int dx = ex - sx;
        int dy = ey - sy;       // xuống dương
        int up = -dy;           // lên dương
        int adx = Math.abs(dx), ady = Math.abs(dy);

        if (sy > maxY - bottomZone) {                 // mép dưới
            if (adx >= switchDist && adx > up) return SWITCH;
            if (up >= homeDist) return (dur >= holdMs) ? RECENTS : HOME;
            return null;
        }
        if (sy > topGuard && sy < maxY - bottomZone) { // thân màn hình, hai mép dọc
            if (sx <= edgeX) {
                if (dx >= backDist && adx > ady) return BACK;
            } else if (sx >= maxX - edgeX) {
                if (dx <= -backDist && adx > ady) return BACK;
            }
        }
        return null;
    }
}

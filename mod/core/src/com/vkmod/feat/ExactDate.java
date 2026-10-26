package com.vkmod.feat;

import android.content.res.Resources;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Absolute date formatting used instead of VK's relative "yesterday" / "2 hours ago". */
public final class ExactDate {
    private ExactDate() {}

    public static String format(int ts, Resources res, boolean withTime) {
        Locale loc = res.getConfiguration().getLocales().get(0);
        return format(ts * 1000L, loc, withTime, System.currentTimeMillis());
    }

    /** Pure; [now] decides whether the year is shown. */
    public static String format(long millis, Locale loc, boolean withTime, long now) {
        java.util.Calendar a = java.util.Calendar.getInstance(), b = java.util.Calendar.getInstance();
        a.setTimeInMillis(millis);
        b.setTimeInMillis(now);
        boolean sameYear = a.get(java.util.Calendar.YEAR) == b.get(java.util.Calendar.YEAR);
        String pattern = (sameYear ? "d MMM" : "d MMM yyyy") + (withTime ? ", HH:mm" : "");
        return new SimpleDateFormat(pattern, loc).format(new Date(millis));
    }
}

package com.android.tools;

import javax.annotation.Nullable;

public class StringUtil {
    public static boolean isEmpty(@Nullable final String str) {
        if (str == null) {
            return true;
        }
        return str.length() == 0;
    }
}

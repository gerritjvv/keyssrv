package keyssrv.util;

import clojure.lang.IFn;
import com.google.common.io.ByteStreams;
import org.apache.commons.lang3.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Map;

public class Utils {

    public static final String join(Collection coll, char sep) {
        return StringUtils.join(coll, sep);
    }

    public static final String toString(Object obj) {
        return obj.toString();
    }

    /**
     * Clojure reduce-kv for any map interface
     *
     * @param reducer
     * @param init
     * @param map
     * @return
     */
    public static final Object reduce_kv(IFn reducer, Object init, Map<Object, Object> map) {
        Object state = init;

        for (Map.Entry entry : map.entrySet()) {
            state = reducer.invoke(state, entry.getKey(), entry.getValue());
        }

        return state;
    }

    public static boolean isFirstByte(byte[] bts, byte v) {
        return bts[0] == v;
    }

    public static final byte[] as128Bits(String val) {
        byte[] bts = new byte[16];
        if (val.length() < 16)
            throw new RuntimeException("String must be at least 16 chars long");

        for (int i = 0; i < 16; i++)
            bts[i] = (byte) val.charAt(i);

        return bts;
    }

    public static final String removeWhiteSpace(String val) {
        if (val == null)
            return "";

        StringBuilder buff = new StringBuilder(val.length());
        char ch;
        for (int i = 0; i < val.length(); i++) {
            ch = val.charAt(i);

            if (!Character.isWhitespace(ch))
                buff.append(ch);

        }

        return buff.toString();
    }

    public static final String limitText(String txt, int limit) {
        if(txt != null && txt.length() > limit) {
            return txt.substring(0, limit);
        }

        return txt;
    }
    /**
     * Remove all characters not in A-Za-z0-9-_
     */
    public static final String sanitizePath(String s) {
        StringBuilder buff = new StringBuilder();

        char ch;
        for (int i = 0; i < s.length(); i++) {
            ch = s.charAt(i);

            if ((ch >= 'A' && ch <= 'Z') ||
                    (ch >= '0' && ch <= '9') ||
                    (ch >= 'a' && ch <= 'z') ||
                    (ch == '-' || ch == '_')) {
                buff.append(ch);
            }
        }

        return buff.toString();
    }
}

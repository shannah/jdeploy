package ca.weblite.tools.io;

public class StringUtil {

    public static String splitIntoFixedWidthLines(String str, int lineLength) {
        StringBuilder sb = new StringBuilder();
        char[] chars = str.toCharArray();
        int len = chars.length;
        for (int i=0; i < len; i++) {
            if (i > 0 && i % lineLength == 0) {
                sb.append("\n");
            }
            sb.append(chars[i]);
        }
        return sb.toString();
    }
}

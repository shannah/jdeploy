package ca.weblite.jdeploy.installer.win;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIFunctionMapper;
import com.sun.jna.win32.W32APITypeMapper;

import java.util.HashMap;
import java.util.Map;

public interface Shell32 extends StdCallLibrary {
    public static final long SHCNE_ASSOCCHANGED = 0x08000000;
    public static final int SHCNF_IDLIST = 0x0000;
    final static Map<String, Object> WIN32API_OPTIONS = new HashMap<String, Object>() {
        private static final long serialVersionUID = 1L;
        {
            put(Library.OPTION_FUNCTION_MAPPER, W32APIFunctionMapper.UNICODE);
            put(Library.OPTION_TYPE_MAPPER, W32APITypeMapper.UNICODE);
        }
    };

    public Shell32 INSTANCE = (Shell32) Native.loadLibrary("Shell32", Shell32.class, WIN32API_OPTIONS);

    //whatever you want to expose here
    void SHChangeNotify(long wEventId, int uFlags, Pointer dwItem1, Pointer dwItem2);


}

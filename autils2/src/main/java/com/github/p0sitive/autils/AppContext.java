package com.github.p0sitive.autils;

import android.app.ActivityManager;
import android.content.ContentResolver;
import android.content.Context;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;

import java.util.List;
import java.util.Locale;

/**
 *
 * Created by momo on 2017/8/21.
 */

public class AppContext {
    public static Context sContext;
    public static boolean DEBUGGABLE;

    public static void init(Context context) {
        sContext = context;
    }

    private static String sPackageName = null;
    private static ContentResolver sContentResolver = null;

    public static Context getContext() {
        return sContext;
    }

    public static void openDebug() {
        DEBUGGABLE = true;
    }

    public static String getCurrentProcessName() {
        if (sContext == null) {
            return null;
        }
        int pid = android.os.Process.myPid();
        ActivityManager mActivityManager = (ActivityManager) sContext.getSystemService(Context.ACTIVITY_SERVICE);
        if (mActivityManager == null) {
            return null;
        }

        List<ActivityManager.RunningAppProcessInfo> appProcesses = null;
        try {
            appProcesses = mActivityManager.getRunningAppProcesses();
        } catch (Exception e) {
        }
        if (appProcesses == null) {
            return null;
        }

        for (ActivityManager.RunningAppProcessInfo appProcess : appProcesses) {
            if (appProcess.pid == pid) {
                return appProcess.processName;
            }
        }
        return null;
    }

    /**
     * 应用是否在前台  此方法已经在oppoR5手机上测试，耗时6ms左右
     *
     * @return
     */
    public static boolean isAppOnForeground() {
        if (null == sContext) {
            return false;
        }
        try {
            //https://fabric.io/momo6/android/apps/com.immomo.momo/issues/570a17f6ffcdc0425095363d  LETV X500在getRunningAppProcesses方法内部会抛出空指针异常！
            ActivityManager activityManager = (ActivityManager) sContext.getSystemService(Context.ACTIVITY_SERVICE);
            List<ActivityManager.RunningAppProcessInfo> appProcesses = activityManager.getRunningAppProcesses();
            if (appProcesses == null) {
                return false;
            }
            int myPid = android.os.Process.myPid();
            for (ActivityManager.RunningAppProcessInfo appProcess : appProcesses) {
                if (appProcess.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND && appProcess.pid == myPid) {
                    return true;
                }
            }
        } catch (Exception e) {
        }
        return false;
    }

    public static boolean isRunningInMainProcess() {
        String processName = getCurrentProcessName();
        if (!TextUtils.isEmpty(processName) && processName.equals(sContext.getPackageName())) {
            return true;
        }
        return false;
    }

    public static boolean isRunningInMainThread() {
        Looper myLooper = Looper.myLooper();
        Looper mainLooper = Looper.getMainLooper();
        Log.i("","isRunningInMainThread myLooper=" + myLooper + ", mainLooper=" + mainLooper);
        return myLooper == mainLooper;
    }

    public static String getPackageName() {
        if (sPackageName == null) {
            sPackageName = getContext().getPackageName();
            if (sPackageName.indexOf(":") >= 0) {
                sPackageName = sPackageName.substring(0, sPackageName.lastIndexOf(":"));
            }
        }

        return sPackageName;
    }

    public static ContentResolver getContentResolver() {
        if (sContentResolver == null) {
            sContentResolver = getContext().getContentResolver();
        }
        return sContentResolver;
    }

    /**
     * 获取当前系统的国家代号字符串 *
     */
    public static String getSystemCountry() {
        return Locale.getDefault().getCountry();
    }

    /**
     * 获取当前系统的语言代号字符串 *
     */
    public static String getSystemLanguage() {
        return Locale.getDefault().getLanguage();
    }
}

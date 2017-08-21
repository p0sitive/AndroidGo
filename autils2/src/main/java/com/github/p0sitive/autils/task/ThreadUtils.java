package com.github.p0sitive.autils.task;

import com.github.p0sitive.autils.AppContext;

import android.util.Log;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 执行一个Thread，不关心Callback，有需要关心Callback的请使用MomoTaskExecutor，关心Callback就一定要cancelCallback<p/>
 * 这里提供了两个线程池，一种是对执行时间要求不高，比如下载一些资源、上传日志等
 * 一种是需要立即执行，比如load消息等(建议使用第二种)
 * <p/>
 */
public class ThreadUtils {
    private static final String TAG = "ThreadUtils";
    private static ThreadPoolExecutor innerPool = null;

    private static ThreadPoolExecutor rightNowPool = null;

    private static final int INNER_THREAD_SIZE_MIN = 1;
    private static final int INNER_THREAD_SIZE_MAX = 3;
    private static final int RIGHT_NOW_THREAD_SIZE_MIN = 10;
    private static final int RIGHT_NOW_THREAD_SIZE_MAX = 50;


    /**
     * 对执行时间要求不高
     */
    public static final int TYPE_INNER = 1;
    /**
     * 立即执行
     */
    public static final int TYPE_RIGHT_NOW = 2;
    /**
     * 立即执行，本地操作：文件、DB查询等
     */
    public static final int TYPE_RIGHT_NOW_LOCAL = 3;

    public synchronized static void execute(int type, Runnable runnable) {
        switch (type) {
            case TYPE_INNER:
                if (null == innerPool) {
                    innerPool = new ThreadPoolExecutor(INNER_THREAD_SIZE_MIN, INNER_THREAD_SIZE_MAX, 60L, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>(), new AThreadFactory(type), new RejectedHandler());
                }
                innerPool.execute(runnable);
                if (AppContext.DEBUGGABLE) {
                    Log.i(TAG, "ThreadUtils [innerPool] : PoolSize:" + innerPool.getPoolSize() + " ActiveSize:" + innerPool.getActiveCount());
                }
                break;
            case TYPE_RIGHT_NOW:
            case TYPE_RIGHT_NOW_LOCAL:
                if (null == rightNowPool) {
                    rightNowPool = new ThreadPoolExecutor(RIGHT_NOW_THREAD_SIZE_MIN, RIGHT_NOW_THREAD_SIZE_MAX, 60L, TimeUnit.SECONDS, new SynchronousQueue<Runnable>(), new AThreadFactory(type), new RejectedHandler());
                }
                rightNowPool.execute(runnable);
                if (AppContext.DEBUGGABLE) {
                    int poolSize = rightNowPool.getPoolSize();
                    int activeSize = rightNowPool.getActiveCount();
                    Log.i(TAG, "ThreadUtils [rightNowPool] : PoolSize:" + poolSize + " ActiveSize:" + activeSize);
                    if (activeSize > 45) {
                        //产生crash
                        String test = null;
                        test.equals("");
                    }
                }
                break;
            default:
                break;
        }
    }

    public static ThreadPoolExecutor getThreadPool(int type) {
        switch (type) {
            case TYPE_INNER:
                return innerPool;
            case TYPE_RIGHT_NOW:
                return rightNowPool;
            default:
                break;
        }
        return null;
    }

    public static void reset() {
        Log.i(TAG, "duanqing ThreadUtils reset");
        if (innerPool != null) {
            try {
                innerPool.shutdownNow();
            } catch (Exception e) {
            }
            innerPool = null;
        }

        if (rightNowPool != null) {
            try {
                rightNowPool.shutdownNow();
            } catch (Exception e) {
            }
            rightNowPool = null;
        }
    }

    private static class RejectedHandler implements RejectedExecutionHandler {
        @Override
        public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
            Log.e(TAG, "Task " + r.toString() + " rejected from " + executor.toString(), null);
        }
    }
}

class AThreadFactory implements ThreadFactory {
    private static final String TAG = "AThreadFactory";
    private static final AtomicInteger mCount = new AtomicInteger(1);

    private int type;

    public AThreadFactory(int type) {
        this.type = type;
    }

    public Thread newThread(Runnable r) {
        String threadName = "AsyncTask #" + mCount.getAndIncrement();
        Log.i(TAG, "AThreadFactory -> newThread : " + threadName);
        Thread thread = new Thread(r, threadName);
        if (type == ThreadUtils.TYPE_RIGHT_NOW || type == ThreadUtils.TYPE_RIGHT_NOW_LOCAL) {
            thread.setPriority(Thread.MAX_PRIORITY);
        } else {
            thread.setPriority(Thread.MIN_PRIORITY);
        }
        return thread;
    }
}

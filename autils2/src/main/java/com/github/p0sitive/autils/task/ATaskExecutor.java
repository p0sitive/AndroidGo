package com.github.p0sitive.autils.task;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import com.github.p0sitive.autils.AppContext;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 *
 */
public class ATaskExecutor {
    private static final String TAG = "ATaskExecutor";

    /** 有用户UI感知的任务队列 优先级最高 */
    public static final int EXECUTOR_TYPE_USER = 0;
    /** 无用户感知的任务队列 优先级低于user*/
    public static final int EXECUTOR_TYPE_INNER = 1;

    private static final Map<Object, List<Task>> runningTasks = new ConcurrentHashMap<>();

    private static final Map<Object, WeakHashMap<Runnable, ExecutorService>> scheduledTaskMap = new WeakHashMap<>();


    private static ICommonTaskErrorProcessor taskErrorProcessor;

    public static void setCommonTaskErrorProcessor(ICommonTaskErrorProcessor taskErrorProcessor) {
        ATaskExecutor.taskErrorProcessor = taskErrorProcessor;
    }


    public static void executeUserTask(Object tag, Task task) {
        executeTask(EXECUTOR_TYPE_USER, tag, task);
    }

    public static void executeInnerTask(Object tag, Task task) {
        executeTask(EXECUTOR_TYPE_INNER, tag, task);
    }

    public static void executeTask(int type, Object tag, Task task) {
        if(tag == null) {
            throw new IllegalArgumentException("tag is null");
        }
        if(task == null) {
            throw new IllegalArgumentException("task is null");
        }

        task.onPreTask();
        task.tag = tag;
        if (AppContext.DEBUGGABLE) {
            Log.i("","task[" + task.getClass().getName() + "] / thread[" + Thread.currentThread().getName() + "] : execute");
        }
        if (type == EXECUTOR_TYPE_USER) {
            ThreadUtils.execute(ThreadUtils.TYPE_RIGHT_NOW, task);
        } else {
            ThreadUtils.execute(ThreadUtils.TYPE_INNER, task);
        }

        List<Task> tasks = runningTasks.get(tag);
        if(tasks == null) {
            tasks = new CopyOnWriteArrayList<>();
        }
        tasks.add(task);

        runningTasks.put(tag, tasks);

    }

    public static void executeDelayTask(Object tag, Runnable runnable, long delay, TimeUnit timeUnit) {
        if(tag == null) {
            throw new IllegalArgumentException("tag is null");
        }
        if(runnable == null) {
            throw new IllegalArgumentException("runnable is null");
        }
        if(delay <= 0) {
            throw new IllegalArgumentException("delay <= 0");
        }

        ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
        WeakHashMap<Runnable, ExecutorService> runnableMap = new WeakHashMap<>();
        runnableMap.put(runnable, scheduledExecutorService);

        scheduledTaskMap.put(tag, runnableMap);

        scheduledExecutorService.schedule(runnable, delay, timeUnit);

    }

    public void cancelDelayTask(Object tag, Runnable runnable) {
        if(tag == null) {
            throw new IllegalArgumentException("tag is null");
        }
        if(runnable == null) {
            throw new IllegalArgumentException("runnable is null");
        }

        Map<Runnable, ExecutorService> runnableMap = scheduledTaskMap.get(tag);
        if(runnableMap == null) {
            return;
        }

        ExecutorService executorService = runnableMap.get(runnable);
        if(executorService != null) {
            if(!executorService.isTerminated()) {
                executorService.shutdownNow();
                runnableMap.remove(runnable);
                if(runnableMap.isEmpty()) {
                    scheduledTaskMap.remove(tag);
                }
            }
        }

    }

    public void cancelAllDelayTasks(Object tag) {
        if(tag == null) {
            throw new IllegalArgumentException("tag is null");
        }

        Map<Runnable, ExecutorService> runnableMap = scheduledTaskMap.get(tag);
        if(runnableMap == null) {
            return;
        }

        for(WeakHashMap.Entry<Runnable, ExecutorService> entry : runnableMap.entrySet()) {
            ExecutorService service = entry.getValue();
            if(!service.isTerminated()) {
                service.shutdownNow();
            }
        }

        runnableMap.clear();
        scheduledTaskMap.remove(tag);
    }

    public static void cancleAllTasksByTag(Object tag) {
        if(tag == null) {
            throw new IllegalArgumentException("tag is null");
        }
        List<Task> tasks = runningTasks.get(tag);
        if(tasks != null) {
            for(Task runningTask : tasks) {
                runningTask.cancel(true);
            }
            tasks.clear();
        }

        runningTasks.remove(tag);

    }

    public static void cancleSpecificTask(Object tag, Task task) {
        if(tag == null) {
            throw new IllegalArgumentException("tag is null");
        }
        if(task == null) {
            throw new IllegalArgumentException("task is null");
        }

        task.cancel(true);
        List<Task> tasks = runningTasks.get(tag);
        if(tasks != null) {
            try {
                tasks.remove(task);
            } catch (UnsupportedOperationException e) {
                Log.e(TAG,"",e);
            }
            if(tasks.isEmpty()) {
                runningTasks.remove(tag);
            }
        }
    }

    public static abstract class Task<Params, Progress, Result> implements Runnable, IInterruptable {

        private static TaskHandler handler;

        private Params[] mParams;

        private volatile boolean isInterrupted;
        private volatile boolean isCancelled;

        private volatile long threadId;
        private Object tag;

        public Task() {
            this.isCancelled = false;
            this.isInterrupted = false;
        }

        public Task(Params... params) {
            this();
            this.mParams = params;
        }

        private static class AsyncResult<Params, Progress> {
            Task task;
            Params result;
            Progress[] progress;
            Throwable exception;
        }

        public final void cancel(boolean interrupt) {
            if(isCancelled) {
                return;
            }
            isCancelled = true;
            if(interrupt && !isInterrupted) {
                interrupt();
                killRuningHttpConnection();
            }

        }

        protected void onCancelled() {
        }

        private void finish() {
            if(tag == null) {
                return;
            }

            if(isCancelled()) {
                AsyncResult<Result, Progress> result = new AsyncResult<>();
                result.task = this;

                Message message = Message.obtain();
                message.what = TaskHandler.MSG_TYPE_CANCLE;
                message.obj = result;

                getHandler().sendMessage(message);
            }

            List<Task> tasks = runningTasks.get(tag);
            if(tasks != null) {
                try {
                    tasks.remove(this);
                } catch (UnsupportedOperationException e) {
                    Log.e(TAG,"",e);
                }
                if(tasks.isEmpty()) {
                    runningTasks.remove(tag);
                }
            }
        }

        private void killRuningHttpConnection() {
        }

        @Override
        public void interrupt() {
            isInterrupted = true;
        }

        @Override
        public void run() {
            Log.i(TAG,"task[" + this.getClass().getName() + "] / thread[" + Thread.currentThread().getName() + "] : run");
            if(isInterrupted){
                finish();
                return;
            }

            long startTime = System.currentTimeMillis();
            AsyncResult<Result, Progress> result = doInBackground(mParams);
            if (AppContext.DEBUGGABLE) {
                Log.i(TAG,"task[" + this.getClass().getName() + "] / thread[" + Thread.currentThread().getName() + "] : doInBackground costs " + (System.currentTimeMillis() - startTime));
            }

            if(isInterrupted){
                if (AppContext.DEBUGGABLE) {
                    Log.i(TAG,"task[" + this.getClass().getName() + "] / thread[" + Thread.currentThread().getName() + "] : isInterrupted, finish");
                }
                finish();
                return;
            }

            Message message = Message.obtain();
            message.what = TaskHandler.MSG_TYPE_POST_EXECUTE;
            message.obj = result;

            getHandler().sendMessage(message);

        }

        protected static Handler getHandler() {
            if (handler == null) {
                synchronized (ATaskExecutor.class) {
                    if(handler == null) {
                        handler = new TaskHandler();
                    }
                }
            }
            return handler;
        }

        private static class TaskHandler extends Handler {
            public static final int MSG_TYPE_POST_EXECUTE = 1;
            public static final int MSG_TYPE_PROGRESS_UPDATE = 2;
            public static final int MSG_TYPE_CANCLE= 3;

            public TaskHandler() {
                super(Looper.getMainLooper());
            }

            @Override
            public void handleMessage(Message msg) {
                AsyncResult<?,?> result = (AsyncResult<?,?>)msg.obj;

                if (result == null || result.task == null) {
                    Log.i(TAG,"task[null] / thread[" + Thread.currentThread().getName() + "] : handleMessage return");
                    return;
                }

                Task task = result.task;
                if(msg.what == MSG_TYPE_POST_EXECUTE) {
                    if(result.task.isInterrupted) {
                        Log.i(TAG,"task[" + result.task.getClass().getName() + "] / thread[" + Thread.currentThread().getName() + "] : handleMessage isInterrupted, finish");
                        result.task.finish();
                    } else {
                        Log.i(TAG,"task[" + result.task.getClass().getName() + "] / thread[" + Thread.currentThread().getName() + "] : handleMessage onPostExecute");
                        task.onPostExecute(result);
                    }

                } else if(msg.what == MSG_TYPE_PROGRESS_UPDATE) {
                    if(!result.task.isInterrupted) {
                        task.onProgressUpdate(result.progress);
                    }
                } else if(msg.what == MSG_TYPE_CANCLE) {
                    task.onCancelled();
                }
            }
        }

        public final boolean isCancelled() {
            return isCancelled;
        }

        protected abstract Result executeTask(Params... params) throws Exception;

        private final AsyncResult<Result, Progress> doInBackground(Params... params) {
            AsyncResult<Result, Progress> result = new AsyncResult<>();
            try {
                if(!isCancelled()) {
                    threadId = Thread.currentThread().getId();
                    result.result = executeTask(params);
                } else {
                    result.exception = new Exception("task already canceled");
                }
            } catch (Throwable e) {
                result.exception = e;
            }
            result.task = this;
            return result;
        }

        protected final void publishProgress(Progress... progress) {
            if (!isCancelled()) {
                AsyncResult<Result, Progress> result = new AsyncResult<>();
                result.progress = progress;
                result.task = this;

                Message message = Message.obtain();
                message.what = TaskHandler.MSG_TYPE_PROGRESS_UPDATE;
                message.obj = result;

                getHandler().sendMessage(message);
            }
        }

        private final void onPostExecute(AsyncResult<Result, Progress> result) {
            finish();
            onTaskFinish();
            if(result.exception == null) {
                onTaskSuccess(result.result);
            } else {
                if(result.exception instanceof Exception) {
                    onTaskError((Exception) result.exception);
                } else {
                    onTaskError(new Exception(result.exception));
                }
            }
        }

        protected void onProgressUpdate(Progress... values) {

        }

        protected void onPreTask() {

        }
        protected void onTaskFinish() {

        }

        protected void onTaskSuccess(Result result) {

        }
        protected void onTaskError(Exception e) {
            if(taskErrorProcessor != null) {
                taskErrorProcessor.processTaskError(e);
            }
        }

    }

}

interface IInterruptable {
    void interrupt();
}






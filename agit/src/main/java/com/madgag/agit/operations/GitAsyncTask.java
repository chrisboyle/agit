package com.madgag.agit.operations;

import static android.R.drawable.stat_notify_error;
import static java.lang.System.currentTimeMillis;

import java.util.concurrent.Future;

import android.R;
import com.google.inject.Provider;
import com.google.inject.name.Named;
import com.madgag.agit.guice.RepositoryScope;
import com.madgag.ssh.android.authagent.AndroidAuthAgent;
import org.connectbot.service.PromptHelper;
import roboguice.util.RoboAsyncTask;
import android.os.Handler;
import android.util.Log;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.assistedinject.Assisted;
import com.madgag.agit.Progress;
import com.madgag.agit.ProgressListener;
import com.madgag.agit.operation.lifecycle.OperationLifecycleSupport;

public class GitAsyncTask extends RoboAsyncTask<OpNotification> implements ProgressListener<Progress> {

	public final static String TAG = "GAT";

    @Inject GitOperationExecutor operationExecutor;
    @Inject Provider<PromptHelper> promptHelperProvider;
	
	private final GitOperation operation;
	private final OperationLifecycleSupport lifecycleSupport;
	
	private long startTime;

	private Progress latestProgress;

    private final Runnable publishOnUIThreadRunnable = new Runnable() {
        public void run() { publishLatestProgress(); }
    };
	
	@Inject
	public GitAsyncTask(
            @Named("uiThread") Handler handler,
			@Assisted GitOperation operation,
			@Assisted OperationLifecycleSupport lifecycleSupport) {
        handler(handler);
		this.operation = operation;
		this.lifecycleSupport = lifecycleSupport;
	}
	
    @Override
    protected void onPreExecute() {
    	Log.d(TAG, "Starting onPreExecute "+operation+" handler="+handler);
    	lifecycleSupport.startedWith(new OpNotification(operation.getOngoingIcon(), operation.getTickerText(), operation.getShortDescription(), operation.getUrl().toString()));
    	startTime = currentTimeMillis();
    }

	public OpNotification call() {
        return operationExecutor.call(operation, new OperationUIContext(this, promptHelperProvider));
	}
	
	@Override
	protected void onSuccess(OpNotification opResult) {
		long duration=currentTimeMillis()-startTime;
		Log.d(TAG, "Completed in "+duration+" ms");
        lifecycleSupport.success(opResult);
		lifecycleSupport.completed(opResult);
	}

    @Override
    protected void onException(Exception e) throws RuntimeException {
        OpNotification notification = new OpNotification(stat_notify_error,operation.getName()+" failed","Fetch failed due to "+e.getMessage(), "");
        lifecycleSupport.error(notification);
        lifecycleSupport.completed(notification);
    }

    // Called on background thread
	public void publish(Progress... values) {
		latestProgress = values[values.length-1];
		Log.d(TAG, "Got progress to post : "+latestProgress);
        handler().post(publishOnUIThreadRunnable);
		Log.d(TAG, "...posted progress");
	}
	
	protected void publishLatestProgress() {
		Log.d(TAG, "publishLatestProgress() : Calling lifecycle publisher with "+latestProgress+" ...");
		lifecycleSupport.publish(latestProgress);
		Log.d(TAG, "...called lifecycle publisher.");
	}

	public GitOperation getOperation() {
		return operation;
	}

	public Future<Void> getFutureInUse() {
		return future;
	}
	
	@Override
	public String toString() {
		return getClass().getSimpleName()+"["+operation+"]";
	}
}

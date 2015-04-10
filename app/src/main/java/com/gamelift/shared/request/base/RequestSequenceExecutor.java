package com.gamelift.shared.request.base;

import android.util.Log;

import com.octo.android.robospice.SpiceManager;
import com.octo.android.robospice.persistence.exception.SpiceException;
import com.octo.android.robospice.request.listener.RequestListener;
import com.octo.android.robospice.request.springandroid.SpringAndroidSpiceRequest;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by Deepscorn (vnms11@gmail.com) on 4/10/15.
 */
public class RequestSequenceExecutor {

    private Map<Class<?>, OnSuccessListener<?>> requestClassToSuccessListener
            = new HashMap<Class<?>, OnSuccessListener<?>>();

    private Map<Class<?>, OnFailListener> requestClassToFailListener
            = new HashMap<Class<?>, OnFailListener>();

    private boolean isExecuteRequested = false;
    private SpringAndroidSpiceRequest<?> currentRequest = null;
    private OnFailListener defaultFailListener = null;
    private SpiceManager manager;

    public RequestSequenceExecutor(SpiceManager manager) {
        this.manager = manager;
    }

    // Note: when execution is started calling setRequest() will start request
    // immidiately, so ensure, that you've set all the listeners before setRequest() call
    public <T> void setRequest(SpringAndroidSpiceRequest<T> request) {
        if (currentRequest != null)
            throw (new IllegalStateException("Parallel execution not allowed. Only one by one!"));

        currentRequest = request;

        if(isExecuteRequested) {
            executeRequest();
        }
    }

    public <T> void setOnSuccessListener(Class<? extends SpringAndroidSpiceRequest<T>> clazz, OnSuccessListener<T> onSuccessListener) {
        requestClassToSuccessListener.put(clazz, onSuccessListener);
    }

    public <T> void setOnFailListener(Class<? extends SpringAndroidSpiceRequest<T>> clazz, OnFailListener onFailListener) {
        requestClassToFailListener.put(clazz, onFailListener);
    }

    public void execute() {
        if(null == currentRequest)
            throw(new IllegalStateException("At least 1 request must be added to execute"));
        if(!manager.isStarted())
            throw(new IllegalStateException("SpiceManager is not started"));

        isExecuteRequested = true;
        executeRequest();
    }

    private <T> void executeRequest() {
        logRequest();
        manager.execute((SpringAndroidSpiceRequest<T>) currentRequest, new RequestListener<T>() {
            @Override
            public void onRequestFailure(SpiceException spiceException) {
                OnFailListener failListener = requestClassToFailListener.get(currentRequest.getClass());
                logFailure(spiceException, failListener != null);
                currentRequest = null;
                if(failListener != null)
                    failListener.onFailure(RequestSequenceExecutor.this, spiceException);
                else
                    defaultFailListener.onFailure(RequestSequenceExecutor.this, spiceException);
            }

            @Override
            public void onRequestSuccess(T response) {
                logSuccess(response);
                OnSuccessListener<T> listener = (OnSuccessListener<T>) requestClassToSuccessListener
                        .get(currentRequest.getClass());
                currentRequest = null;
                listener.onSuccess(RequestSequenceExecutor.this, response);
            }
        });
    }

    private void logRequest() {
        Log.d("request_sequence", "execute "+currentRequest);
    }

    private <T> void logSuccess(T response) {
        Log.d("request_sequence", "success "+response+" for request "+currentRequest.getClass());
    }

    private void logFailure(Exception ex, boolean failListenerIsSet) {
        Log.d("request_sequence", "fail "+ex+" for request "
                +currentRequest.getClass()+" and failListener is set: "+failListenerIsSet);
    }

    // Sets default OnFailListener. That is usefull when any fail just returns some "false" and
    // execution ends (no new requests).
    // So, if no fail listener was set for some class and fail occurred,
    // than default fail listener will be called
    // Set to null and you'll get exception in that case
    public void setDefaultOnFailListener(OnFailListener onFailListener) {
        defaultFailListener = onFailListener;
    }

    public interface OnSuccessListener<T> {
        public void onSuccess(RequestSequenceExecutor executor, T response);
    };

    public interface OnFailListener {
        public void onFailure(RequestSequenceExecutor executor, Exception ex);
    };
}


package com.nathcat.messagecat_client;

import androidx.appcompat.app.AppCompatActivity;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;

public class LoadingActivity extends AppCompatActivity {
    // This is used to connect to the networker service
    private ServiceConnection networkerServiceConnection = new ServiceConnection() {

        /**
         * Called when the service connects to this process
         * @param componentName Name for an application component
         * @param iBinder The binder returned by the service
         */
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            System.out.println("Bound");
            // Get the service instance
            networkerServiceMessenger = new Messenger(iBinder);
            bound = true;

            try {
                Message msg = Message.obtain(null, NetworkerService.CLIENT_REGISTER_FOR_EVENTS);
                msg.replyTo = nsReceiver;
                networkerServiceMessenger.send(msg);

                msg = Message.obtain(null, NetworkerService.CLIENT_REQUEST_AUTH_STATE);
                msg.replyTo = nsReceiver;
                networkerServiceMessenger.send(msg);

            } catch (RemoteException e) {
                throw new RuntimeException(e);
            }
        }

        /**
         * Called when the service disconnects from this process
         * @param componentName Name for an application component
         */
        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            networkerServiceMessenger = null;
            bound = false;
        }
    };

    public class NSReceiverHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            if (msg.arg1 == NetworkerService.EVENTS_AUTH_SUCCESS) {
                // Load the main activity
                startActivity(new Intent(LoadingActivity.this, MainActivity.class));
            }
            else if (msg.arg1 == NetworkerService.EVENTS_AUTH_FAILED_INVALID_USER) {
                // Load the new user activity
                startActivity(new Intent(LoadingActivity.this, NewUserActivity.class));
            }
            else {
                System.out.println("Unexpected event! Code: " + msg.what);
            }
        }
    }
    private Messenger networkerServiceMessenger = null;  // Messenger to the networker service
    public Messenger nsReceiver;                         // Messenger which receives messages from the networker service
    private boolean bound = false;                     // Is the service currently bound to this process

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // This method is called when the application is opened
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_loading);

        // Check if the networker service is not running
        if (!isServiceRunning(NetworkerService.class)) {
            System.out.println("Service start is being called");
            // Start the foreground service
            startForegroundService(new Intent(this, NetworkerService.class));
        }

        nsReceiver = new Messenger(new NSReceiverHandler());

        System.out.println("Attempting bind");
        // Try to bind to the networker service
        bindService(
                new Intent(this, NetworkerService.class),  // The intent to bind with
                networkerServiceConnection,                            // The ServiceConnection object to use
                Context.BIND_AUTO_CREATE                               // If the service does not already exist, create it
        );
    }

    /**
     * Check if a service is running
     * @param serviceClass The class of the service
     * @return boolean
     */
    private boolean isServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }
}
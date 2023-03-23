package com.nathcat.messagecat_client;

import androidx.appcompat.app.AppCompatActivity;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;

public class LoadingActivity extends AppCompatActivity {
    /**
     * Thread to wait for authentication to complete, if it is not already complete
     */
    private class WaitForAuthThread extends Thread {
        @Override
        public void run() {
            if (networkerService.authenticated) {
                startActivity(new Intent(LoadingActivity.this, MainActivity.class));
            }

            while (networkerService.waitingForResponse) {
                try {
                    Thread.sleep(100);

                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            if (networkerService.authenticated) {
                startActivity(new Intent(LoadingActivity.this, MainActivity.class));
            }
            else {
                startActivity(new Intent(LoadingActivity.this, NewUserActivity.class));
            }
        }
    }

    // This is used to connect to the networker service
    private ServiceConnection networkerServiceConnection = new ServiceConnection() {

        /**
         * Called when the service connects to this process
         * @param componentName Name for an application component
         * @param iBinder The binder returned by the service
         */
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            // Get the service instance
            networkerService = ((NetworkerService.NetworkerServiceBinder) iBinder).getService();
            bound = true;

            // Wait for authentication to complete
            new WaitForAuthThread().start();
        }

        /**
         * Called when the service disconnects from this process
         * @param componentName Name for an application component
         */
        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            networkerService = null;
            bound = false;
        }
    };

    private NetworkerService networkerService = null;  // The instance of the networker service
    private boolean bound = false;                     // Is the service currently bound to this process

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // This method is called when the application is opened
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_loading);

        // Check if the networker service is not running
        if (!isServiceRunning(NetworkerService.class)) {
            // Start the foreground service
            startForegroundService(new Intent(this, NetworkerService.class));
        }

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
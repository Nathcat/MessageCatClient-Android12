package com.nathcat.messagecat_client;

import androidx.appcompat.app.AppCompatActivity;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.widget.Toast;

public class LoadingActivity extends AppCompatActivity {
    /**
     * Thread to wait for authentication to complete, if it is not already complete
     */
    private class WaitForAuthThread extends Thread {
        @Override
        public void run() {
            while (networkerService.waitingForResponse) {
                try {
                    Thread.sleep(100);

                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            if (networkerService.authenticated) {
                // TODO This is a debug message
                ((Activity) LoadingActivity.this).runOnUiThread(() -> Toast.makeText(LoadingActivity.this, "Auth success", Toast.LENGTH_SHORT).show());
            }
            else {
                // TODO This is also a debug message
                ((Activity) LoadingActivity.this).runOnUiThread(() -> Toast.makeText(LoadingActivity.this, "Auth failed", Toast.LENGTH_SHORT).show());
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

            // TODO Debug message
            Toast.makeText(LoadingActivity.this, "Bound to service", Toast.LENGTH_SHORT).show();

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

        // Try to bind to the networker service
        bindService(
                new Intent(this, NetworkerService.class),  // The intent to bind with
                networkerServiceConnection,                            // The ServiceConnection object to use
                Context.BIND_AUTO_CREATE                               // If the service does not already exist, create it
        );

        if (!bound) {
            startService(new Intent(this, NetworkerService.class));
            bindService(
                    new Intent(this, NetworkerService.class),  // The intent to bind with
                    networkerServiceConnection,                            // The ServiceConnection object to use
                    Context.BIND_AUTO_CREATE                               // If the service does not already exist, create it
            );
        }
    }
}
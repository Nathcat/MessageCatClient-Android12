package com.nathcat.messagecat_client;

import static android.Manifest.permission.READ_PHONE_NUMBERS;
import static android.Manifest.permission.READ_PHONE_STATE;
import static android.Manifest.permission.READ_SMS;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.IBinder;
import android.telephony.TelephonyManager;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.nathcat.RSA.ObjectContainer;
import com.nathcat.messagecat_database.Result;
import com.nathcat.messagecat_database_entities.Chat;
import com.nathcat.messagecat_database_entities.User;
import com.nathcat.messagecat_server.RequestType;

import org.json.simple.JSONObject;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.Date;
import java.util.Random;

public class NewUserActivity extends AppCompatActivity {

    private ServiceConnection networkerServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            networkerService = ((NetworkerService.NetworkerServiceBinder) iBinder).getService();
            bound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            networkerService = null;
            bound = false;
        }
    };

    private EditText displayNameEntry;
    private EditText phoneNumberEntry;
    private ProgressBar loadingWheel;

    private String phoneNumber;
    private String password;

    private NetworkerService networkerService;
    private boolean bound = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_new_user);

        loadingWheel = (ProgressBar) findViewById(R.id.newUserLoadingWheel);
        loadingWheel.setVisibility(View.GONE);

        bindService(
                new Intent(this, NetworkerService.class),
                networkerServiceConnection,
                BIND_AUTO_CREATE
        );

        displayNameEntry = (EditText) findViewById(R.id.displayName);
        phoneNumberEntry = (EditText) findViewById(R.id.phoneNumber);

        // Get the user's phone number
        TelephonyManager telephonyManager = (TelephonyManager) this.getSystemService(Context.TELEPHONY_SERVICE);
        // Check if the required permissions are granted
        if (ActivityCompat.checkSelfPermission(this, READ_SMS) == PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, READ_PHONE_NUMBERS) == PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
            phoneNumber = telephonyManager.getLine1Number();
        }
        else {
            // Try and request those permissions and try again
            requestPermissions(new String[]{READ_SMS, READ_PHONE_NUMBERS, READ_PHONE_STATE}, 100);

            if (ActivityCompat.checkSelfPermission(this, READ_SMS) == PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, READ_PHONE_NUMBERS) == PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
                phoneNumber = telephonyManager.getLine1Number();
            }
            else {
                System.exit(1);
            }
        }

        assert phoneNumber != null;

        password = randomPassword();

        phoneNumberEntry.setText(phoneNumber);
    }

    /**
     * Generates a random password
     * @return Random password
     */
    private String randomPassword() {
        Random r = new Random();
        StringBuilder sb = new StringBuilder();

        // Add a series of random integers to the password string
        int length = r.nextInt();
        while (length <= 0) {
            length = r.nextInt(20);
        }

        for (int i = 0; i < length; i++) {
            sb.append(r.nextInt(1000));
        }

        // Return the final string
        return String.valueOf(sb.toString().hashCode());
    }

    public void onSubmitButtonClicked(View v) {
        // Block user interaction
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);

        // Show the loading wheel
        loadingWheel.setVisibility(View.VISIBLE);

        JSONObject request = new JSONObject();
        request.put("type", RequestType.AddUser);
        request.put("data", new ObjectContainer(new User(-1, phoneNumberEntry.getText().toString(), password, displayNameEntry.getText().toString(), new Date().toString(), "default.png")));

        networkerService.SendRequest(new NetworkerService.Request(new NetworkerService.IRequestCallback() {
            @Override
            public void callback(Result result, Object response) {
                if (result == Result.FAILED) {
                    networkerService.startConnectionHandler();
                    onSubmitButtonClicked(v);
                }

                // Check if the response is null
                // If this is the case then the entry had duplicate data
                if (response == null) {
                    NewUserActivity.this.runOnUiThread(() -> Toast.makeText(NewUserActivity.this, "Either your username or display name is already used, try something else.", Toast.LENGTH_SHORT).show());
                    return;
                }

                // Write the data to the auth file
                try {
                    ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(new File(getFilesDir(), "UserData.bin")));
                    oos.writeObject(response);
                    oos.flush();
                    oos.close();

                    // TODO Remove test chats
                    oos = new ObjectOutputStream(new FileOutputStream(new File(getFilesDir(), "Chats.bin")));
                    oos.writeObject(new Chat[] {new Chat(-1, "test1", "test1-desc", -1), new Chat(-1, "test2", "test2-desc", -1)});
                    oos.flush();
                    oos.close();

                    // Now start an authentication request and start up the loading screen
                    JSONObject authRequest = new JSONObject();
                    authRequest.put("type", RequestType.Authenticate);
                    authRequest.put("data", new ObjectContainer(response));

                    // Send the auth request and start the loading activity
                    networkerService.SendRequest(new NetworkerService.Request(new NetworkerService.IRequestCallback() {
                        @Override
                        public void callback(Result result, Object response) {
                            if (result == Result.FAILED) {
                                networkerService.authenticated = false;
                            }
                            else {
                                if (response.getClass() == String.class) {
                                    networkerService.authenticated = false;
                                }
                                else {
                                    networkerService.authenticated = true;

                                }
                            }

                            networkerService.waitingForResponse = false;
                        }
                    }, authRequest));

                    ((Activity) NewUserActivity.this).runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
                        }
                    });

                    startActivity(new Intent(NewUserActivity.this, LoadingActivity.class));

                } catch (IOException e) {
                    e.printStackTrace();
                }

                networkerService.waitingForResponse = false;
            }
        }, request));
    }
}
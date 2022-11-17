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

;
import com.nathcat.messagecat_database.Result;
import com.nathcat.messagecat_database_entities.Chat;
import com.nathcat.messagecat_database_entities.ChatInvite;
import com.nathcat.messagecat_database_entities.FriendRequest;
import com.nathcat.messagecat_database_entities.User;
import com.nathcat.messagecat_server.ListenRule;
import com.nathcat.messagecat_server.RequestType;

import org.json.simple.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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
    private EditText passwordEntry;
    private EditText passwordRetypeEntry;

    private final MessageDigest digest = MessageDigest.getInstance("SHA-256");
    private NetworkerService networkerService;
    private boolean bound = false;

    public NewUserActivity() throws NoSuchAlgorithmException {
    }

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

        passwordEntry = (EditText) findViewById(R.id.password);
        passwordRetypeEntry = (EditText) findViewById(R.id.passwordRetype);

        phoneNumberEntry.setText(phoneNumber);
    }

    public void onSubmitButtonClicked(View v) {
        // Check if any of the fields are empty, and check that the password entries match
        if (phoneNumberEntry.getText().toString().contentEquals("") || passwordEntry.getText().toString().contentEquals("") || passwordRetypeEntry.getText().toString().contentEquals("") || displayNameEntry.getText().toString().contentEquals("")) {
            Toast.makeText(this, "One or more of the entry fields are empty!", Toast.LENGTH_LONG).show();
            return;
        }

        if (!passwordEntry.getText().toString().contentEquals(passwordRetypeEntry.getText().toString())) {
            Toast.makeText(this, "Passwords do not match!", Toast.LENGTH_LONG).show();
            return;
        }

        // Hash the password
        String hashedPassword = bytesToHex(digest.digest(
                passwordEntry.getText().toString().getBytes(StandardCharsets.UTF_8)
        ));


        // Block user interaction
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);

        // Show the loading wheel
        loadingWheel.setVisibility(View.VISIBLE);

        JSONObject request = new JSONObject();
        request.put("type", RequestType.AddUser);
        request.put("data", new User(-1, phoneNumberEntry.getText().toString(), hashedPassword, displayNameEntry.getText().toString(), new Date().toString(), "default.png"));

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
                    NewUserActivity.this.runOnUiThread(() -> {
                        Toast.makeText(NewUserActivity.this, "Either your username or display name is already used, try something else.", Toast.LENGTH_SHORT).show();
                        loadingWheel.setVisibility(View.GONE);
                        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
                    });
                    return;
                }

                // Write the data to the auth file
                try {
                    ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(new File(getFilesDir(), "UserData.bin")));
                    oos.writeObject(response);
                    oos.flush();
                    oos.close();

                    oos = new ObjectOutputStream(new FileOutputStream(new File(getFilesDir(), "Chats.bin")));
                    oos.writeObject(new Chat[0]);
                    oos.flush();
                    oos.close();

                    // Now start an authentication request and start up the loading screen
                    JSONObject authRequest = new JSONObject();
                    authRequest.put("type", RequestType.Authenticate);
                    authRequest.put("data", response);

                    // Send the auth request and start the loading activity
                    networkerService.SendRequest(new NetworkerService.Request(new NetworkerService.IRequestCallback() {
                        @Override
                        public void callback(Result result, Object response) {
                            if (result == Result.FAILED) {
                                System.exit(1);
                            }

                            if (response.getClass() == String.class) {
                                networkerService.authenticated = false;
                                networkerService.waitingForResponse = false;
                                System.exit(1);
                            }

                            // If authentication was successful...
                            if (result == Result.SUCCESS) {
                                networkerService.authenticated = true;

                                // Update the data in the auth file
                                try {
                                    ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(new File(getFilesDir(), "UserData.bin")));
                                    oos.writeObject(response);
                                    oos.flush();
                                    oos.close();

                                    networkerService.user = (User) response;

                                } catch (IOException e) {
                                    e.printStackTrace();
                                }

                                // Add the notification listen rules
                                JSONObject friendRequestRuleRequest = new JSONObject();
                                friendRequestRuleRequest.put("type", RequestType.AddListenRule);
                                friendRequestRuleRequest.put("data", new ListenRule(RequestType.SendFriendRequest, "RecipientID", networkerService.user.UserID));
                                networkerService.SendRequest(new NetworkerService.ListenRuleRequest(new NetworkerService.IListenRuleCallback() {
                                    @Override
                                    public void callback(Object response) {
                                        // Get the friend request from the response object
                                        FriendRequest friendRequest = (FriendRequest) ((JSONObject) response).get("data");

                                        // Request the user that sent the request from the server
                                        JSONObject senderRequest = new JSONObject();
                                        senderRequest.put("type", RequestType.GetUser);
                                        senderRequest.put("data", new User(friendRequest.SenderID, null, null, null, null, null));
                                        networkerService.SendRequest(new NetworkerService.Request(new NetworkerService.IRequestCallback() {
                                            @Override
                                            public void callback(Result result, Object response) {
                                                if (result == Result.FAILED) {
                                                    return;
                                                }

                                                // Cast the response into a user object
                                                User sender = (User) response;
                                                // Show the notification
                                                networkerService.notificationChannel.showNotification("New friend request", sender.DisplayName + " wants to be friends!");
                                            }
                                        }, senderRequest));
                                    }
                                }, new NetworkerService.IRequestCallback() {
                                    @Override
                                    public void callback(Result result, Object response) {
                                        NetworkerService.IRequestCallback.super.callback(result, response);
                                    }
                                }, friendRequestRuleRequest));

                                JSONObject chatRequestRuleRequest = new JSONObject();
                                chatRequestRuleRequest.put("type", RequestType.AddListenRule);
                                chatRequestRuleRequest.put("data", new ListenRule(RequestType.SendChatInvite, "RecipientID", networkerService.user.UserID));
                                networkerService.SendRequest(new NetworkerService.ListenRuleRequest(new NetworkerService.IListenRuleCallback() {
                                    @Override
                                    public void callback(Object response) {
                                        // Get the chat invite from the request that triggered the listen rule
                                        ChatInvite chatInvite = (ChatInvite) ((JSONObject) response).get("data");

                                        // Create a new request to get the chat that the user has been invited to
                                        JSONObject getChatRequest = new JSONObject();
                                        getChatRequest.put("type", RequestType.GetChat);
                                        getChatRequest.put("data", new Chat(chatInvite.ChatID, null, null, -1));

                                        networkerService.SendRequest(new NetworkerService.Request(new NetworkerService.IRequestCallback() {
                                            @Override
                                            public void callback(Result result, Object response) {
                                                if (result == Result.FAILED) {
                                                    return;
                                                }

                                                // Cast the response to a Chat object
                                                Chat chat = (Chat) response;
                                                // Show the notification
                                                networkerService.notificationChannel.showNotification("New chat invitation", "You have been invited to " + chat.Name);
                                            }
                                        }, getChatRequest));
                                    }
                                }, new NetworkerService.IRequestCallback() {
                                    @Override
                                    public void callback(Result result, Object response) {
                                        NetworkerService.IRequestCallback.super.callback(result, response);
                                    }
                                }, chatRequestRuleRequest));

                                File chatsFile = new File(getFilesDir(), "Chats.bin");
                                if (chatsFile.exists()) {
                                    try {
                                        // Get the array of chats
                                        ObjectInputStream ois = new ObjectInputStream(new FileInputStream(chatsFile));
                                        Chat[] chats = (Chat[]) ois.readObject();

                                        // Create a listen rule for each of the chats
                                        for (Chat chat : chats) {
                                            JSONObject msgRule = new JSONObject();
                                            msgRule.put("type", RequestType.AddListenRule);
                                            msgRule.put("data", new ListenRule(RequestType.SendMessage, "ChatID", chat.ChatID));
                                            Bundle bundle = new Bundle();
                                            bundle.putSerializable("chat", chat);

                                            networkerService.SendRequest(new NetworkerService.ListenRuleRequest(new NetworkerService.IListenRuleCallback() {
                                                @Override
                                                public void callback(Object response, Bundle bundle) {
                                                    // Create a notification
                                                    networkerService.notificationChannel.showNotification("New message", "You have a new message in " + ((Chat) bundle.getSerializable("chat")).Name);
                                                }
                                            }, new NetworkerService.IRequestCallback() {
                                                @Override
                                                public void callback(Result result, Object response) {
                                                    NetworkerService.IRequestCallback.super.callback(result, response);
                                                }
                                            }, msgRule, bundle));
                                        }

                                    } catch (IOException | ClassNotFoundException e) {
                                        e.printStackTrace();
                                    }

                                }
                            }

                            networkerService.waitingForResponse = false;
                        }
                    }, authRequest));

                    NewUserActivity.this.runOnUiThread(() -> getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE));

                    startActivity(new Intent(NewUserActivity.this, LoadingActivity.class));

                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }, request));
    }

    /**
     * Converts a byte array to a hex string. Used for hashing passwords.
     * @param bytes The byte array to convert.
     * @return A hex string
     */
    public String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(2 * bytes.length);  // Twice the length since we have two hex characters per byte
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hex = "0" + hex;
            }

            sb.append(hex);
        }

        return sb.toString();
    }
}
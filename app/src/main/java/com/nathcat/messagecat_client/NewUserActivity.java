package com.nathcat.messagecat_client;

import static android.Manifest.permission.READ_PHONE_NUMBERS;
import static android.Manifest.permission.READ_PHONE_STATE;
import static android.Manifest.permission.READ_SMS;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.telephony.TelephonyManager;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.nathcat.messagecat_database.Result;
import com.nathcat.messagecat_database_entities.Chat;
import com.nathcat.messagecat_database_entities.ChatInvite;
import com.nathcat.messagecat_database_entities.FriendRequest;
import com.nathcat.messagecat_database_entities.User;
import com.nathcat.messagecat_server.ListenRule;
import com.nathcat.messagecat_server.RequestType;

import org.json.simple.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;

public class NewUserActivity extends AppCompatActivity {

    private ServiceConnection networkerServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            SharedData.nsMessenger = new Messenger(iBinder);
            bound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            SharedData.nsMessenger = null;
            bound = false;
        }
    };

    public class NSReceiverHandler extends Handler {
        @Override
        public void handleMessage(android.os.Message msg) {
            switch (msg.arg1) {
                case NetworkerService.EVENTS_AUTH_SUCCESS:
                    // Add the notification listen rules
                    JSONObject friendRequestRuleRequest = new JSONObject();
                    friendRequestRuleRequest.put("type", RequestType.AddListenRule);
                    friendRequestRuleRequest.put("data", new ListenRule(RequestType.SendFriendRequest, "RecipientID", SharedData.user.UserID));
                    NetworkerService.SendRequest(SharedData.nsMessenger, new NetworkerService.ListenRuleRequest(NetworkerService::friendRequestLrCallback, (Result result, Object response) -> {}, friendRequestRuleRequest));

                    JSONObject chatRequestRuleRequest = new JSONObject();
                    chatRequestRuleRequest.put("type", RequestType.AddListenRule);
                    chatRequestRuleRequest.put("data", new ListenRule(RequestType.SendChatInvite, "RecipientID", SharedData.user.UserID));
                    NetworkerService.SendRequest(SharedData.nsMessenger, new NetworkerService.ListenRuleRequest(NetworkerService::chatInvitationLrCallback, (Result result, Object response) -> {}, chatRequestRuleRequest));

                    File chatsFile = new File(getFilesDir(), "Chats.bin");
                    if (chatsFile.exists()) {
                        try {
                            // Get the array of chats
                            ObjectInputStream ois = new ObjectInputStream(Files.newInputStream(chatsFile.toPath()));
                            Chat[] chats = (Chat[]) ois.readObject();

                            // Create a listen rule for each of the chats
                            for (Chat chat : chats) {
                                JSONObject msgRule = new JSONObject();
                                msgRule.put("type", RequestType.AddListenRule);
                                msgRule.put("data", new ListenRule(RequestType.SendMessage, "ChatID", chat.ChatID));
                                Bundle bundle = new Bundle();
                                bundle.putSerializable("chat", chat);

                                NetworkerService.SendRequest(SharedData.nsMessenger, new NetworkerService.ListenRuleRequest(NetworkerService::messageLrCallback, (Result result, Object response) -> {}, msgRule, bundle));
                            }

                        } catch (IOException | ClassNotFoundException e) {
                            e.printStackTrace();
                        }

                    } break;

                case NetworkerService.EVENTS_AUTH_FAILED_INVALID_USER:
                    throw new RuntimeException(new Exception("Unexpected authentication failure"));
            }
        }
    }

    private EditText displayNameEntry;
    private EditText phoneNumberEntry;
    private ProgressBar loadingWheel;

    private String phoneNumber;
    private EditText passwordEntry;
    private EditText passwordRetypeEntry;

    private static final MessageDigest digest;

    static {
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    private boolean bound = false;

    public NewUserActivity() throws NoSuchAlgorithmException {
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_new_user);

        loadingWheel = (ProgressBar) findViewById(R.id.newUserLoadingWheel);
        loadingWheel.setVisibility(View.GONE);

        SharedData.nsReceiver = new Messenger(new NewUserActivity.NSReceiverHandler());
        SharedData.misc.put("activity", this);

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

        SharedData.misc.put("v", v);

        NetworkerService.SendRequest(SharedData.nsMessenger, new NetworkerService.Request(NewUserActivity::addUserCallback, request));
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

    /**
     * Callback for the add user request
     * @param result The result of the request
     * @param response The server's response from the request, if successful, this will be the new user data
     */
    private static void addUserCallback(Result result, Object response) {
        if (result == Result.FAILED) {
            try {
                android.os.Message msg = android.os.Message.obtain(null, NetworkerService.RESTART_CONNECTION_HANDLER);
                SharedData.nsMessenger.send(msg);
            } catch (RemoteException e) {
                throw new RuntimeException(e);
            }

            ((NewUserActivity) SharedData.misc.get("activity")).runOnUiThread(() -> ((NewUserActivity) SharedData.misc.get("activity")).onSubmitButtonClicked((View) SharedData.misc.get("v")));
        }

        // Check if the response is null
        // If this is the case then the entry had duplicate data
        if (response == null) {
            ((NewUserActivity) SharedData.misc.get("activity")).runOnUiThread(() -> {
                Toast.makeText(((NewUserActivity) SharedData.misc.get("activity")), "Either your username or display name is already used, try something else.", Toast.LENGTH_SHORT).show();
                ((NewUserActivity) SharedData.misc.get("activity")).loadingWheel.setVisibility(View.GONE);
                ((NewUserActivity) SharedData.misc.get("activity")).getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
            });
            return;
        }

        // Write the data to the auth file
        try {
            ObjectOutputStream oos = new ObjectOutputStream(Files.newOutputStream(new File(((NewUserActivity) SharedData.misc.get("activity")).getFilesDir(), "UserData.bin").toPath()));
            oos.writeObject(response);
            oos.flush();
            oos.close();

            SharedData.user = (User) response;

            oos = new ObjectOutputStream(Files.newOutputStream(new File(((NewUserActivity) SharedData.misc.get("activity")).getFilesDir(), "Chats.bin").toPath()));
            oos.writeObject(new Chat[0]);
            oos.flush();
            oos.close();

            try {
                android.os.Message msg = android.os.Message.obtain(null, NetworkerService.CLIENT_REGISTER_FOR_EVENTS);
                msg.replyTo = SharedData.nsReceiver;
                SharedData.nsMessenger.send(msg);

                msg = android.os.Message.obtain(null, NetworkerService.CLIENT_AUTHENTICATE);
                SharedData.nsMessenger.send(msg);

            } catch (RemoteException e) {
                throw new RuntimeException(e);
            }

            ((NewUserActivity) SharedData.misc.get("activity")).runOnUiThread(() -> ((NewUserActivity) SharedData.misc.get("activity")).getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE));

            ((NewUserActivity) SharedData.misc.get("activity")).startActivity(new Intent(((NewUserActivity) SharedData.misc.get("activity")), LoadingActivity.class));

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
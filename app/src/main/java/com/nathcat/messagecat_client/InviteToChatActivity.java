package com.nathcat.messagecat_client;

import androidx.appcompat.app.AppCompatActivity;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Messenger;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.nathcat.RSA.KeyPair;
import com.nathcat.RSA.RSA;
import com.nathcat.messagecat_database.KeyStore;
import com.nathcat.messagecat_database.Result;
import com.nathcat.messagecat_database_entities.Chat;
import com.nathcat.messagecat_database_entities.ChatInvite;
import com.nathcat.messagecat_database_entities.User;
import com.nathcat.messagecat_server.RequestType;

import org.json.simple.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.security.NoSuchAlgorithmException;
import java.util.Date;

public class InviteToChatActivity extends AppCompatActivity {

    private ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            SharedData.nsMessenger = new Messenger(iBinder);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            SharedData.nsMessenger = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_invite_to_chat);

        // Bind to the networker service
        bindService(
                new Intent(this, NetworkerService.class),
                connection,
                BIND_AUTO_CREATE
        );

        // Add the chats fragment view
        getSupportFragmentManager().beginTransaction()
                .setReorderingAllowed(true)
                .add(R.id.InviteToChatChatsContainer, ChatsFragment.class, null)
                .commit();


        SharedData.misc.put("activity", this);
    }

    /**
     * Changes the fragment to the create new chat fragment
     * @param v The view that called this method
     */
    public void onAddChatButtonClicked(View v) {
        // Change the chats fragment to the create new chat fragment
        ((LinearLayout) findViewById(R.id.InviteToChatChatsContainer)).removeAllViews();

        getSupportFragmentManager().beginTransaction()
                .setReorderingAllowed(true)
                .add(R.id.InviteToChatChatsContainer, CreateNewChatFragment.class, null)
                .commit();
    }

    /**
     * Sends a chat invite for the chat that was clicked
     * @param v The view that called this method, this will be the chat that was clicked
     */
    public void onChatClicked(View v) {
        // Get the container object of the chat fragment that was clicked
        LinearLayout container = (LinearLayout) v.getParent().getParent();

        // Get the chats array from the file
        Chat[] chats = null;
        try {
            ObjectInputStream ois = new ObjectInputStream(Files.newInputStream(new File(getFilesDir(), "Chats.bin").toPath()));
            chats = (Chat[]) ois.readObject();
            ois.close();

        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
            Toast.makeText(this, "Something went wrong :(", Toast.LENGTH_SHORT).show();
            System.exit(1);
        }

        assert chats != null;

        // Determine which chat was clicked
        Chat chat = null;

        for (int i = 0; i < container.getChildCount(); i++) {
            if (container.getChildAt(i).equals(v.getParent())) {
                chat = chats[i];
            }
        }

        assert chat != null;

        // Get the key pair from the key store
        KeyPair pair = null;

        try {
            KeyStore keyStore = new KeyStore(new File(getFilesDir(), "KeyStore.bin"));
            pair = keyStore.GetKeyPair(chat.PublicKeyID);

        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Something went wrong :(", Toast.LENGTH_SHORT).show();
            System.exit(1);
        }

        assert pair != null;

        // Create and send the request
        JSONObject request = new JSONObject();
        request.put("type", RequestType.SendChatInvite);
        request.put("data", new ChatInvite(-1, chat.ChatID, SharedData.user.UserID, ((User) SharedData.misc.get("userToInvite")).UserID, new Date().getTime(), -1));
        request.put("keyPair", new KeyPair(null, pair.pri));

        NetworkerService.SendRequest(SharedData.nsMessenger, new NetworkerService.Request(InviteToChatActivity::sendChatInviteCallback, request));
    }

    /**
     * Creates a new chat
     * @param v The view that called this method
     */
    public void onCreateNewChatClicked(View v) {
        // Get the name and description of the new chat
        View fragmentContainer = (View) v.getParent();
        String chatName = ((EditText) fragmentContainer.findViewById(R.id.NewChatName)).getText().toString();
        String chatDesc = ((EditText) fragmentContainer.findViewById(R.id.NewChatDesc)).getText().toString();

        // Check that neither of the fields are empty before proceeding
        if (chatName.contentEquals("") || chatDesc.contentEquals("")) {
            Toast.makeText(this, "One or more of the required fields are empty!", Toast.LENGTH_SHORT).show();
            return;
        }

        // Generate a new key pair for the new chat
        KeyPair chatKeyPair;
        try {
            chatKeyPair = RSA.GenerateRSAKeyPair();

        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            return;
        }

        // Send the request to the server
        JSONObject request = new JSONObject();
        request.put("type", RequestType.AddChat);
        request.put("data", new Chat(-1, chatName, chatDesc, -1));
        request.put("keyPair", new KeyPair(chatKeyPair.pub, null));

        SharedData.misc.put("chatKeyPair", chatKeyPair);

        NetworkerService.SendRequest(SharedData.nsMessenger, new NetworkerService.Request(InviteToChatActivity::createNewChatCallback, request));
    }

    //
    // Callbacks
    //

    private static void createNewChatCallback(Result result, Object response) {
        if (result == Result.FAILED) {
            ((InviteToChatActivity) SharedData.misc.get("activity")).runOnUiThread(() -> Toast.makeText(((InviteToChatActivity) SharedData.misc.get("activity")), "Something went wrong!", Toast.LENGTH_SHORT).show());
            System.exit(1);
        }

        // Get the chat from the response
        Chat chat = (Chat) response;

        try {
            // Open the chats file and read the current chat array
            ObjectInputStream ois = new ObjectInputStream(Files.newInputStream(new File(((InviteToChatActivity) SharedData.misc.get("activity")).getFilesDir(), "Chats.bin").toPath()));
            Chat[] chats = (Chat[]) ois.readObject();
            ois.close();

            // Add the new chat to the array
            Chat[] newChats = new Chat[chats.length + 1];
            System.arraycopy(chats, 0, newChats, 0, chats.length);
            newChats[chats.length] = chat;

            // Write the new array to the chats file
            ObjectOutputStream oos = new ObjectOutputStream(Files.newOutputStream(new File(((InviteToChatActivity) SharedData.misc.get("activity")).getFilesDir(), "Chats.bin").toPath()));
            oos.writeObject(newChats);
            oos.flush();
            oos.close();

            // Open the key store and add the new key pair
            KeyStore keyStore = new KeyStore(new File(((InviteToChatActivity) SharedData.misc.get("activity")).getFilesDir(), "KeyStore.bin"));
            if (keyStore.AddKeyPair(chat.PublicKeyID, ((KeyPair) SharedData.misc.get("chatKeyPair"))) == Result.FAILED) {
                ((InviteToChatActivity) SharedData.misc.get("activity")).runOnUiThread(() -> Toast.makeText(((InviteToChatActivity) SharedData.misc.get("activity")), "Something went wrong!", Toast.LENGTH_SHORT).show());
                System.exit(1);
            }

        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
            ((InviteToChatActivity) SharedData.misc.get("activity")).runOnUiThread(() -> Toast.makeText(((InviteToChatActivity) SharedData.misc.get("activity")), "Something went wrong!", Toast.LENGTH_SHORT).show());
            System.exit(1);
        }

        // Send a chat invite
        JSONObject inviteRequest = new JSONObject();
        inviteRequest.put("type", RequestType.SendChatInvite);
        inviteRequest.put("data", new ChatInvite(-1, chat.ChatID, SharedData.user.UserID, ((User) SharedData.misc.get("userToInvite")).UserID, new Date().getTime(), -1));
        inviteRequest.put("keyPair", new KeyPair(null,((KeyPair) SharedData.misc.get("chatKeyPair")).pri));

        NetworkerService.SendRequest(SharedData.nsMessenger, new NetworkerService.Request(InviteToChatActivity::sendChatInviteCallback, inviteRequest));
    }

    private static void sendChatInviteCallback(Result result, Object response) {
        if (result == Result.FAILED) {
            ((InviteToChatActivity) SharedData.misc.get("activity")).runOnUiThread(() -> Toast.makeText(((InviteToChatActivity) SharedData.misc.get("activity")), "Something went wrong!", Toast.LENGTH_SHORT).show());
            System.exit(1);
        }

        ((InviteToChatActivity) SharedData.misc.get("activity")).runOnUiThread(() -> {
            Toast.makeText(((InviteToChatActivity) SharedData.misc.get("activity")), "Sent chat invite!", Toast.LENGTH_SHORT).show();

            // Go back to the main activity
            ((InviteToChatActivity) SharedData.misc.get("activity")).runOnUiThread(() -> ((InviteToChatActivity) SharedData.misc.get("activity")).startActivity(new Intent(((InviteToChatActivity) SharedData.misc.get("activity")), MainActivity.class)));
        });
    }
}
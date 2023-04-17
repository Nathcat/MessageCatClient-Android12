package com.nathcat.messagecat_client;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Messenger;
import android.os.RemoteException;
import android.view.View;
import android.view.Menu;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.navigation.NavigationView;

import androidx.fragment.app.FragmentContainerView;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.appcompat.app.AppCompatActivity;

import com.nathcat.RSA.EncryptedObject;
import com.nathcat.RSA.KeyPair;
import com.nathcat.RSA.PublicKeyException;
import com.nathcat.messagecat_client.databinding.ActivityMainBinding;
import com.nathcat.messagecat_database.KeyStore;
import com.nathcat.messagecat_database.Result;
import com.nathcat.messagecat_database_entities.Chat;
import com.nathcat.messagecat_database_entities.ChatInvite;
import com.nathcat.messagecat_database_entities.FriendRequest;
import com.nathcat.messagecat_database_entities.Message;
import com.nathcat.messagecat_database_entities.User;
import com.nathcat.messagecat_server.ListenRule;
import com.nathcat.messagecat_server.RequestType;

import org.json.simple.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Random;

public class MainActivity extends AppCompatActivity {

    private ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            // Get the networker service instance
            SharedData.nsMessenger = new Messenger(iBinder);

            // Get the user data from the networker service
            try {
                android.os.Message msg = android.os.Message.obtain(null, NetworkerService.CLIENT_REQUEST_USER);
                msg.replyTo = SharedData.nsReceiver;
                msg.obj = new Bundle();
                ((Bundle) msg.obj).putSerializable("callback", new INSCallback() {
                    @Override
                    public void callback(android.os.Message message) {
                        SharedData.user = (User) ((Bundle) message.obj).getSerializable("user");

                        // Set the display name in the nav header to the user's display name
                        ((TextView) ((NavigationView) findViewById(R.id.nav_view))
                                .getHeaderView(0).findViewById(R.id.displayName))
                                .setText(SharedData.user.DisplayName);
                    }
                });

                SharedData.nsMessenger.send(msg);
            } catch (RemoteException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            SharedData.nsMessenger = null;
        }
    };

    public static class NSReceiverHandler extends Handler {
        @Override
        public void handleMessage(android.os.Message msg) {
            // Get the callback from the message
            INSCallback callback = (INSCallback) ((Bundle) msg.obj).getSerializable("callback");
            callback.callback(msg);
        }
    }

    public interface INSCallback extends Serializable {
        default void callback(android.os.Message msg) {

        }
    }

    private AppBarConfiguration mAppBarConfiguration;
    private ActivityMainBinding binding;

    // Used on the "find people page"
    private User[] searchResults;

    // Used on the "friends" page
    public User[] friends;

    // Used on the invites page
    public InvitationsFragment invitationsFragment;
    public ArrayList<InvitationFragment> invitationFragments = new ArrayList<>();

    // Used on the messaging page
    public HashMap<Integer, User> users = new HashMap<>();
    public MessagingFragment messagingFragment;

    // Used on the chats page
    public ArrayList<ChatFragment> chatFragments = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedData.nsReceiver = new Messenger(new NSReceiverHandler());
        SharedData.misc.put("activity", this);

        // Bind to the networker service
        bindService(
                new Intent(this, NetworkerService.class),
                connection,
                BIND_AUTO_CREATE
        );

        // Set up the drawer and action bar (the bar at the top of the application)
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.appBarMain.toolbar);

        DrawerLayout drawer = binding.drawerLayout;
        NavigationView navigationView = binding.navView;
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        mAppBarConfiguration = new AppBarConfiguration.Builder(
                R.id.chatsFragment, R.id.findPeopleFragment)
                .setOpenableLayout(drawer)
                .build();

        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        NavigationUI.setupActionBarWithNavController(this, navController, mAppBarConfiguration);
        NavigationUI.setupWithNavController(navigationView, navController);

        // Set a click listener so that the navigation drawer menu correctly navigates the available pages
        ((NavigationView) findViewById(R.id.nav_view)).setNavigationItemSelectedListener(item -> {
            NavController navController1 = Navigation.findNavController(MainActivity.this, R.id.nav_host_fragment_content_main);
            switch (item.getItemId()) {
                case R.id.nav_chats:
                    navController1.navigate(R.id.chatsFragment);
                    break;

                case R.id.nav_find_people:
                    navController1.navigate(R.id.findPeopleFragment);
                    break;

                case R.id.nav_friends:
                    navController1.navigate(R.id.friendsFragment);
                    break;

                case R.id.nav_invitations:
                    navController1.navigate(R.id.invitationsFragment);
                    break;
            }
            return false;
        });

        // Set the text in the nav header to show the app is waiting for the networker service
        // It is unlikely that the user will actually see this message
        ((TextView) ((NavigationView) findViewById(R.id.nav_view))
                .getHeaderView(0).findViewById(R.id.displayName))
                .setText("Waiting for networker service");
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onSupportNavigateUp() {
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        return NavigationUI.navigateUp(navController, mAppBarConfiguration)
                || super.onSupportNavigateUp();
    }

    /**
     * Called when the search button is clicked on the search for people page
     * @param v The view that called this method
     */
    public void onSearchButtonClicked(View v) {
        View fragmentView = (View) v.getParent().getParent();

        // Get the display name entered into the search box
        String displayName = ((EditText) fragmentView.findViewById(R.id.UserSearchBar).findViewById(R.id.userSearchDisplayNameEntry)).getText().toString();
        // If the entry is empty, ask the user to enter a name and then end the method
        if (displayName.contentEquals("")) {
            Toast.makeText(this, "Please enter a name first!", Toast.LENGTH_SHORT).show();
            return;
        }

        // Create a request to search users by display name
        JSONObject request = new JSONObject();
        request.put("type", RequestType.GetUser);
        request.put("selector", "displayName");
        request.put("data", new User(-1, null, null, displayName, null, null));

        SharedData.misc.put("fragmentView", fragmentView);

        // Send the request
        NetworkerService.SendRequest(SharedData.nsMessenger, new NetworkerService.Request(MainActivity::onSearchButtonClicked_callback, request));
    }

    private static void onSearchButtonClicked_callback(Result result, Object response) {
        Runnable action;

        if (result == Result.FAILED) {
            action = () -> Toast.makeText(((MainActivity) SharedData.misc.get("activity")), "Something went wrong :(", Toast.LENGTH_SHORT).show();
        }
        else {
            action = () -> {

                User[] results = (User[]) response;

                // Get the fragment container linear layout to add the result fragments to
                LinearLayout fragmentContainerLayout = ((View) SharedData.misc.get("fragmentView")).findViewById(R.id.SearchResultFragmentContainer);

                fragmentContainerLayout.removeAllViews();

                // If the list of results is empty, hide the no results message
                if (results.length == 0) {
                    TextView message = new TextView(((MainActivity) SharedData.misc.get("activity")));
                    message.setText(R.string.no_search_results_message);

                    ((LinearLayout) ((View) SharedData.misc.get("fragmentView")).findViewById(R.id.SearchResultFragmentContainer)).addView(message);
                }

                // Clean the results of invalid results
                int numberRemoved = 0;
                for (int i = 0; i < results.length; i++) {
                    // Check if this user is the logged in user
                    if (results[i].UserID == SharedData.user.UserID) {
                        results[i] = null;
                        numberRemoved++;
                    }
                }

                ((MainActivity) SharedData.misc.get("activity")).searchResults = new User[results.length - numberRemoved];
                int finalIndex = 0;
                for (User value : results) {
                    if (value != null) {
                        ((MainActivity) SharedData.misc.get("activity")).searchResults[finalIndex] = value;
                        finalIndex++;
                    }
                }

                // Add each of the results to the fragment container layout
                for (User user : ((MainActivity) SharedData.misc.get("activity")).searchResults) {
                    // Generate a random id for the fragment container
                    int id = new Random().nextInt();

                    FragmentContainerView fragmentContainer = new FragmentContainerView(((MainActivity) SharedData.misc.get("activity")));
                    fragmentContainer.setId(id);

                    // Create the argument bundle to pass to the fragment
                    Bundle bundle = new Bundle();
                    bundle.putSerializable("user", user);

                    // Add the fragment to the fragment container view
                    ((MainActivity) SharedData.misc.get("activity")).getSupportFragmentManager().beginTransaction()
                            .setReorderingAllowed(true)
                            .add(id, UserSearchFragment.class, bundle)
                            .commit();

                    // Add the fragment container view to the linear layout
                    fragmentContainerLayout.addView(fragmentContainer);
                }
            };
        }

        // Run the predetermined action on the UI thread
        ((MainActivity) SharedData.misc.get("activity")).runOnUiThread(action);
    }

    /**
     * Called when the button to add a friend is clicked on the search people page
     * @param v The view that called the method
     */
    public void onAddFriendButtonClicked(View v) {
        FragmentContainerView fragmentContainerView = (FragmentContainerView) v.getParent().getParent();
        LinearLayout ll = (LinearLayout) fragmentContainerView.getParent();
        User user = null;

        for (int i = 0; i < ll.getChildCount(); i++) {
            if (fragmentContainerView.equals(ll.getChildAt(i))) {
                user = searchResults[i];
            }
        }

        assert user != null;

        // Create a request to send a friend request
        JSONObject request = new JSONObject();
        request.put("type", RequestType.SendFriendRequest);
        request.put("data", new FriendRequest(-1, user.UserID, user.UserID, new Date().getTime()));

        // Send the request to the server
        NetworkerService.SendRequest(SharedData.nsMessenger, new NetworkerService.Request(MainActivity::onAddFriendButtonClicked_callback, request));
    }

    private static void onAddFriendButtonClicked_callback(Result result, Object response) {
        // Notify the user of the result
        if (result == Result.SUCCESS) {
            ((MainActivity) SharedData.misc.get("activity")).runOnUiThread(() -> Toast.makeText(((MainActivity) SharedData.misc.get("activity")), "Friend request sent!", Toast.LENGTH_SHORT).show());
        }
        else {
            ((MainActivity) SharedData.misc.get("activity")).runOnUiThread(() -> Toast.makeText(((MainActivity) SharedData.misc.get("activity")), "Something went wrong :(", Toast.LENGTH_SHORT).show());
        }
    }

    /**
     * Called when a chat invite button is clicked on the friends page
     * @param v The view that called this method
     */
    public void onInviteToChatClicked(View v) {
        // Get the friend that was clicked
        LinearLayout ll = (LinearLayout) v.getParent().getParent();
        User friend = null;
        for (int i = 0; i < ll.getChildCount(); i++) {
            if (ll.getChildAt(i).equals(v.getParent())) {
                friend = friends[i];
                break;
            }
        }

        assert friend != null;

        SharedData.misc.put("userToInvite", friend);
        startActivity(new Intent(this, InviteToChatActivity.class));
    }

    /**
     * Called when a chat box is clicked
     * @param v The view that called this method
     */
    public void onChatClicked(View v) {
        // Get the navigation controller for this activity
        NavController navController1 = Navigation.findNavController(MainActivity.this, R.id.nav_host_fragment_content_main);
        // Create the bundle which we will pass to the messaging fragment
        Bundle bundle = new Bundle();

        // We now need to determine which chat was clicked
        boolean found = false;
        for (int i = 0; i < chatFragments.size(); i++) {
            if (chatFragments.get(i).requireView().equals(v)) {
                bundle.putSerializable("chat", chatFragments.get(i).chat);
                found = true;
                break;
            }
        }

        assert found;  // Ensure that the chat was found

        // Navigate to the messaging fragment, passing the argument bundle
        navController1.navigate(R.id.messagingFragment, bundle);
    }

    /**
     * Called when an invite is accepted
     * @param v The view that called this method
     */
    public void onAcceptInviteClicked(View v) {
        // Get the invite from the view that was clicked
        Object invite = null;

        // Iterate over the invitation fragments
        for (int i = 0; i < invitationFragments.size(); i++) {
            // Check if the views are the same
            if (invitationFragments.get(i).requireView().equals(v.getParent())) {
                // Get the invite object and exit the loop
                invite = invitationFragments.get(i).invite;
                break;
            }
        }

        // Ensure that the invite is found
        assert invite != null;

        // Determine if the invite is a friend request or chat invite
        RequestType type = (invite.getClass() == FriendRequest.class) ? RequestType.AcceptFriendRequest : RequestType.AcceptChatInvite;

        // Check if the user is already a member of this chat
        if (type == RequestType.AcceptChatInvite) {
            try {
                ObjectInputStream ois = new ObjectInputStream(Files.newInputStream(new File(getFilesDir(), "Chats.bin").toPath()));
                Chat[] chats = (Chat[]) ois.readObject();
                for (Chat chat : chats) {
                    if (chat.ChatID == ((ChatInvite) invite).ChatID) {
                        Toast.makeText(this, "You are already a member of this chat!", Toast.LENGTH_SHORT).show();
                        return;
                    }
                }
            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace();
                System.exit(1);
            }
        }

        // Create a request to accept the invite
        JSONObject request = new JSONObject();
        request.put("type", type);
        request.put("data", invite);

        SharedData.misc.put("invite", invite);
        NetworkerService.SendRequest(SharedData.nsMessenger, new NetworkerService.Request(MainActivity::onAcceptInviteClicked_callback, request));
    }

    private static void onAcceptInviteClicked_callback(Result result, Object response) {
        if (result == Result.FAILED) {
            ((MainActivity) SharedData.misc.get("activity")).runOnUiThread(() -> Toast.makeText(((MainActivity) SharedData.misc.get("activity")), "Something went wrong :(", Toast.LENGTH_SHORT).show());
            System.exit(1);
        }

        // Check if the response is a string
        if (response.getClass() == String.class) {
            // Output an appropriate message based on the response
            if (response.equals("failed")) {
                ((MainActivity) SharedData.misc.get("activity")).runOnUiThread(() -> Toast.makeText(((MainActivity) SharedData.misc.get("activity")), "Failed to accept invite :(", Toast.LENGTH_SHORT).show());
            }
            else {
                ((MainActivity) SharedData.misc.get("activity")).runOnUiThread(() -> Toast.makeText(((MainActivity) SharedData.misc.get("activity")), "Invite accepted!", Toast.LENGTH_SHORT).show());
            }

            ((MainActivity) SharedData.misc.get("activity")).runOnUiThread(() -> ((MainActivity) SharedData.misc.get("activity")).invitationsFragment.reloadInvites());
        }
        else {  // In this case the response will be a key pair, and the invite must have been a chat invite
            assert SharedData.misc.get("invite") instanceof ChatInvite;
            SharedData.misc.put("privateKey", response);

            // Request the chat as we need it's public key id to store it in the internal key store
            JSONObject chatRequest = new JSONObject();
            chatRequest.put("type", RequestType.GetChat);
            chatRequest.put("data", new Chat(((ChatInvite) SharedData.misc.get("invite")).ChatID, "", "", -1));

            NetworkerService.SendRequest(SharedData.nsMessenger, new NetworkerService.Request(MainActivity::onAcceptInviteClicked_callback_getPublicKey, chatRequest));

            // Register a new listen rule so that the user can see messages from this chat without having to restart the application
            JSONObject getChatRequest = new JSONObject();
            getChatRequest.put("type", RequestType.GetChat);
            getChatRequest.put("data", new Chat(((ChatInvite) SharedData.misc.get("invite")).ChatID, null, null, -1));

            NetworkerService.SendRequest(SharedData.nsMessenger, new NetworkerService.Request(MainActivity::onAcceptInviteClicked_callback_addListenRuleForNewChat, getChatRequest));

        }
    }

    private static void onAcceptInviteClicked_callback_addListenRuleForNewChat(Result result, Object response) {
        // Add a message listen rule for the new chat
        JSONObject lrRequest = new JSONObject();
        lrRequest.put("type", RequestType.AddListenRule);
        lrRequest.put("data", new ListenRule(RequestType.SendMessage, "ChatID", ((ChatInvite) SharedData.misc.get("invite")).ChatID));

        Bundle bundle = new Bundle();
        bundle.putSerializable("chat", (Chat) response);

        NetworkerService.SendRequest(SharedData.nsMessenger, new NetworkerService.ListenRuleRequest(NetworkerService::messageLrCallback, (Result r1, Object r2) -> {}, lrRequest, bundle));
    }

    private static void onAcceptInviteClicked_callback_getPublicKey(Result result, Object response) {
        if (result == Result.FAILED) {
            ((MainActivity) SharedData.misc.get("activity")).runOnUiThread(() -> Toast.makeText(((MainActivity) SharedData.misc.get("activity")), "Something went wrong :(", Toast.LENGTH_SHORT).show());
            System.exit(1);
        }

        Chat chat = (Chat) response;

        // Add the private key to the key store and the chat to the chats file
        try {
            KeyStore keyStore = new KeyStore(new File(((MainActivity) SharedData.misc.get("activity")).getFilesDir(), "KeyStore.bin"));
            keyStore.AddKeyPair(chat.PublicKeyID, ((KeyPair) SharedData.misc.get("privateKey")));

            ObjectInputStream ois = new ObjectInputStream(Files.newInputStream(new File(((MainActivity) SharedData.misc.get("activity")).getFilesDir(), "Chats.bin").toPath()));
            Chat[] chats = (Chat[]) ois.readObject();
            Chat[] newChats = new Chat[chats.length + 1];
            System.arraycopy(chats, 0, newChats, 0, chats.length);
            newChats[chats.length] = chat;
            ois.close();
            ObjectOutputStream oos = new ObjectOutputStream(Files.newOutputStream(new File(((MainActivity) SharedData.misc.get("activity")).getFilesDir(), "Chats.bin").toPath()));
            oos.writeObject(newChats);
            oos.flush();
            oos.close();


        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
            ((MainActivity) SharedData.misc.get("activity")).runOnUiThread(() -> Toast.makeText(((MainActivity) SharedData.misc.get("activity")), "Something went wrong :(", Toast.LENGTH_SHORT).show());
            System.exit(1);
        }

        ((MainActivity) SharedData.misc.get("activity")).runOnUiThread(() -> Toast.makeText(((MainActivity) SharedData.misc.get("activity")), "Invite accepted!", Toast.LENGTH_SHORT).show());
        ((MainActivity) SharedData.misc.get("activity")).runOnUiThread(() -> ((MainActivity) SharedData.misc.get("activity")).invitationsFragment.reloadInvites());
    }

    /**
     * Called when an invite is declined
     * @param v The view that called this method
     */
    public void onDeclineInviteClicked(View v) {
        // Get the invite from the view that was clicked
        Object invite = null;

        // Iterate over the invitation fragments
        for (int i = 0; i < invitationFragments.size(); i++) {
            // Check if the views are the same
            if (invitationFragments.get(i).requireView().equals(v.getParent())) {
                // Get the invite object and exit the loop
                invite = invitationFragments.get(i).invite;
                break;
            }
        }

        // Ensure that the invite is found
        assert invite != null;

        // Determine if the invite is a friend request or chat invite
        RequestType type = (invite.getClass() == FriendRequest.class) ? RequestType.DeclineFriendRequest : RequestType.DeclineChatInvite;

        // Create a request to decline the invite
        JSONObject request = new JSONObject();
        request.put("type", type);
        request.put("data", invite);

        NetworkerService.SendRequest(SharedData.nsMessenger, new NetworkerService.Request(MainActivity::onDeclineInviteClicked_callback, request));
    }

    private static void onDeclineInviteClicked_callback(Result result, Object response) {
        if (result == Result.FAILED) {
            ((MainActivity) SharedData.misc.get("activity")).runOnUiThread(() -> Toast.makeText(((MainActivity) SharedData.misc.get("activity")), "Something went wrong :(", Toast.LENGTH_SHORT).show());
            System.exit(1);
        }

        // Output an appropriate message based on the response
        if (response.equals("failed")) {
            ((MainActivity) SharedData.misc.get("activity")).runOnUiThread(() -> Toast.makeText(((MainActivity) SharedData.misc.get("activity")), "Failed to decline invite :(", Toast.LENGTH_SHORT).show());
        }
        else {
            ((MainActivity) SharedData.misc.get("activity")).runOnUiThread(() -> Toast.makeText(((MainActivity) SharedData.misc.get("activity")), "Invite declined!", Toast.LENGTH_SHORT).show());
        }

        ((MainActivity) SharedData.misc.get("activity")).runOnUiThread(() -> ((MainActivity) SharedData.misc.get("activity")).invitationsFragment.reloadInvites());
    }

    /**
     * Sends a message to the currently active chat
     * @param v The view that called this method
     */
    public void SendMessage(View v) {
        SharedData.misc.put("v", v);

        // Hide the send button and show the loading wheel
        v.setVisibility(View.GONE);
        ((View) v.getParent()).findViewById(R.id.messageSendButtonLoadingWheel).setVisibility(View.VISIBLE);

        // Get the active chat from the messaging fragment
        Chat chat = messagingFragment.chat;

        // Get the message from the text box
        String messageContent = ((EditText) ((View) v.getParent()).findViewById(R.id.MessageEntry)).getText().toString();

        SharedData.misc.put("messageContent", messageContent);
        SharedData.misc.put("chat", chat);

        // Ensure that the message is not empty before proceeding
        if (messageContent.equals("")) {
            Toast.makeText(this, "Please enter a message first!", Toast.LENGTH_SHORT).show();
            return;
        }

        // Get the chat's public key from the server
        JSONObject keyRequest = new JSONObject();
        keyRequest.put("type", RequestType.GetPublicKey);
        keyRequest.put("data", chat.PublicKeyID);

        NetworkerService.SendRequest(SharedData.nsMessenger, new NetworkerService.Request(MainActivity::sendMessage_callback_getPublicKey, keyRequest));

        // Clear the text box
        ((EditText) ((View) v.getParent()).findViewById(R.id.MessageEntry)).setText("");
    }

    private static void sendMessage_callback_getPublicKey(Result result, Object response) {
        if (result == Result.FAILED || response == null) {
            ((MainActivity) SharedData.misc.get("activity")).runOnUiThread(() -> Toast.makeText(((MainActivity) SharedData.misc.get("activity")), "Something went wrong!", Toast.LENGTH_SHORT).show());
            System.exit(1);
        }

        // Encrypt the message contents using the public key
        assert response != null;
        KeyPair publicKey = (KeyPair) response;
        EncryptedObject eContent = null;
        try {
            eContent = publicKey.encrypt(SharedData.misc.get("messageContent"));

        } catch (PublicKeyException e) {
            ((MainActivity) SharedData.misc.get("activity")).runOnUiThread(() -> Toast.makeText(((MainActivity) SharedData.misc.get("activity")), "Something went wrong!", Toast.LENGTH_SHORT).show());
            System.exit(1);
        }

        assert eContent != null;

        // Create the message object to send to the server
        Message message = new Message(SharedData.user.UserID, ((Chat) SharedData.misc.get("chat")).ChatID, new Date().getTime(), eContent);

        JSONObject sendRequest = new JSONObject();
        sendRequest.put("type", RequestType.SendMessage);
        sendRequest.put("data", message);

        NetworkerService.SendRequest(SharedData.nsMessenger, new NetworkerService.Request(MainActivity::sendMessage_callback_sendMessage, sendRequest));
    }

    private static void sendMessage_callback_sendMessage(Result result, Object response) {
        if (result == Result.FAILED || response == null) {
            ((MainActivity) SharedData.misc.get("activity")).runOnUiThread(() -> Toast.makeText(((MainActivity) SharedData.misc.get("activity")), "Something went wrong!", Toast.LENGTH_SHORT).show());
            System.exit(1);
        }

        ((MainActivity) SharedData.misc.get("activity")).runOnUiThread(() -> {
            // Hide the loading wheel and show the send button
            ((View) SharedData.misc.get("v")).setVisibility(View.VISIBLE);
            ((View) ((View) SharedData.misc.get("v")).getParent()).findViewById(R.id.messageSendButtonLoadingWheel).setVisibility(View.GONE);
        });
    }
}
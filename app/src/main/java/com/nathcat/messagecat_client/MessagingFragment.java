package com.nathcat.messagecat_client;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import android.os.Messenger;
import android.os.RemoteException;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Toast;

import com.nathcat.RSA.EncryptedObject;
import com.nathcat.RSA.KeyPair;
import com.nathcat.RSA.PrivateKeyException;
import com.nathcat.messagecat_database.KeyStore;
import com.nathcat.messagecat_database.MessageQueue;
import com.nathcat.messagecat_database.Result;
import com.nathcat.messagecat_database_entities.Chat;
import com.nathcat.messagecat_database_entities.Message;
import com.nathcat.messagecat_database_entities.User;
import com.nathcat.messagecat_server.ListenRule;
import com.nathcat.messagecat_server.RequestType;

import org.json.simple.JSONObject;

import java.io.File;
import java.io.IOException;

public class MessagingFragment extends Fragment {

    public Chat chat;
    private KeyPair privateKey;
    private MessageQueue messageQueue;
    private int listenRuleId = -1;

    public MessagingFragment() {
        super(R.layout.fragment_messaging);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Get the chat argument passed to this fragment, and the networker service instance from
        // the main activity
        chat = (Chat) requireArguments().getSerializable("chat");
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_messaging, container, false);
    }

    @Override
    public void onStart() {
        super.onStart();

        // Set the title on the action bar
        ((AppCompatActivity) requireActivity()).getSupportActionBar().setTitle(chat.Name);

        // Hide the loading wheel under the send button
        requireView().findViewById(R.id.messageSendButtonLoadingWheel).setVisibility(View.GONE);

        ((MainActivity) requireActivity()).messagingFragment = this;
        SharedData.misc.put("fragment", this);

        try {
            KeyStore keyStore = new KeyStore(new File(requireActivity().getFilesDir(), "KeyStore.bin"));
            privateKey = keyStore.GetKeyPair(MessagingFragment.this.chat.PublicKeyID);

        } catch (IOException e) {
            e.printStackTrace();
        }

        assert privateKey != null;

        SharedData.activeChatID = this.chat.ChatID;

        // Create the users hashmap and put the current user in it
        ((MainActivity) requireActivity()).users.put(SharedData.user.UserID, SharedData.user);

        // Request the message queue from the server
        JSONObject request = new JSONObject();
        request.put("type", RequestType.GetMessageQueue);
        request.put("data", chat.ChatID);

        NetworkerService.SendRequest(SharedData.nsMessenger, new NetworkerService.Request(MessagingFragment::onStart_callback_getMessageQueue, request));


        // Create the listen rule for messages in this chat
        ListenRule listenRule = new ListenRule(RequestType.SendMessage, "ChatID", this.chat.ChatID);
        JSONObject listenRuleRequest = new JSONObject();
        listenRuleRequest.put("type", RequestType.AddListenRule);
        listenRuleRequest.put("data", listenRule);

        NetworkerService.SendRequest(SharedData.nsMessenger, new NetworkerService.ListenRuleRequest(MessagingFragment::onStart_LrCallback_message, MessagingFragment::onStart_callback_messageLrResponse, listenRuleRequest));
    }

    private static void onStart_callback_getMessageQueue(Result result, Object response) {
        // Check if the request failed
        if (result == Result.FAILED || response == null) {
            ((MessagingFragment) SharedData.misc.get("fragment")).requireActivity().runOnUiThread(() -> Toast.makeText(((MessagingFragment) SharedData.misc.get("fragment")).requireContext(), "Something went wrong :(", Toast.LENGTH_SHORT).show());
            return;
        }

        // Assign the message queue to the field
        System.out.println(response);
        ((MessagingFragment) SharedData.misc.get("fragment")).messageQueue = (MessageQueue) response;

        // Call the update message box function on the UI thread
        // Passing the instance of the fragment class as a parameter
        try {
            ((MessagingFragment) SharedData.misc.get("fragment")).requireActivity().runOnUiThread(MessagingFragment::updateMessageBoxStart);
            // Hide the loading wheel
            ((MessagingFragment) SharedData.misc.get("fragment")).requireActivity().runOnUiThread(() -> ((MessagingFragment) SharedData.misc.get("fragment")).requireView().findViewById(R.id.messagingLoadingWheel).setVisibility(View.GONE));

        } catch (IllegalStateException e) {
            System.out.println("Message window was closed!");
            return;
        }
    }

    private static void onStart_LrCallback_message(Object response, Bundle bundle) {
        updateMessageBox((Message) ((JSONObject) response).get("data"));
    }

    private static void onStart_callback_messageLrResponse(Result result, Object response) {
        if (result == Result.FAILED) {
            System.out.println("Failed to create listen rule");
            System.exit(1);
        }

        ((MessagingFragment) SharedData.misc.get("fragment")).listenRuleId = (int) response;
    }

    @Override
    public void onStop() {
        super.onStop();

        JSONObject request = new JSONObject();
        request.put("type", RequestType.RemoveListenRule);
        request.put("data", listenRuleId);

        NetworkerService.SendRequest(SharedData.nsMessenger, new NetworkerService.ListenRuleRequest((Object response, Bundle bundle) -> {}, (Result result, Object response) -> {}, request));

        SharedData.activeChatID = -1;
    }

    /**
     * Updates the messagebox with the current contents of the message queue
     */
    public static void updateMessageBoxStart() {
        // Remove all messages currently in the messagebox
        ((MessagingFragment) SharedData.misc.get("fragment")).requireActivity().runOnUiThread(() -> ((LinearLayout) ((MessagingFragment) SharedData.misc.get("fragment")).requireView().findViewById(R.id.MessageBox)).removeAllViews());


        // Add the new messages
        for (int i = 0; i < 50; i++) {
            /*while (fragment.networkerService.waitingForResponse) {
                System.out.println("Waiting for response");
            }*/

            Message message = ((MessagingFragment) SharedData.misc.get("fragment")).messageQueue.Get(i);
            if (message == null) {
                continue;
            }

            // If the user that sent the message is not currently in the hashmap, request the user from the server and add them
            // Then we can add the message
            if (((MainActivity) ((MessagingFragment) SharedData.misc.get("fragment")).requireActivity()).users.get(message.SenderID) == null) {
                JSONObject request = new JSONObject();
                request.put("type", RequestType.GetUser);
                request.put("selector", "id");
                request.put("data", new User(message.SenderID, null, null, null, null, null));

                NetworkerService.SendRequest(SharedData.nsMessenger, new NetworkerService.Request((Result result, Object response) -> {
                    ((MainActivity) ((MessagingFragment) SharedData.misc.get("fragment")).requireActivity()).users.put(((User) response).UserID, (User) response);

                    // Decrypt the message before passing it to the fragment
                    String content = null;
                    try {
                        content = (String) ((MessagingFragment) SharedData.misc.get("fragment")).privateKey.decrypt((EncryptedObject) message.Content);

                    } catch (PrivateKeyException e) {
                        e.printStackTrace();
                        System.exit(1);
                    }

                    // Create a new message object with the decrypted contents
                    Message decryptedMessage = new Message(message.SenderID, message.ChatID, message.TimeSent, content);

                    // Add the message to the view as a fragment
                    Bundle bundle = new Bundle();
                    bundle.putSerializable("message", decryptedMessage);
                    bundle.putBoolean("fromOtherUser", message.SenderID != SharedData.user.UserID);

                    ((MessagingFragment) SharedData.misc.get("fragment")).getChildFragmentManager().beginTransaction()
                            .setReorderingAllowed(true)
                            .add(R.id.MessageBox, MessageFragment.class, bundle)
                            .commit();
                }, request));
            }
            else {
                // Decrypt the message contents
                String content = null;
                try {
                    content = (String) ((MessagingFragment) SharedData.misc.get("fragment")).privateKey.decrypt((EncryptedObject) message.Content);

                } catch (PrivateKeyException e) {
                    e.printStackTrace();
                    System.exit(1);
                }

                // Add the message to the view as a fragment
                Bundle bundle = new Bundle();
                bundle.putSerializable("message", new Message(message.SenderID, message.ChatID, message.TimeSent, content));
                bundle.putBoolean("fromOtherUser", message.SenderID != SharedData.user.UserID);

                ((MessagingFragment) SharedData.misc.get("fragment")).getChildFragmentManager().beginTransaction()
                        .setReorderingAllowed(true)
                        .add(R.id.MessageBox, MessageFragment.class, bundle)
                        .commit();
            }
        }

        // Scroll to the bottom of the scroll view
        ((ScrollView) ((MessagingFragment) SharedData.misc.get("fragment")).requireView().findViewById(R.id.MessageBoxScrollView)).fullScroll(View.FOCUS_DOWN);
    }

    /**
     * Updates the message box with the current contents of the chat following the listen rule architecture
     * @param newMessage The new message that was sent into the chat
     */
    public static void updateMessageBox(Message newMessage) {
        // If the user that sent the message is not currently in the hashmap, request the user from the server and add them
        // Then we can add the message
        if (((MainActivity) ((MessagingFragment) SharedData.misc.get("fragment")).requireActivity()).users.get(newMessage.SenderID) == null) {
            JSONObject request = new JSONObject();
            request.put("type", RequestType.GetUser);
            request.put("selector", "id");
            request.put("data", new User(newMessage.SenderID, null, null, null, null, null));

            NetworkerService.SendRequest(SharedData.nsMessenger, new NetworkerService.Request(new NetworkerService.IRequestCallback() {
                @Override
                public void callback(Result result, Object response) {
                    ((MainActivity) ((MessagingFragment) SharedData.misc.get("fragment")).requireActivity()).users.put(((User) response).UserID, (User) response);

                    // Decrypt the message before passing it to the fragment
                    String content = null;
                    try {
                        content = (String) ((MessagingFragment) SharedData.misc.get("fragment")).privateKey.decrypt((EncryptedObject) newMessage.Content);

                    } catch (PrivateKeyException e) {
                        e.printStackTrace();
                        System.exit(1);
                    }

                    // Create a new message object with the decrypted contents
                    Message decryptedMessage = new Message(newMessage.SenderID, newMessage.ChatID, newMessage.TimeSent, content);

                    // Add the message to the view as a fragment
                    Bundle bundle = new Bundle();
                    bundle.putSerializable("message", decryptedMessage);
                    bundle.putBoolean("fromOtherUser", newMessage.SenderID != SharedData.user.UserID);

                    ((MessagingFragment) SharedData.misc.get("fragment")).getChildFragmentManager().beginTransaction()
                            .setReorderingAllowed(true)
                            .add(R.id.MessageBox, MessageFragment.class, bundle)
                            .commit();

                    // Scroll to the bottom of the scroll view
                    ((MessagingFragment) SharedData.misc.get("fragment")).requireActivity().runOnUiThread(() -> ((ScrollView) ((MessagingFragment) SharedData.misc.get("fragment")).requireView().findViewById(R.id.MessageBoxScrollView)).fullScroll(View.FOCUS_DOWN));
                }
            }, request));
        }
        else {
            // Decrypt the contents of the message
            String content = null;
            try {
                content = (String) ((MessagingFragment) SharedData.misc.get("fragment")).privateKey.decrypt((EncryptedObject) newMessage.Content);

            } catch (PrivateKeyException e) {
                e.printStackTrace();
                System.exit(1);
            }

            // Add the message to the view as a fragment
            Bundle bundle = new Bundle();
            bundle.putSerializable("message", new Message(newMessage.SenderID, newMessage.ChatID, newMessage.TimeSent, content));
            bundle.putBoolean("fromOtherUser", newMessage.SenderID != SharedData.user.UserID);

            ((MessagingFragment) SharedData.misc.get("fragment")).getChildFragmentManager().beginTransaction()
                    .setReorderingAllowed(true)
                    .add(R.id.MessageBox, MessageFragment.class, bundle)
                    .commit();
        }
    }
}
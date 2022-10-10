package com.nathcat.messagecat_client;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.nathcat.RSA.EncryptedObject;
import com.nathcat.RSA.KeyPair;
import com.nathcat.RSA.ObjectContainer;
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
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;

public class MessagingFragment extends Fragment {

    /**
     * Used to continuously update the message box
     * @deprecated
     */
    private class MessageBoxUpdaterThread extends Thread {
        public boolean allowRun = true;
        private KeyPair privateKey;

        public MessageBoxUpdaterThread() throws IOException {
            super();

            KeyStore keyStore = new KeyStore(new File(requireActivity().getFilesDir(), "KeyStore.bin"));
            privateKey = keyStore.GetKeyPair(MessagingFragment.this.chat.PublicKeyID);
        }

        public void run() {
            System.out.println("Thread started");

            while (allowRun) {
                try {
                    // Check if the networker service is already waiting for a response
                    if (networkerService.waitingForResponse) {
                        Thread.sleep(100);
                        continue;
                    }

                    // Create a request to get the message queue for this chat
                    JSONObject request = new JSONObject();
                    request.put("type", RequestType.GetMessageQueue);
                    request.put("data", new ObjectContainer(chat.ChatID));

                    networkerService.SendRequest(new NetworkerService.Request(new NetworkerService.IRequestCallback() {
                        @Override
                        public void callback(Result result, Object response) {
                            // Check if the request failed
                            if (result == Result.FAILED || response == null) {
                                requireActivity().runOnUiThread(() -> Toast.makeText(requireContext(), "Something went wrong :(", Toast.LENGTH_SHORT).show());
                                return;
                            }

                            // Assign the message queue to the field
                            messageQueue = (MessageQueue) ((ObjectContainer) ((ObjectContainer) response).obj).obj;

                            // Call the update message box function on the UI thread
                            // Passing the instance of the fragment class as a parameter
                            requireActivity().runOnUiThread(() -> MessagingFragment.updateMessageBoxStart(MessagingFragment.this, privateKey));

                            networkerService.waitingForResponse = false;
                        }
                    }, request));

                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            System.out.println("Thread stopped");
        }
    }

    public Chat chat;
    private KeyPair privateKey;
    private NetworkerService networkerService;
    private MessageQueue messageQueue;
    private long newestMessageTimeSent = 0;
    private int listenRuleId = -1;

    public MessagingFragment() {
        super(R.layout.fragment_messaging);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Get the chat argument passed to this fragment, and the netorker service instance from
        // the main activity
        chat = (Chat) requireArguments().getSerializable("chat");
        networkerService = ((MainActivity) requireActivity()).networkerService;
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

        ((MainActivity) requireActivity()).messagingFragment = this;

        try {
            KeyStore keyStore = new KeyStore(new File(requireActivity().getFilesDir(), "KeyStore.bin"));
            privateKey = keyStore.GetKeyPair(MessagingFragment.this.chat.PublicKeyID);

        } catch (IOException e) {
            e.printStackTrace();
        }

        assert privateKey != null;

        // Create the users hashmap and put the current user in it
        ((MainActivity) requireActivity()).users = new HashMap<>();
        ((MainActivity) requireActivity()).users.put(networkerService.user.UserID, networkerService.user);

        networkerService.waitingForResponse = false;

        // Request the message queue from the server
        JSONObject request = new JSONObject();
        request.put("type", RequestType.GetMessageQueue);
        request.put("data", new ObjectContainer(chat.ChatID));

        networkerService.SendRequest(new NetworkerService.Request(new NetworkerService.IRequestCallback() {
            @Override
            public void callback(Result result, Object response) {
                // Check if the request failed
                if (result == Result.FAILED || response == null) {
                    requireActivity().runOnUiThread(() -> Toast.makeText(requireContext(), "Something went wrong :(", Toast.LENGTH_SHORT).show());
                    return;
                }

                // Assign the message queue to the field
                messageQueue = (MessageQueue) ((ObjectContainer) ((ObjectContainer) response).obj).obj;

                // Call the update message box function on the UI thread
                // Passing the instance of the fragment class as a parameter
                requireActivity().runOnUiThread(() -> MessagingFragment.updateMessageBoxStart(MessagingFragment.this, privateKey));

                networkerService.waitingForResponse = false;
            }
        }, request));


        // Create the listen rule for messages in this chat
        ListenRule listenRule = new ListenRule(RequestType.SendMessage, "ChatID", this.chat.ChatID);
        JSONObject listenRuleRequest = new JSONObject();
        listenRuleRequest.put("type", RequestType.AddListenRule);
        listenRuleRequest.put("data", new ObjectContainer(listenRule));

        networkerService.SendListenRuleRequest(new NetworkerService.ListenRuleRequest(new NetworkerService.IListenRuleCallback() {
            @Override
            public void callback(Object response) {
                updateMessageBox((Message) ((ObjectContainer) ((JSONObject) response).get("data")).obj);
            }
        }, new NetworkerService.IRequestCallback() {
            @Override
            public void callback(Result result, Object response) {
                if (result == Result.FAILED) {
                    System.out.println("Failed to create listen rule");
                    System.exit(1);
                }

                listenRuleId = (int) response;
            }
        }, listenRuleRequest));
    }

    @Override
    public void onStop() {
        super.onStop();

        JSONObject request = new JSONObject();
        request.put("type", RequestType.RemoveListenRule);
        request.put("data", new ObjectContainer(listenRuleId));

        networkerService.SendListenRuleRequest(new NetworkerService.ListenRuleRequest(new NetworkerService.IListenRuleCallback() {
            @Override
            public void callback(Object response) {
                NetworkerService.IListenRuleCallback.super.callback(response);
            }
        }, new NetworkerService.IRequestCallback() {
            @Override
            public void callback(Result result, Object response) {
                NetworkerService.IRequestCallback.super.callback(result, response);
            }
        }, request));
    }

    /**
     * Updates the messagebox with the current contents of the message queue
     * @param fragment The instance of the current fragment class, this is necessary as this method will mostly be called from a Runnable, which is a static context
     */
    public static void updateMessageBoxStart(MessagingFragment fragment, KeyPair privateKey) {
        // Remove all messages currently in the messagebox
        //((LinearLayout) fragment.requireView().findViewById(R.id.MessageBox)).removeAllViews();

        // Add the new messages
        for (int i = 49; i >= 0; i--) {
            Message message = fragment.messageQueue.Get(i);
            if (message == null) {
                continue;
            }

            if (message.TimeSent > fragment.newestMessageTimeSent) {
                fragment.newestMessageTimeSent = message.TimeSent;
            }
            else {
                continue;
            }

            // If the user that sent the message is not currently in the hashmap, request the user from the server and add them
            // Then we can add the message
            if (((MainActivity) fragment.requireActivity()).users.get(message.SenderID) == null) {
                JSONObject request = new JSONObject();
                request.put("type", RequestType.GetUser);
                request.put("selector", "id");
                request.put("data", new ObjectContainer(new User(message.SenderID, null, null, null, null, null)));

                fragment.networkerService.SendRequest(new NetworkerService.Request(new NetworkerService.IRequestCallback() {
                    @Override
                    public void callback(Result result, Object response) {
                        ((MainActivity) fragment.requireActivity()).users.put(((User) response).UserID, (User) response);

                        // Decrypt the message before passing it to the fragment
                        String content = null;
                        try {
                            content = (String) privateKey.decryptBigObject((EncryptedObject[]) message.Content);

                        } catch (PrivateKeyException e) {
                            e.printStackTrace();
                            System.exit(1);
                        }

                        // Create a new message object with the decrypted contents
                        Message decryptedMessage = new Message(message.SenderID, message.ChatID, message.TimeSent, content);

                        // Add the message to the view as a fragment
                        Bundle bundle = new Bundle();
                        bundle.putSerializable("message", decryptedMessage);
                        bundle.putBoolean("fromOtherUser", message.SenderID != fragment.networkerService.user.UserID);

                        fragment.getChildFragmentManager().beginTransaction()
                                .setReorderingAllowed(true)
                                .add(R.id.MessageBox, MessageFragment.class, bundle)
                                .commit();
                    }
                }, request));
            }
            else {
                // Decrypt the message contents
                String content = null;
                try {
                    content = (String) privateKey.decryptBigObject((EncryptedObject[]) message.Content);

                } catch (PrivateKeyException e) {
                    e.printStackTrace();
                    System.exit(1);
                }

                // Add the message to the view as a fragment
                Bundle bundle = new Bundle();
                bundle.putSerializable("message", new Message(message.SenderID, message.ChatID, message.TimeSent, content));
                bundle.putBoolean("fromOtherUser", message.SenderID != fragment.networkerService.user.UserID);

                fragment.getChildFragmentManager().beginTransaction()
                        .setReorderingAllowed(true)
                        .add(R.id.MessageBox, MessageFragment.class, bundle)
                        .commit();
            }
        }

        fragment.networkerService.waitingForResponse = false;
    }

    /**
     * Updates the message box with the current contents of the chat following the listen rule architecture
     */
    public void updateMessageBox(Message newMessage) {
        // If the user that sent the message is not currently in the hashmap, request the user from the server and add them
        // Then we can add the message
        if (((MainActivity) requireActivity()).users.get(newMessage.SenderID) == null) {
            JSONObject request = new JSONObject();
            request.put("type", RequestType.GetUser);
            request.put("selector", "id");
            request.put("data", new ObjectContainer(new User((int) newMessage.SenderID, null, null, null, null, null)));

            networkerService.SendRequest(new NetworkerService.Request(new NetworkerService.IRequestCallback() {
                @Override
                public void callback(Result result, Object response) {
                    ((MainActivity) requireActivity()).users.put(((User) response).UserID, (User) response);

                    // Decrypt the message before passing it to the fragment
                    String content = null;
                    try {
                        content = (String) privateKey.decryptBigObject((EncryptedObject[]) newMessage.Content);

                    } catch (PrivateKeyException e) {
                        e.printStackTrace();
                        System.exit(1);
                    }

                    // Create a new message object with the decrypted contents
                    Message decryptedMessage = new Message(newMessage.SenderID, newMessage.ChatID, newMessage.TimeSent, content);

                    // Add the message to the view as a fragment
                    Bundle bundle = new Bundle();
                    bundle.putSerializable("message", decryptedMessage);
                    bundle.putBoolean("fromOtherUser", newMessage.SenderID != networkerService.user.UserID);

                    getChildFragmentManager().beginTransaction()
                            .setReorderingAllowed(true)
                            .add(R.id.MessageBox, MessageFragment.class, bundle)
                            .commit();
                }
            }, request));
        }
        else {
            // Decrypt the contents of the message
            String content = null;
            try {
                content = (String) privateKey.decryptBigObject((EncryptedObject[]) newMessage.Content);

            } catch (PrivateKeyException e) {
                e.printStackTrace();
                System.exit(1);
            }

            // Add the message to the view as a fragment
            Bundle bundle = new Bundle();
            bundle.putSerializable("message", new Message(newMessage.SenderID, newMessage.ChatID, newMessage.TimeSent, content));
            bundle.putBoolean("fromOtherUser", newMessage.SenderID != networkerService.user.UserID);

            getChildFragmentManager().beginTransaction()
                    .setReorderingAllowed(true)
                    .add(R.id.MessageBox, MessageFragment.class, bundle)
                    .commit();
        }
    }
}
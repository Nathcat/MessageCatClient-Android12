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
                            requireActivity().runOnUiThread(() -> MessagingFragment.updateMessageBox(MessagingFragment.this, privateKey));

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
    private NetworkerService networkerService;
    private MessageQueue messageQueue;
    private MessageBoxUpdaterThread updaterThread;
    private long newestMessageTimeSent = 0;

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

        // Create the users hashmap and put the current user in it
        ((MainActivity) requireActivity()).users = new HashMap<>();
        ((MainActivity) requireActivity()).users.put(networkerService.user.UserID, networkerService.user);

        networkerService.waitingForResponse = false;


        // Create and start the message box updater thread
        try {
            updaterThread = new MessageBoxUpdaterThread();
            updaterThread.start();

        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    @Override
    public void onStop() {
        super.onStop();

        updaterThread.allowRun = false;
    }

    /**
     * Updates the messagebox with the current contents of the message queue
     * @param fragment The instance of the current fragment class, this is necessary as this method will mostly be called from a Runnable, which is a static context
     * TODO This is untested
     */
    public static void updateMessageBox(MessagingFragment fragment, KeyPair privateKey) {
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
                request.put("data", new ObjectContainer(new User((int) message.SenderID, null, null, null, null, null)));

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

                        Message decryptedMessage = new Message(message.SenderID, message.ChatID, message.TimeSent, content);

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
                String content = null;
                try {
                    content = (String) privateKey.decryptBigObject((EncryptedObject[]) message.Content);

                } catch (PrivateKeyException e) {
                    e.printStackTrace();
                    System.exit(1);
                }

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
}
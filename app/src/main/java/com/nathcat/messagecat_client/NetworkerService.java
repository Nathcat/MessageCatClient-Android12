package com.nathcat.messagecat_client;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Bundle;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

;
import com.nathcat.messagecat_database.MessageQueue;
import com.nathcat.messagecat_database_entities.Chat;
import com.nathcat.messagecat_database_entities.ChatInvite;
import com.nathcat.messagecat_database_entities.FriendRequest;
import com.nathcat.messagecat_database_entities.User;
import com.nathcat.messagecat_server.ListenRule;
import com.nathcat.messagecat_server.RequestType;
import com.nathcat.messagecat_database.Result;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Date;

/**
 * Background service used to handle networking tasks.
 *
 * @author Nathan "Nathcat" Baines
 */
public class NetworkerService extends Service implements Serializable {
    /**
     * Used to manage notifications.
     * All notifications will be sent through this class
     */
    public class NotificationChannel {
        private final String channelName;         // Name of the notification channel
        private final String channelDescription;  // Description of the notification channel

        public NotificationChannel(String channelName, String channelDescription, int importance) {
            this.channelName = channelName;
            this.channelDescription = channelDescription;

            // Create the notification channel
            android.app.NotificationChannel channel = new android.app.NotificationChannel(this.channelName, this.channelName, importance);
            channel.setDescription(this.channelDescription);
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }

        /**
         * Create and display a notification from a given title and message.
         *
         * @param title The title of the notification
         * @param message The message of the notification
         */
        public void showNotification(String title, String message) {
            NotificationCompat.Builder builder = new NotificationCompat.Builder(NetworkerService.this, this.channelName)
                    .setSmallIcon(R.drawable.cat_notification)
                    .setContentTitle(title)
                    .setContentText(message)
                    .setPriority(Notification.PRIORITY_MAX)
                    .setDefaults(Notification.DEFAULT_SOUND | Notification.DEFAULT_VIBRATE);

            NotificationManagerCompat manager = NotificationManagerCompat.from(NetworkerService.this);
            manager.notify(0, builder.build());
        }
    }

    /**
     * Passed to the active ConnectionHandler, contains request object and a callback
     */
    public static class Request {
        public final IRequestCallback callback;  // The callback to be performed when a response is received
        public final Object request;             // The request object
        public final Bundle bundle;              // External data that may be needed in the callback

        public Request(IRequestCallback callback, Object request) {
            this.callback = callback;
            this.request = request;
            this.bundle = null;
        }

        public Request(IRequestCallback callback, Object request, Bundle bundle) {
            this.callback = callback;
            this.request = request;
            this.bundle = bundle;
        }
    }

    /**
     * Request callback interface.
     * Implement this and pass to the Request object to create a callback
     */
    public interface IRequestCallback {
        default void callback(Result result, Object response) {}
    }

    /**
     * Passed to the active ListenRuleHandler to create a new listen rule on the server
     */
    public static class ListenRuleRequest extends Request {
        public final IListenRuleCallback lrCallback;      // Called when the listen rule is triggered

        public ListenRuleRequest(IListenRuleCallback lrCallback, IRequestCallback callback, Object request) {
            super(callback, request);
            this.lrCallback = lrCallback;
        }

        public ListenRuleRequest(IListenRuleCallback lrCallback, IRequestCallback callback, Object request, Bundle bundle) {
            super(callback, request, bundle);
            this.lrCallback = lrCallback;
        }
    }

    /**
     * Listen rule callback interface, called when a listen rule is triggered
     */
    public interface IListenRuleCallback {
        default void callback(Object response) {}
        default void callback(Object response, Bundle bundle) {}
    }

    /**
     * Used by the main application activity (UI thread) to get the active instance of this service
     */
    public class NetworkerServiceBinder extends Binder {
        /**
         * Get an instance of the active service
         * @return The instance of the active service
         */
        NetworkerService getService() {
            return NetworkerService.this;
        }
    }

    public NotificationChannel notificationChannel;   // The notification channel to be used to send notifications
    public NotificationChannel serviceStatusChannel;  // The notification channel used to show service notifications
    private ConnectionHandler connectionHandler;      // The active connection handler
    private Looper connectionHandlerLooper;           // The Handler looper attached to the active connection handler
    public boolean authenticated = false;             // Is the client currently authenticated
    public boolean waitingForResponse = false;        // Is the client currently waiting for a response
    private final NetworkerServiceBinder binder = new NetworkerServiceBinder();
    private boolean bound = false;                    // Is the service currently bound to the UI thread
    public User user = null;                          // The user that is currently authenticated
    public int activeChatID = -1;                     // The id of the chat that is currently being viewed, or -1 if none are being viewed
    public static final String hostName = "192.168.1.26";

    /**
     * Returns a binder
     * @param intent The intent to bind to
     * @return The binder for this service
     */
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        bound = true;
        return this.binder;
    }

    /**
     * Unbind from another process
     * @param intent The intent that just unbound from this service
     * @return false, indicating to Android that the service should not try to rebind
     */
    @Override
    public boolean onUnbind(Intent intent) {
        bound = false;
        return false;
    }

    /**
     * Called when the service is created
     */
    @Override
    public void onCreate() {
        // Create the notification channel
        notificationChannel = new NotificationChannel(
                "MessageCat",
                "Notification channel used to send notifications to the user about things that happen in the app.",
                NotificationManager.IMPORTANCE_HIGH
        );

        serviceStatusChannel = new NotificationChannel(
                "MessageCat Service",
                "Used to notify the user that the MessageCat service is running",
                NotificationManager.IMPORTANCE_NONE
        );

        startForeground(1, new Notification.Builder(this, serviceStatusChannel.channelName)
                .setSmallIcon(R.drawable.cat_notification)
                .setContentTitle("MessageCat")
                .setContentText("MessageCat service is running")
                .setPriority(Notification.PRIORITY_LOW)
                .build());

        startConnectionHandler();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    /**
     * Send a request to the server via the connection handler
     * @param request The request object
     */
    public void SendRequest(Request request) {
        this.waitingForResponse = true;  // Indicate that the client is waiting for a response
        // Create the message to the connection handler
        Message msg = connectionHandler.obtainMessage();
        msg.obj = request;
        msg.what = 1;

        // Send the request to the connection handler to be sent off to the server
        connectionHandler.sendMessage(msg);
    }

    @Override
    public void onTaskRemoved(Intent intent) {
        System.out.println("Service task removed");
    }

    @Override
    public void onDestroy() {
        System.out.println("Service destroyed");
        connectionHandler.sendEmptyMessage(2);
        super.onDestroy();
    }

    public void startConnectionHandler() {
        // Create the connection handler thread
        HandlerThread thread = new HandlerThread("MessageCatNetworkingHandlerThread", 10);
        thread.start();

        this.connectionHandlerLooper = thread.getLooper();
        this.connectionHandler = new ConnectionHandler(this, this.connectionHandlerLooper);

        // Initialise the connection to the server
        connectionHandler.sendEmptyMessage(0);

        // Try and find auth data
        File authDataFile = new File(getFilesDir(), "UserData.bin");
        if (authDataFile.exists()) {
            // Try to use the data in the auth file to authenticate the client
            try {
                ObjectInputStream authDataInputStream = new ObjectInputStream(new FileInputStream(authDataFile));
                Object userData = authDataInputStream.readObject();
                authDataInputStream.close();

                // Create the request and send it to the server
                JSONObject requestData = new JSONObject();
                requestData.put("type", RequestType.Authenticate);
                requestData.put("data", userData);

                // Send the authentication request
                this.SendRequest(new Request(new IRequestCallback() {
                    @Override
                    public void callback(Result result, Object response) {
                        if (result == Result.FAILED) {
                            startConnectionHandler();
                            return;
                        }

                        if (response.getClass() == String.class) {
                            authenticated = false;
                            waitingForResponse = false;
                            Toast.makeText(NetworkerService.this, "Finished auth", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        // If authentication was successful...
                        if (result == Result.SUCCESS) {
                            authenticated = true;

                            // Update the data in the auth file
                            try {
                                ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(new File(getFilesDir(), "UserData.bin")));
                                oos.writeObject(response);
                                oos.flush();
                                oos.close();

                                user = (User) response;

                            } catch (IOException e) {
                                e.printStackTrace();
                            }

                            // Add the notification listen rules
                            JSONObject friendRequestRuleRequest = new JSONObject();
                            friendRequestRuleRequest.put("type", RequestType.AddListenRule);
                            friendRequestRuleRequest.put("data", new ListenRule(RequestType.SendFriendRequest, "RecipientID", user.UserID));
                            SendRequest(new ListenRuleRequest(new IListenRuleCallback() {
                                @Override
                                public void callback(Object response) {
                                    // Get the friend request from the response object
                                    FriendRequest friendRequest = (FriendRequest) ((JSONObject) response).get("data");

                                    // Request the user that sent the request from the server
                                    JSONObject senderRequest = new JSONObject();
                                    senderRequest.put("type", RequestType.GetUser);
                                    senderRequest.put("selector", "id");
                                    senderRequest.put("data", new User(friendRequest.SenderID, null, null, null, null, null));
                                    SendRequest(new Request(new IRequestCallback() {
                                        @Override
                                        public void callback(Result result, Object response) {
                                            if (result == Result.FAILED) {
                                                return;
                                            }

                                            // Cast the response into a user object
                                            User sender = (User) response;
                                            // Show the notification
                                            notificationChannel.showNotification("New friend request", sender.DisplayName + " wants to be friends!");
                                        }
                                    }, senderRequest));
                                }
                            }, new IRequestCallback() {
                                @Override
                                public void callback(Result result, Object response) {
                                    IRequestCallback.super.callback(result, response);
                                }
                            }, friendRequestRuleRequest));

                            JSONObject chatRequestRuleRequest = new JSONObject();
                            chatRequestRuleRequest.put("type", RequestType.AddListenRule);
                            chatRequestRuleRequest.put("data", new ListenRule(RequestType.SendChatInvite, "RecipientID", user.UserID));
                            SendRequest(new ListenRuleRequest(new IListenRuleCallback() {
                                @Override
                                public void callback(Object response) {
                                    // Get the chat invite from the request that triggered the listen rule
                                    ChatInvite chatInvite = (ChatInvite) ((JSONObject) response).get("data");

                                    // Create a new request to get the chat that the user has been invited to
                                    JSONObject getChatRequest = new JSONObject();
                                    getChatRequest.put("type", RequestType.GetChat);
                                    getChatRequest.put("data", new Chat(chatInvite.ChatID, null, null, -1));

                                    SendRequest(new Request(new IRequestCallback() {
                                        @Override
                                        public void callback(Result result, Object response) {
                                            if (result == Result.FAILED) {
                                                return;
                                            }

                                            // Cast the response to a Chat object
                                            Chat chat = (Chat) response;
                                            // Show the notification
                                            notificationChannel.showNotification("New chat invitation", "You have been invited to " + chat.Name);
                                        }
                                    }, getChatRequest));
                                }
                            }, new IRequestCallback() {
                                @Override
                                public void callback(Result result, Object response) {
                                    IRequestCallback.super.callback(result, response);
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

                                        SendRequest(new ListenRuleRequest(new IListenRuleCallback() {
                                            @Override
                                            public void callback(Object response, Bundle bundle) {
                                                if (activeChatID != ((Chat) bundle.getSerializable("chat")).ChatID) {
                                                    // Create a notification
                                                    notificationChannel.showNotification("New message", "You have a new message in " + ((Chat) bundle.getSerializable("chat")).Name);
                                                }
                                            }
                                        }, new IRequestCallback() {
                                            @Override
                                            public void callback(Result result, Object response) {
                                                IRequestCallback.super.callback(result, response);
                                            }
                                        }, msgRule, bundle));
                                    }

                                } catch (IOException | ClassNotFoundException e) {
                                    e.printStackTrace();
                                }

                            }
                        }

                        waitingForResponse = false;
                    }
                }, requestData));

            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace();
            }
        }
    }
}

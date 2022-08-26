package com.nathcat.messagecat_client;

import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import com.nathcat.messagecat_database.MessageQueue;
import com.nathcat.messagecat_database_entities.Chat;
import com.nathcat.messagecat_database_entities.ChatInvite;
import com.nathcat.messagecat_database_entities.FriendRequest;
import com.nathcat.messagecat_database_entities.User;
import com.nathcat.messagecat_server.RequestType;
import com.nathcat.messagecat_database.Result;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Date;

/**
 * Background service used to handle networking tasks.
 *
 * @author Nathan "Nathcat" Baines
 */
public class NetworkerService extends Service {
    /**
     * Used to manage notifications.
     * All notifications will be sent through this class
     */
    public class NotificationChannel {
        private final String channelName;         // Name of the notification channel
        private final String channelDescription;  // Description of the notification channel

        public NotificationChannel(String channelName, String channelDescription) {
            this.channelName = channelName;
            this.channelDescription = channelDescription;

            // Create the notification channel with default importance
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
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
                    .setSmallIcon(R.drawable.ic_launcher_foreground)  // TODO Change this to app logo
                    .setContentTitle(title)
                    .setContentText(message)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT);

            NotificationManagerCompat manager = NotificationManagerCompat.from(NetworkerService.this);
            manager.notify(0, builder.build());
        }
    }

    /**
     * Passed to the active ConnectionHandler, contains request object and a callback
     */
    public class Request {
        public final IRequestCallback callback;  // The callback to be performed when a response is received
        public final Object request;             // The request object

        public Request(IRequestCallback callback, Object request) {
            this.callback = callback;
            this.request = request;
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

    /**
     * Process which manages notifications while the application is not open
     */
    private class NotifierRoutine extends Thread {
        @Override
        public void run() {
            long lastCheckedTime = 0;
            try {
                ObjectInputStream ois = new ObjectInputStream(new FileInputStream(new File(getFilesDir(), "LastCheckedTime.bin")));
                lastCheckedTime = (long) ois.readObject();
                ois.close();

            } catch (IOException | ClassNotFoundException e) {
                lastCheckedTime = new Date().getTime();
                try {
                    ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(new File(getFilesDir(), "LastCheckedTime.bin")));
                    oos.writeObject(lastCheckedTime);
                    oos.flush();
                    oos.close();

                } catch (IOException ioException) {
                    ioException.printStackTrace();
                }
            }

            while (true) {
                try {
                    Thread.sleep(5000);

                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                if (bound || !authenticated) {
                    continue;
                }

                // Get a list of friend requests from the server
                JSONObject request = new JSONObject();
                request.put("type", RequestType.GetFriendRequests);
                request.put("selector", "recipientID");
                request.put("data", new FriendRequest(-1, -1, user.UserID, -1));

                long finalLastCheckedTime = lastCheckedTime;

                SendRequest(new Request(new IRequestCallback() {
                    @Override
                    public void callback(Result result, Object response) {
                        waitingForResponse = false;

                        FriendRequest[] friendRequests = (FriendRequest[]) response;
                        // Check if any of the requests are new
                        for (FriendRequest fr : friendRequests) {
                            if (fr.TimeSent > finalLastCheckedTime) {
                                // Get the sender and send a notification
                                JSONObject senderRequest = new JSONObject();
                                senderRequest.put("type", RequestType.GetUser);
                                senderRequest.put("selector", "id");
                                senderRequest.put("data", new User(fr.SenderID, null, null, null, null, null));

                                SendRequest(new Request(new IRequestCallback() {
                                    @Override
                                    public void callback(Result result, Object response) {
                                        waitingForResponse = false;

                                        notificationChannel.showNotification("You got a friend request!", ((User) response).DisplayName + " wants to be friends!");
                                    }
                                }, senderRequest));
                            }
                        }
                    }
                }, request));

                // Get a list of chat invites from the server
                request = new JSONObject();
                request.put("type", RequestType.GetChatInvite);
                request.put("selector", "recipientID");
                request.put("data", new ChatInvite(-1, -1, -1, user.UserID, -1, -1));

                SendRequest(new Request(new IRequestCallback() {
                    @Override
                    public void callback(Result result, Object response) {
                        waitingForResponse = false;

                        ChatInvite[] chatInvites = (ChatInvite[]) response;
                        // Check if any of the requests are new
                        for (ChatInvite ci : chatInvites) {
                            if (ci.TimeSent > finalLastCheckedTime) {
                                // Get the sender and send a notification
                                JSONObject senderRequest = new JSONObject();
                                senderRequest.put("type", RequestType.GetUser);
                                senderRequest.put("selector", "id");
                                senderRequest.put("data", new User(ci.SenderID, null, null, null, null, null));

                                SendRequest(new Request(new IRequestCallback() {
                                    @Override
                                    public void callback(Result result, Object response) {
                                        waitingForResponse = false;

                                        notificationChannel.showNotification("You were invited to a Chat!", ((User) response).DisplayName + " wants to chat with you!");
                                    }
                                }, senderRequest));
                            }
                        }
                    }
                }, request));

                // Get a list of chats this user is a member of
                try {
                    ObjectInputStream ois = new ObjectInputStream(new FileInputStream(new File(getFilesDir(), "Chats.bin")));
                    Chat[] chats = (Chat[]) ois.readObject();
                    ois.close();

                    // Get the message queues for each of the chats
                    for (int i = 0; i < chats.length; i++) {
                        request = new JSONObject();
                        request.put("type", RequestType.GetMessageQueue);
                        request.put("data", chats[i].ChatID);

                        SendRequest(new Request(new IRequestCallback() {
                            @Override
                            public void callback(Result result, Object response) {
                                waitingForResponse = false;
                                // Get the messages in the message queue as a JSON string
                                String[] messageStrings = ((MessageQueue) response).GetJSONString();
                                for (String messageString : messageStrings) {
                                    try {
                                        // Parse the JSON string and request the sender
                                        JSONObject messageJSON = (JSONObject) new JSONParser().parse(messageString);
                                        // Check if the message has arrived since the last check time
                                        if ((long) messageJSON.get("TimeSent") > finalLastCheckedTime) {
                                            JSONObject senderRequest = new JSONObject();
                                            senderRequest.put("type", RequestType.GetUser);
                                            senderRequest.put("selector", "id");
                                            senderRequest.put("data", new User((int) messageJSON.get("SenderID"), null, null, null, null, null));

                                            SendRequest(new Request(new IRequestCallback() {
                                                @Override
                                                public void callback(Result result, Object response) {
                                                    waitingForResponse = false;
                                                    // Send a notification
                                                    notificationChannel.showNotification("You have a new message!", ((User) response).DisplayName + " sent you a message!");
                                                }
                                            }, senderRequest));
                                        }

                                    } catch (ParseException e) {
                                        e.printStackTrace();
                                    }
                                }
                            }
                        }, request));
                    }

                } catch (IOException | ClassNotFoundException e) {
                    e.printStackTrace();
                }

            }
        }
    }

    public NotificationChannel notificationChannel;  // The notification channel to be used to send notifications
    private ConnectionHandler connectionHandler;     // The active connection handler
    private Looper connectionHandlerLooper;          // The Handler looper attached to the active connection handler
    public boolean authenticated = false;            // Is the client currently authenticated
    public boolean waitingForResponse = false;       // Is the client currently waiting for a response
    private final NetworkerServiceBinder binder = new NetworkerServiceBinder();
    private boolean bound = false;                   // Is the service currently bound to the UI thread
    public User user = null;                         // The user that is currently authenticated

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
        try {
            ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(new File(getFilesDir(), "Chats.bin")));
            oos.writeObject(new Chat[] {new Chat(1, null, null, -1), new Chat(2, null, null, -1)});
            oos.flush();
            oos.close();

        } catch (IOException e) {
            e.printStackTrace();
        }

        // Create the notification channel
        notificationChannel = new NotificationChannel(
                "MessageCatNotificationChannel",
                "Notification channel used to send notifications to the user about things that happen in the app."
        );

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
                        }

                        waitingForResponse = false;
                    }
                }, requestData));

            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Called when the service is started
        NotifierRoutine notifierRoutine = new NotifierRoutine();
        notifierRoutine.setDaemon(true);
        notifierRoutine.start();

        return START_STICKY;
    }

    /**
     * Send a request to the server
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
}

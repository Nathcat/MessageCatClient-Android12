package com.nathcat.messagecat_client;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.telephony.NetworkRegistrationInfo;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

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
import java.nio.file.Files;

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
    public static class Request implements Serializable {
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
    public interface IRequestCallback extends Serializable {
        void callback(Result result, Object response);
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
    public interface IListenRuleCallback extends Serializable {
        void callback(Object response, Bundle bundle);
    }

    public class IncomingMessageHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case CLIENT_SEND_REQUEST:
                    // Check that the contents of the message are a request object
                    if (((Bundle) msg.obj).getSerializable("request").getClass() == NetworkerService.Request.class) {
                        // Send the request
                        Message message = Message.obtain(null, 1);
                        message.obj = new Bundle();
                        ((Bundle) message.obj).putSerializable("request", ((Bundle) msg.obj).getSerializable("request"));
                        connectionHandler.sendMessage(message);

                        if (msg.replyTo == null) {
                            break;
                        }

                        // Give a positive response to the networker service
                        try {
                            msg.replyTo.send(Message.obtain(null, 0));
                        } catch (RemoteException e) {
                            throw new RuntimeException(e);
                        }
                    }
                    else {
                        if (msg.replyTo == null) {
                            break;
                        }

                        // Give a negative response to the networker service.
                        try {
                            msg.replyTo.send(Message.obtain(null, -1));
                        } catch (RemoteException e) {
                            throw new RuntimeException(e);
                        }
                    } break;


                case CLIENT_REGISTER_FOR_EVENTS:
                    // Set the registered event listener
                    registeredEventListener = msg.replyTo;
                    break;

                case CLIENT_UNREGISTER_FOR_EVENTS:
                    // Unset the registered event listener
                    registeredEventListener = null;
                    break;

                case CLIENT_REQUEST_AUTH_STATE:
                    // Reply with the authentication state, as an event code
                    try {
                        Message reply = Message.obtain(null, CLIENT_REQUEST_AUTH_STATE);
                        reply.arg1 = NetworkerService.this.authenticated ? EVENTS_AUTH_SUCCESS : EVENTS_AUTH_FAILED_INVALID_USER;
                        reply.obj = msg.obj;

                        msg.replyTo.send(reply);
                    } catch (RemoteException e) {
                        throw new RuntimeException(e);
                    } break;

                case CLIENT_REQUEST_USER:
                    // Reply with the user data
                    try {
                        Message reply = Message.obtain(null, CLIENT_REQUEST_AUTH_STATE);
                        reply.obj = msg.obj;
                        ((Bundle) reply.obj).putSerializable("user", NetworkerService.this.user);

                        msg.replyTo.send(reply);
                    } catch (RemoteException e) {
                        throw new RuntimeException(e);
                    } break;

                case CLIENT_REQUEST_ACTIVE_CHAT_ID:
                    // Reply with the user data
                    try {
                        Message reply = Message.obtain(null, CLIENT_REQUEST_ACTIVE_CHAT_ID);
                        reply.obj = msg.obj;
                        ((Bundle) reply.obj).putSerializable("active_chat_id", NetworkerService.this.activeChatID);

                        msg.replyTo.send(reply);
                    } catch (RemoteException e) {
                        throw new RuntimeException(e);
                    } break;

                case CLIENT_SEND_NOTIFICATION:
                    Bundle data = (Bundle) msg.obj;
                    notificationChannel.showNotification(data.getString("title"), data.getString("content"));
                    break;

                case CLIENT_SET_ACTIVE_CHAT_ID:
                    activeChatID = ((Bundle) msg.obj).getInt("active_chat_id");
                    break;

                case RESTART_CONNECTION_HANDLER:
                    startConnectionHandler();
                    break;

                case CLIENT_AUTHENTICATE:
                    authenticate();
                    break;
            }
        }
    }

    public Messenger registeredEventListener = null;  // The currently registered event listener

    public NotificationChannel notificationChannel;   // The notification channel to be used to send notifications
    public NotificationChannel serviceStatusChannel;  // The notification channel used to show service notifications
    private ConnectionHandler connectionHandler;      // The active connection handler
    private Looper connectionHandlerLooper;
    public boolean authenticated = false;             // Is the client currently authenticated
    public Messenger messenger;                       // The messenger used to transmit messages between processes
    private boolean bound = false;                    // Is the service currently bound to the UI thread
    public User user = null;                          // The user that is currently authenticated
    public int activeChatID = -1;                     // The id of the chat that is currently being viewed, or -1 if none are being viewed
    public static final String hostName = "192.168.1.26";

    public static final int EVENTS_AUTH_SUCCESS = 0;
    public static final int EVENTS_AUTH_FAILED_INVALID_USER = 1;
    public static final int CLIENT_REGISTER_FOR_EVENTS = 2;
    public static final int CLIENT_REQUEST_AUTH_STATE = 3;
    public static final int CLIENT_SEND_REQUEST = 4;
    public static final int CLIENT_UNREGISTER_FOR_EVENTS = 5;
    public static final int CLIENT_REQUEST_USER = 6;
    public static final int CLIENT_REQUEST_ACTIVE_CHAT_ID = 7;
    public static final int CLIENT_SEND_NOTIFICATION = 8;
    public static final int CLIENT_SET_ACTIVE_CHAT_ID = 9;
    public static final int RESTART_CONNECTION_HANDLER = 10;
    public static final int CLIENT_AUTHENTICATE = 11;

    /**
     * Returns a binder
     * @param intent The intent to bind to
     * @return The binder for this service
     */
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        bound = true;
        return messenger.getBinder();
    }

    /**
     * Unbind from another process
     * @param intent The intent that just unbound from this service
     * @return false, indicating to Android that the service should not try to rebind
     */
    @Override
    public boolean onUnbind(Intent intent) {
        bound = false;
        registeredEventListener = null;
        return false;
    }

    /**
     * Called when the service is created
     */
    @Override
    public void onCreate() {
        System.out.println("Service: onCreate");

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
        System.out.println("Service: onStartCommand");
        return START_STICKY;
    }

    /**
     * Send a request to the server via the connection handler
     * @param request The request object
     */
    public static void SendRequest(Messenger messenger, Request request) {
        Bundle b = new Bundle();
        b.putSerializable("request", request);

        try {
            messenger.send(Message.obtain(null, CLIENT_SEND_REQUEST, b));
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void onTaskRemoved(Intent intent) {
        System.out.println("Service task removed");
    }

    @Override
    public void onDestroy() {
        System.out.println("Service destroyed");
        connectionHandler.sendEmptyMessage(2);
    }

    public void startConnectionHandler() {
        System.out.println("Service: startConnectionHandler");
        messenger = new Messenger(new IncomingMessageHandler());

        HandlerThread thread = new HandlerThread("MessageCatConnectionHandlerThread", 10);
        thread.start();

        this.connectionHandlerLooper = thread.getLooper();

        connectionHandler = new ConnectionHandler(this.connectionHandlerLooper, this);
        connectionHandler.sendEmptyMessage(0);

        authenticate();
    }

    public void authenticate() {
        System.out.println("Service: authenticate");
        // Try and find auth data
        File authDataFile = new File(getFilesDir(), "UserData.bin");
        if (authDataFile.exists()) {
            // Try to use the data in the auth file to authenticate the client
            try {
                ObjectInputStream authDataInputStream = new ObjectInputStream(Files.newInputStream(authDataFile.toPath()));
                Object userData = authDataInputStream.readObject();
                authDataInputStream.close();

                // Create the request and send it to the server
                JSONObject requestData = new JSONObject();
                requestData.put("type", RequestType.Authenticate);
                requestData.put("data", userData);

                // Send the authentication request
                SendRequest(this.messenger, new Request(new IRequestCallback() {
                    @Override
                    public void callback(Result result, Object response) {
                        if (result == Result.FAILED) {
                            startConnectionHandler();
                            return;
                        }

                        if (response.getClass() == String.class) {
                            authenticated = false;
                            // Notify the registered event listener, if there is one
                            if (registeredEventListener != null) {
                                try {
                                    registeredEventListener.send(Message.obtain(null, EVENTS_AUTH_FAILED_INVALID_USER));
                                } catch (RemoteException e) {
                                    throw new RuntimeException(e);
                                }
                            }

                            SharedData.nsWaitingForResponse = false;
                            Toast.makeText(NetworkerService.this, "Finished auth", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        // If authentication was successful...
                        if (result == Result.SUCCESS) {
                            authenticated = true;
                            // Notify the registered event listener, if there is one
                            if (registeredEventListener != null) {
                                try {
                                    registeredEventListener.send(Message.obtain(null, EVENTS_AUTH_SUCCESS));
                                } catch (RemoteException e) {
                                    throw new RuntimeException(e);
                                }
                            }

                            // Update the data in the auth file
                            try {
                                ObjectOutputStream oos = new ObjectOutputStream(Files.newOutputStream(new File(getFilesDir(), "UserData.bin").toPath()));
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
                            SendRequest(NetworkerService.this.messenger, new ListenRuleRequest(NetworkerService::friendRequestLrCallback, (Result r1, Object r2) -> {}, friendRequestRuleRequest));

                            JSONObject chatRequestRuleRequest = new JSONObject();
                            chatRequestRuleRequest.put("type", RequestType.AddListenRule);
                            chatRequestRuleRequest.put("data", new ListenRule(RequestType.SendChatInvite, "RecipientID", user.UserID));
                            SendRequest(NetworkerService.this.messenger, new ListenRuleRequest(NetworkerService::chatInvitationLrCallback, (Result r1, Object r2) -> {}, chatRequestRuleRequest));

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

                                        SendRequest(NetworkerService.this.messenger, new ListenRuleRequest(NetworkerService::messageLrCallback, (Result r1, Object r2) -> {}, msgRule, bundle));
                                    }

                                } catch (IOException | ClassNotFoundException e) {
                                    e.printStackTrace();
                                }

                            }
                        }

                        SharedData.nsWaitingForResponse = false;
                    }
                }, requestData));

            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace();
            }
        }
    }

    //
    // Notification callbacks
    //
    public static void friendRequestLrCallback(Object response, Bundle bundle) {
        // Get the friend request from the response object
        FriendRequest friendRequest = (FriendRequest) ((JSONObject) response).get("data");

        // Request the user that sent the request from the server
        JSONObject senderRequest = new JSONObject();
        senderRequest.put("type", RequestType.GetUser);
        senderRequest.put("data", new User(friendRequest.SenderID, null, null, null, null, null));
        NetworkerService.SendRequest(SharedData.nsMessenger, new NetworkerService.Request(NetworkerService::friendRequestLrCallback_getUser, senderRequest));
    }

    private static void friendRequestLrCallback_getUser(Result result, Object response) {
        if (result == Result.FAILED) {
            return;
        }

        // Cast the response into a user object
        User sender = (User) response;
        // Show the notification
        try {
            android.os.Message msg = android.os.Message.obtain(null, NetworkerService.CLIENT_SEND_NOTIFICATION);
            msg.obj = new Bundle();
            ((Bundle) msg.obj).putString("title", "New friend request");
            ((Bundle) msg.obj).putString("content", sender.DisplayName + " wants to be friends!");

            SharedData.nsMessenger.send(msg);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    public static void chatInvitationLrCallback(Object response, Bundle bundle) {
        // Get the chat invite from the request that triggered the listen rule
        ChatInvite chatInvite = (ChatInvite) ((JSONObject) response).get("data");

        // Create a new request to get the chat that the user has been invited to
        JSONObject getChatRequest = new JSONObject();
        getChatRequest.put("type", RequestType.GetChat);
        getChatRequest.put("data", new Chat(chatInvite.ChatID, null, null, -1));

        NetworkerService.SendRequest(SharedData.nsMessenger, new NetworkerService.Request(NetworkerService::chatInvitationLrCallback_getChat, getChatRequest));
    }

    private static void chatInvitationLrCallback_getChat(Result result, Object response) {
        if (result == Result.FAILED) {
            return;
        }

        // Cast the response to a Chat object
        Chat chat = (Chat) response;
        // Show the notification
        try {
            android.os.Message msg = android.os.Message.obtain(null, NetworkerService.CLIENT_SEND_NOTIFICATION);
            msg.obj = new Bundle();
            ((Bundle) msg.obj).putString("title", "New chat invitation");
            ((Bundle) msg.obj).putString("content", "You have been invited to " + chat.Name);

            SharedData.nsMessenger.send(msg);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    public static void messageLrCallback(Object response, Bundle bundle) {
        // Create a notification
        try {
            if (((Chat) bundle.getSerializable("chat")).ChatID != SharedData.activeChatID) {
                android.os.Message msg = android.os.Message.obtain(null, NetworkerService.CLIENT_SEND_NOTIFICATION);
                msg.obj = new Bundle();
                ((Bundle) msg.obj).putString("title", "New message");
                ((Bundle) msg.obj).putString("content", "You have a new message in " + ((Chat) bundle.getSerializable("chat")).Name);

                SharedData.nsMessenger.send(msg);
            }
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }
}

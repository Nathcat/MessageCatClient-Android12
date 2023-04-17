package com.nathcat.messagecat_client;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;

import com.nathcat.RSA.*;
import com.nathcat.messagecat_database.Result;
import com.nathcat.messagecat_server.ListenRule;
import com.nathcat.messagecat_server.RequestType;

import org.json.simple.JSONObject;

/**
 * Manages a connection to the server
 */
public class ConnectionHandler extends Handler {
    public Socket s = null;                // The socket used to connect to the server
    public ObjectOutputStream oos = null;  // Object output stream
    public ObjectInputStream ois = null;   // Object input stream
    public KeyPair keyPair = null;         // The client's key pair
    public KeyPair serverKeyPair = null;   // The server's key pair
    public int connectionHandlerId;        // The identifier of the connection handler this handler has connected to
    public ListenRuleCallbackHandler callbackHandler;
    public NetworkerService ns;
    public static class ListenRuleRecord {
        public final ListenRule listenRule;
        public final NetworkerService.IListenRuleCallback callback;
        public final Bundle bundle;

        private ListenRuleRecord(ListenRule listenRule, NetworkerService.IListenRuleCallback callback, Bundle bundle) {
            this.listenRule = listenRule;
            this.callback = callback;
            this.bundle = bundle;
        }
    }

    public ArrayList<ListenRuleRecord> listenRules = new ArrayList<>();

    public ConnectionHandler(Looper looper, NetworkerService ns) {
        super(looper);
        this.ns = ns;
    }

    /**
     * Send an object to the server
     * @param obj The object to send
     * @throws IOException Thrown in case of I/O issues
     */
    private void Send(Object obj) throws IOException {
        oos.writeObject(obj);
        oos.flush();
    }

    /**
     * Receive an object from the server
     * @return The object received
     * @throws IOException Thrown by I/O issues
     * @throws ClassNotFoundException Thrown if the required class cannot be found
     */
    private Object Receive() throws IOException, ClassNotFoundException {
        return ois.readObject();
    }

    /**
     * Start a connection to the server
     * @return Result code describing whether or not the connection was successful
     */
    public Result StartConnection() {
        try {
            // Try to connect to the server
            this.s = new Socket(NetworkerService.hostName, 1234);
            this.s.setSoTimeout(20000);
            this.oos = new ObjectOutputStream(s.getOutputStream());
            this.ois = new ObjectInputStream(s.getInputStream());

            // Create a key pair and perform the handshake
            this.keyPair = RSA.GenerateRSAKeyPair();
            this.serverKeyPair = (KeyPair) this.Receive();

            this.Send(new KeyPair(this.keyPair.pub, null));

            this.connectionHandlerId = (int) this.keyPair.decrypt((EncryptedObject) this.Receive());
            System.out.println("Got handler id: " + connectionHandlerId);

            int port = (int) this.keyPair.decrypt((EncryptedObject) this.Receive());
            System.out.println("Got port: " + port);

            callbackHandler = new ListenRuleCallbackHandler(this, keyPair, serverKeyPair, port);
            callbackHandler.setDaemon(true);
            callbackHandler.start();

            assert this.s != null && this.oos != null && this.ois != null && this.keyPair != null && this.serverKeyPair != null;

        } catch (IOException | NoSuchAlgorithmException | ClassNotFoundException | PrivateKeyException | AssertionError e) {
            e.printStackTrace();
            return Result.FAILED;
        }

        return Result.SUCCESS;
    }

    /**
     * Close the connection to the server
     * @return Result code describing whether or not the closure was successful
     */
    public Result CloseConnection() {
        try {
            this.oos.close();
            this.ois.close();
            this.s.close();
        } catch (IOException e) {
            e.printStackTrace();
            return Result.FAILED;
        }

        return Result.SUCCESS;
    }

    /**
     * Handle a request
     * @param request The request to handle, this may be any type of request (listen rule or regular)
     */
    public void HandleRequest(NetworkerService.Request request) {
        SharedData.nsWaitingForResponse = true;

        if (((JSONObject) request.request).get("type") == RequestType.AddListenRule) {
            NetworkerService.ListenRuleRequest lrRequest = (NetworkerService.ListenRuleRequest) request;

            try {
                ListenRule rule = (ListenRule) ((JSONObject) lrRequest.request).get("data");
                ((JSONObject) lrRequest.request).put("data", rule);

                // Send the request
                this.Send(this.serverKeyPair.encrypt(lrRequest.request));
                // Receive the ID
                int id = (int) this.keyPair.decrypt((EncryptedObject) this.Receive());
                rule = (ListenRule) ((JSONObject) lrRequest.request).get("data");
                rule.setId(id);
                // Add the listen rule to the array along with it's callback
                this.listenRules.add(new ListenRuleRecord(rule, lrRequest.lrCallback, lrRequest.bundle));
                lrRequest.callback.callback(Result.SUCCESS, id);

            } catch (IOException | PublicKeyException | ClassNotFoundException | PrivateKeyException | ListenRule.IDAlreadySetException e) {
                e.printStackTrace();
                lrRequest.callback.callback(Result.FAILED, null);
            }
        }
        else if (((JSONObject) request.request).get("type") == RequestType.RemoveListenRule) {
            NetworkerService.ListenRuleRequest lrRequest = (NetworkerService.ListenRuleRequest) request;

            try {
                // Remove the listen rule from the array
                for (int i = 0; i < this.listenRules.size(); i++) {
                    if (this.listenRules.get(i).listenRule.getId() == (int) ((JSONObject) lrRequest.request).get("data")) {
                        this.listenRules.remove(i);
                        break;
                    }
                }
                // Send the request
                this.Send(this.serverKeyPair.encrypt(lrRequest.request));
                lrRequest.callback.callback(Result.SUCCESS, this.keyPair.decrypt((EncryptedObject) this.Receive()));

            } catch (IOException | PublicKeyException | ClassNotFoundException | PrivateKeyException e) {
                e.printStackTrace();
                lrRequest.callback.callback(Result.FAILED, null);
            }
        } else {

            try {
                // Send the request
                this.Send(this.serverKeyPair.encrypt(request.request));
                // Receive the response and perform the callback from the request object
                request.callback.callback(Result.SUCCESS, this.keyPair.decrypt((EncryptedObject) this.Receive()));

            } catch (IOException | PublicKeyException | ClassNotFoundException | PrivateKeyException e) {
                e.printStackTrace();
                // Perform callback with failure code
                request.callback.callback(Result.FAILED, null);
            }
        }

        SharedData.nsWaitingForResponse = false;
    }

    @Override
    public void handleMessage(Message msg) {
        switch (msg.what) {
            case 0: StartConnection(); break;

            case 1: HandleRequest((NetworkerService.Request) ((Bundle) msg.obj).getSerializable("request")); break;

            case 2: CloseConnection(); break;
        }
    }
}

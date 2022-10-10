package com.nathcat.messagecat_client;

import android.content.Context;
import android.os.Looper;
import android.os.Message;

import com.nathcat.RSA.EncryptedObject;
import com.nathcat.RSA.KeyPair;
import com.nathcat.RSA.ObjectContainer;
import com.nathcat.RSA.PrivateKeyException;
import com.nathcat.RSA.PublicKeyException;
import com.nathcat.RSA.RSA;
import com.nathcat.messagecat_database.Result;
import com.nathcat.messagecat_server.ListenRule;
import com.nathcat.messagecat_server.RequestType;

import org.json.simple.JSONObject;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;

/**
 * Handles listen rules made by this client on the server.
 *
 * @author Nathan "Nathcat" Baines
 */
public class ListenRuleHandler extends ConnectionHandler {

    /**
     * Waits for listen rule triggers and performs the relevant callbacks when those triggers occur.
     */
    private class CallbackHandler extends Thread {
        @Override
        public void run() {
            while (true) {
                try {
                    // Receive the trigger request
                    JSONObject triggerRequest = (JSONObject) ((ObjectContainer) ListenRuleHandler.this.keyPair.decryptBigObject((EncryptedObject[]) ListenRuleHandler.this.Receive())).obj;
                    // Check all of the listen rules in the array list for a rule with a matching id
                    for (int i = 0; i < ListenRuleHandler.this.listenRules.size(); i++) {
                        // If the id matches, perform the callback
                        if (ListenRuleHandler.this.listenRules.get(i).listenRule.getId() == (int) triggerRequest.get("triggerID")) {
                            ListenRuleHandler.this.listenRules.get(i).callback.callback(triggerRequest);
                        }
                    }
                } catch (PrivateKeyException | IOException | ClassNotFoundException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private class ListenRuleRecord {
        public final ListenRule listenRule;
        public final NetworkerService.IListenRuleCallback callback;

        private ListenRuleRecord(ListenRule listenRule, NetworkerService.IListenRuleCallback callback) {
            this.listenRule = listenRule;
            this.callback = callback;
        }
    }

    private ArrayList<ListenRuleRecord> listenRules = new ArrayList<>();
    private CallbackHandler callbackHandler;

    public ListenRuleHandler(Context context, Looper looper) {
        super(context, looper);
    }

    @Override
    public void handleMessage(Message msg) {
        // If the 'what' parameter of the message is 0, that indicates an initialisation request
        // This should only occur when the service is first started
        if (msg.what == 0) {
            try {
                // Try to connect to the server
                this.s = new Socket("192.168.181.226", 1234);
                this.s.setSoTimeout(20000);
                this.oos = new ObjectOutputStream(s.getOutputStream());
                this.ois = new ObjectInputStream(s.getInputStream());

                // Create a key pair and perform the handshake
                this.keyPair = RSA.GenerateRSAKeyPair();
                this.serverKeyPair = (KeyPair) this.Receive();

                this.Send(new KeyPair(this.keyPair.pub, null));

                this.connectionHandlerId = (int) this.serverKeyPair.decryptBigObject((EncryptedObject[]) this.Receive());


                // TODO This should be uncommented later, this is for experimental purposes.
                //this.callbackHandler = new CallbackHandler();
                //this.callbackHandler.setDaemon(true);
                //this.callbackHandler.start();

            } catch (IOException | NoSuchAlgorithmException | ClassNotFoundException | PrivateKeyException e) {
                e.printStackTrace();
            }

            assert this.s != null && this.oos != null && this.ois != null && this.keyPair != null && this.serverKeyPair != null;
            return;
        }
        else if (msg.what == 2) {
            try {
                this.callbackHandler.interrupt();
                this.oos.close();
                this.ois.close();
                this.s.close();
            } catch (IOException e) {
                e.printStackTrace();
            }

            this.getLooper().quit();
            return;
        }

        // This point will only be reached if the 'what' parameter of the message is not 0
        // Get the request object from the message
        NetworkerService.ListenRuleRequest request = (NetworkerService.ListenRuleRequest) msg.obj;

        if (((JSONObject) request.request).get("type") == RequestType.Authenticate) {
            try {
                // Send the request
                this.Send(this.serverKeyPair.encryptBigObject(request.request));
                // Receive the response and perform the callback from the request object
                request.callback.callback(((ObjectContainer) this.keyPair.decryptBigObject((EncryptedObject[]) this.Receive())).obj);

            } catch (IOException | PublicKeyException | ClassNotFoundException | PrivateKeyException e) {
                e.printStackTrace();
                // Perform callback with failure code
                request.callback.callback(null);
            }
        }

        if (((JSONObject) request.request).get("type") == RequestType.AddListenRule) {
            try {
                // Send the request
                this.Send(this.serverKeyPair.encryptBigObject(request.request));
                // Receive the ID
                int id = (int) ((ObjectContainer) this.keyPair.decryptBigObject((EncryptedObject[]) this.Receive())).obj;
                ListenRule rule = (ListenRule) ((ObjectContainer) ((JSONObject) request.request).get("data")).obj;
                rule.setId(id);
                // Add the listen rule to the array along with it's callback
                this.listenRules.add(new ListenRuleRecord(rule, request.callback));
                request.requestCallback.callback(Result.SUCCESS, id);

            } catch (IOException | PublicKeyException | ClassNotFoundException | PrivateKeyException | ListenRule.IDAlreadySetException e) {
                e.printStackTrace();
                request.requestCallback.callback(Result.FAILED, null);
            }
        }
        else if (((JSONObject) request.request).get("type") == RequestType.RemoveListenRule) {
            try {
                // Remove the listen rule from the array
                for (int i = 0; i < this.listenRules.size(); i++) {
                    if (this.listenRules.get(i).listenRule.getId() == (int) ((ObjectContainer) ((JSONObject) request.request).get("data")).obj) {
                        this.listenRules.remove(i);
                        break;
                    }
                }
                // Send the request
                this.Send(this.serverKeyPair.encryptBigObject(request.request));
                request.requestCallback.callback(Result.SUCCESS, null);

            } catch (IOException | PublicKeyException e) {
                e.printStackTrace();
                request.requestCallback.callback(Result.FAILED, null);
            }
        }
    }
}

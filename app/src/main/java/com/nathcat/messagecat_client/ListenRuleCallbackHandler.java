package com.nathcat.messagecat_client;

import com.nathcat.RSA.EncryptedObject;
import com.nathcat.RSA.KeyPair;
;
import com.nathcat.RSA.PrivateKeyException;
import com.nathcat.RSA.RSA;

import org.json.simple.JSONObject;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.NoSuchAlgorithmException;

/**
 * Handles callbacks from listen rules added to the server
 *
 * @author Nathan "Nathcat" Baines
 */
public class ListenRuleCallbackHandler extends Thread {
    private ConnectionHandler connectionHandler;  // Instance of the connection handler that created this process
    public Socket s = null;                       // The socket used to connect to the server
    public ObjectOutputStream oos = null;         // Object output stream
    public ObjectInputStream ois = null;          // Object input stream
    public KeyPair keyPair = null;                // The client's key pair
    public KeyPair serverKeyPair = null;          // The server's key pair
    public int port;

    public ListenRuleCallbackHandler(ConnectionHandler connectionHandler, KeyPair keyPair, KeyPair serverKeyPair, int port) {
        this.connectionHandler = connectionHandler;
        this.keyPair = keyPair;
        this.serverKeyPair = serverKeyPair;
        this.port = port;
    }

    /**
     * Send an object to the server
     * @param obj The object to send
     * @throws IOException Thrown in case of I/O issues
     */
    public void Send(Object obj) throws IOException {
        oos.writeObject(obj);
        oos.flush();
    }

    /**
     * Receive an object from the server
     * @return The object received
     * @throws IOException Thrown by I/O issues
     * @throws ClassNotFoundException Thrown if the required class cannot be found
     */
    public Object Receive() throws IOException, ClassNotFoundException {
        return ois.readObject();
    }

    @Override
    public void run() {
        try {
            this.s = new Socket("13.40.226.47", port);
            this.oos = new ObjectOutputStream(s.getOutputStream());
            this.ois = new ObjectInputStream(s.getInputStream());

        } catch (IOException e) {
            e.printStackTrace();
        }

        assert this.s != null && this.oos != null && this.ois != null && this.keyPair != null && this.serverKeyPair != null;

        while (true) {
            try {
                // Receive the trigger request
                JSONObject triggerRequest = (JSONObject) this.keyPair.decrypt((EncryptedObject) this.Receive());

                // Check all of the listen rules in the array list for a rule with a matching id
                for (int i = 0; i < this.connectionHandler.listenRules.size(); i++) {
                    // If the id matches, perform the callback
                    if (this.connectionHandler.listenRules.get(i).listenRule.getId() == (int) triggerRequest.get("triggerID")) {
                        if (this.connectionHandler.listenRules.get(i).bundle == null) {
                            this.connectionHandler.listenRules.get(i).callback.callback(triggerRequest);
                        }
                        else {
                            this.connectionHandler.listenRules.get(i).callback.callback(triggerRequest, this.connectionHandler.listenRules.get(i).bundle);
                        }
                    }
                }
            } catch (PrivateKeyException | IOException | ClassNotFoundException e) {
                e.printStackTrace();
            }
        }
    }
}

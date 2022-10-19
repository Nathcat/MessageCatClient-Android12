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
    public int connectionHandlerId = -1;

    public ListenRuleCallbackHandler(ConnectionHandler connectionHandler) {
        this.connectionHandler = connectionHandler;
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
            // Try to connect to the server
            this.s = new Socket("192.168.1.26", 1234);
            this.oos = new ObjectOutputStream(s.getOutputStream());
            this.ois = new ObjectInputStream(s.getInputStream());

            // Create a key pair and perform the handshake
            this.keyPair = RSA.GenerateRSAKeyPair();
            this.serverKeyPair = (KeyPair) this.Receive();

            this.Send(new KeyPair(this.keyPair.pub, null));

            this.connectionHandlerId = (int) this.keyPair.decrypt((EncryptedObject) this.Receive());

        } catch (IOException | NoSuchAlgorithmException | ClassNotFoundException | PrivateKeyException e) {
            e.printStackTrace();
        }

        assert this.s != null && this.oos != null && this.ois != null && this.keyPair != null && this.serverKeyPair != null;

        while (true) {
            try {
                // Receive the trigger request
                JSONObject triggerRequest = (JSONObject) this.keyPair.decrypt((EncryptedObject) this.Receive());
                System.out.println("Triggered listen rule with response: " + triggerRequest);
                // Check all of the listen rules in the array list for a rule with a matching id
                for (int i = 0; i < this.connectionHandler.listenRules.size(); i++) {
                    // If the id matches, perform the callback
                    if (this.connectionHandler.listenRules.get(i).listenRule.getId() == (int) triggerRequest.get("triggerID")) {
                        this.connectionHandler.listenRules.get(i).callback.callback(triggerRequest);
                    }
                }
            } catch (PrivateKeyException | IOException | ClassNotFoundException e) {
                e.printStackTrace();
            }
        }
    }
}

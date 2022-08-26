package com.nathcat.messagecat_client;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.security.NoSuchAlgorithmException;

import com.nathcat.RSA.*;
import com.nathcat.messagecat_database.Result;

/**
 * Manages a connection to the server
 */
public class ConnectionHandler extends Handler {
    public Socket s = null;                // The socket used to connect to the server
    public ObjectOutputStream oos = null;  // Object output stream
    public ObjectInputStream ois = null;   // Object input stream
    public KeyPair keyPair = null;         // The client's key pair
    public KeyPair serverKeyPair = null;   // The server's key pair
    public final Context context;          // The context this handler was created in

    public ConnectionHandler(Context context, Looper looper) {
        super(looper);  // Handler constructor

        this.context = context;
    }

    /**
     * Send an object to the server
     * @param obj The object to send
     * @throws IOException Thrown in case of I/O issues
     */
    private void Send(Object obj) throws IOException {
        this.oos.writeObject(obj);
        this.oos.flush();
    }

    /**
     * Receive an object from the server
     * @return The object received
     * @throws IOException Thrown by I/O issues
     * @throws ClassNotFoundException Thrown if the required class cannot be found
     */
    private Object Receive() throws IOException, ClassNotFoundException {
        return this.ois.readObject();
    }

    /**
     * Handles messages passed to the handler.
     *
     * @param msg The message passed to the handler
     */
    @Override
    public void handleMessage(Message msg) {
        // If the 'what' parameter of the message is 0, that indicates an initialisation request
        // This should only occur when the service is first started
        if (msg.what == 0) {
            try {
                // Try to connect to the server
                this.s = new Socket("192.168.1.26", 1234);
                this.oos = new ObjectOutputStream(s.getOutputStream());
                this.ois = new ObjectInputStream(s.getInputStream());

                // Create a key pair and perform the handshake
                this.keyPair = RSA.GenerateRSAKeyPair();
                this.serverKeyPair = (KeyPair) this.Receive();

                this.Send(new KeyPair(this.keyPair.pub, null));

            } catch (IOException | NoSuchAlgorithmException | ClassNotFoundException e) {
                e.printStackTrace();
            }

            assert this.s != null && this.oos != null && this.ois != null && this.keyPair != null && this.serverKeyPair != null;
            return;
        }

        // This point will only be reached if the 'what' parameter of the message is not 0
        // Get the request object from the message
        NetworkerService.Request request = (NetworkerService.Request) msg.obj;

        try {
            // Send the request
            this.Send(this.serverKeyPair.encryptBigObject(request.request));
            // Receive the response and perform the callback from the request object
            request.callback.callback(Result.SUCCESS, this.keyPair.decryptBigObject((EncryptedObject[]) this.Receive()));

        } catch (IOException | PublicKeyException | ClassNotFoundException | PrivateKeyException e) {
            e.printStackTrace();
            // Perform callback with failure code
            request.callback.callback(Result.FAILED, null);
        }
    }
}

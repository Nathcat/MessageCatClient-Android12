package com.nathcat.messagecat_client;

import android.graphics.Color;
import android.os.Bundle;

import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.nathcat.messagecat_database_entities.Message;

import java.util.Objects;

public class MessageFragment extends Fragment {

    public Message message;
    public boolean fromOtherUser;

    public MessageFragment() {
        super(R.layout.fragment_message);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        message = (Message) requireArguments().getSerializable("message");
        fromOtherUser = requireArguments().getBoolean("fromOtherUser");

        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_message, container, false);
    }

    @Override
    public void onStart() {
        super.onStart();

        // Change the view if the message is from another user
        if (fromOtherUser) {
            requireView().findViewById(R.id.messageContainer).setBackground(requireActivity().getDrawable(R.drawable.message_from_others_background));
            ((TextView) requireView().findViewById(R.id.MessageSender)).setTextColor(Color.BLACK);
            ((TextView) requireView().findViewById(R.id.MessageContent)).setTextColor(Color.BLACK);
        }

        // Display the information in the message to the text views
        ((TextView) requireView().findViewById(R.id.MessageSender)).setText(Objects.requireNonNull(((MainActivity) requireActivity()).users.get(message.SenderID)).DisplayName);
        ((TextView) requireView().findViewById(R.id.MessageContent)).setText((String) message.Content);
    }
}
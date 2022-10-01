package com.nathcat.messagecat_client;

import android.os.Bundle;

import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.nathcat.messagecat_database_entities.Chat;

public class ChatFragment extends Fragment {

    private String chatName;
    private String chatDesc;
    public Chat chat;

    public ChatFragment() {
        super(R.layout.fragment_chat);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Get the chat information to be displayed
        chatName = requireArguments().getString("chatName");
        chatDesc = requireArguments().getString("chatDesc");
        chat = (Chat) requireArguments().getSerializable("chat");

        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_chat, container, false);
    }

    @Override
    public void onStart() {
        super.onStart();

        try {
            ((MainActivity) requireActivity()).chatFragments.add(this);
        } catch (ClassCastException ignored) {}

        // Set the text views in this view to the chat information passed to this fragment
        ((TextView) requireView().findViewById(R.id.chatName)).setText(chatName);
        ((TextView) requireView().findViewById(R.id.chatDesc)).setText(chatDesc);
    }
}
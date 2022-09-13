package com.nathcat.messagecat_client;

import android.os.Bundle;

import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

public class CreateNewChatFragment extends Fragment {

    public CreateNewChatFragment() {
        super(R.layout.fragment_create_new_chat);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_create_new_chat, container, false);
    }
}
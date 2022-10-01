package com.nathcat.messagecat_client;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentContainerView;
import androidx.fragment.app.FragmentTransaction;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import com.nathcat.messagecat_database_entities.Chat;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.Random;

public class ChatsFragment extends Fragment {

    public ChatsFragment() {
        super(R.layout.fragment_chats);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_chats, container, false);
    }

    @Override
    public void onStart() {
        super.onStart();

        // Set the title on the action bar
        // Otherwise it will be "fragment_chats"
        ((AppCompatActivity) requireActivity()).getSupportActionBar().setTitle("Chats");

        Chat[] chats;

        try {
            ((MainActivity) requireActivity()).chatFragments.clear();

        } catch (ClassCastException ignored) {}

        // Get the array of chats from local storage
        try {
            ObjectInputStream ois = new ObjectInputStream(new FileInputStream(new File(requireActivity().getFilesDir(), "Chats.bin")));
            chats = (Chat[]) ois.readObject();
            ois.close();

        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
            return;
        }

        // If there is chats to display, hide the no chats message
        if (chats.length != 0) {
            requireView().findViewById(R.id.noChatsMessage).setVisibility(View.GONE);
        }

        // Get the linear layout widget
        LinearLayout chatsContainer = requireView().findViewById(R.id.ChatsContainer);

        for (Chat chat : chats) {
            // Get a random id for the new fragment container
            int containerId = new Random().nextInt();

            // Create a new fragment container
            FragmentContainerView fragmentContainer = new FragmentContainerView(requireContext());
            fragmentContainer.setId(containerId);

            // Create a new chat fragment inside the fragment container
            Bundle bundle = new Bundle();
            bundle.putString("chatName", chat.Name);
            bundle.putString("chatDesc", chat.Description);
            bundle.putSerializable("chat", chat);

            requireActivity().getSupportFragmentManager().beginTransaction()
                    .setReorderingAllowed(true)
                    .add(containerId, ChatFragment.class, bundle)
                    .commit();

            // Add the new fragment container to the linear layout widget
            chatsContainer.addView(fragmentContainer);
        }
    }
}
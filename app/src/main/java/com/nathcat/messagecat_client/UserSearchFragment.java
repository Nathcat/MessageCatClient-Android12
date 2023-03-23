package com.nathcat.messagecat_client;

import android.os.Bundle;

import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.nathcat.messagecat_database_entities.User;

public class UserSearchFragment extends Fragment {

    private User user;

    public UserSearchFragment() {
        super(R.layout.fragment_user_search);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        user = (User) requireArguments().getSerializable("user");

        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_user_search, container, false);
    }

    @Override
    public void onStart() {
        super.onStart();

        // Set the display name label to the user's display name
        ((TextView) requireView().findViewById(R.id.userSearchDisplayName)).setText(user.DisplayName);
    }
}
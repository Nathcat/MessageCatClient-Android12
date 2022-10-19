package com.nathcat.messagecat_client;

import android.os.Bundle;

import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

public class InvitationFragment extends Fragment {

    public Object invite;
    private String text;

    public InvitationFragment() {
        super(R.layout.fragment_invitation);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        invite = requireArguments().getSerializable("invite");
        text = requireArguments().getString("text");

        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_invitation, container, false);
    }

    @Override
    public void onStart() {
        super.onStart();

        ((TextView) requireView().findViewById(R.id.invitationText)).setText(text);

        ((MainActivity) requireActivity()).invitationFragments.add(this);
    }
}
package com.nathcat.messagecat_client;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentContainerView;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.nathcat.RSA.ObjectContainer;
import com.nathcat.messagecat_database.Result;
import com.nathcat.messagecat_database_entities.Friendship;
import com.nathcat.messagecat_database_entities.User;
import com.nathcat.messagecat_server.RequestType;

import org.json.simple.JSONObject;

import java.util.Random;

public class FriendsFragment extends Fragment {

    public FriendsFragment() {
        super(R.layout.fragment_friends);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_friends, container, false);
    }

    @Override
    public void onStart() {
        super.onStart();

        // Set the title on the action bar
        ((AppCompatActivity) requireActivity()).getSupportActionBar().setTitle("Friends");

        LinearLayout container = requireView().findViewById(R.id.FriendsContainer);

        NetworkerService networkerService = ((MainActivity) requireActivity()).networkerService;

        // Create a request to get friendships from the server
        JSONObject request = new JSONObject();
        request.put("type", RequestType.GetFriendship);
        request.put("selector", "userID");
        request.put("data", new ObjectContainer(new Friendship(-1, networkerService.user.UserID, -1, null)));

        networkerService.SendRequest(new NetworkerService.Request(new NetworkerService.IRequestCallback() {
            @Override
            public void callback(Result result, Object response) {
                if (result == Result.FAILED) {
                    requireActivity().runOnUiThread(() -> Toast.makeText(requireActivity(), "Something went wrong :(", Toast.LENGTH_SHORT));
                    return;
                }

                Friendship[] friendships = (Friendship[]) response;
                ((MainActivity) requireActivity()).friends = new User[friendships.length];

                if (friendships.length != 0) {
                    requireActivity().runOnUiThread(container::removeAllViews);
                }

                for (int i = 0; i < friendships.length; i++) {
                    JSONObject friendRequest = new JSONObject();
                    friendRequest.put("type", RequestType.GetUser);
                    friendRequest.put("selector", "id");
                    friendRequest.put("data", new ObjectContainer(new User(friendships[i].FriendID, null, null, null, null, null)));

                    networkerService.SendRequest(new NetworkerService.Request(new NetworkerService.IRequestCallback() {
                        @Override
                        public void callback(Result result, Object response) {
                            if (result == Result.FAILED) {
                                requireActivity().runOnUiThread(() -> Toast.makeText(requireContext(), "Something went wrong :(", Toast.LENGTH_SHORT));
                                return;
                            }

                            // Add the friend to the friends array
                            for (int x = 0; x < ((MainActivity) requireActivity()).friends.length; x++) {
                                if (((MainActivity) requireActivity()).friends[x] == null) {
                                    ((MainActivity) requireActivity()).friends[x] = (User) response;
                                    break;
                                }
                            }

                            Bundle bundle = new Bundle();
                            bundle.putSerializable("user", (User) response);

                            FriendsFragment.this.getChildFragmentManager().beginTransaction()
                                    .setReorderingAllowed(true)
                                    .add(R.id.FriendsContainer, FriendFragment.class, bundle)
                                    .commit();

                            networkerService.waitingForResponse = false;
                        }
                    }, friendRequest));
                }

                networkerService.waitingForResponse = false;
            }
        }, request));
    }
}
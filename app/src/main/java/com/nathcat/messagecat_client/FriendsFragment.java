package com.nathcat.messagecat_client;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import android.os.Messenger;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.nathcat.messagecat_database.Result;
import com.nathcat.messagecat_database_entities.Friendship;
import com.nathcat.messagecat_database_entities.User;
import com.nathcat.messagecat_server.RequestType;

import org.json.simple.JSONObject;

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

        // Create a request to get friendships from the server
        JSONObject request = new JSONObject();
        request.put("type", RequestType.GetFriendship);
        request.put("selector", "userID");
        request.put("data", new Friendship(-1, SharedData.user.UserID, -1, null));
        
        SharedData.misc.put("fragment", this);
        SharedData.misc.put("container", container);

        NetworkerService.SendRequest(SharedData.nsMessenger, new NetworkerService.Request(FriendsFragment::getFriendshipCallback, request));
    }

    //
    // Callbacks
    //

    private static void getFriendshipCallback(Result result, Object response) {
        if (result == Result.FAILED) {
            ((FriendsFragment) SharedData.misc.get("fragment")).requireActivity().runOnUiThread(() -> Toast.makeText(((FriendsFragment) SharedData.misc.get("fragment")).requireActivity(), "Something went wrong :(", Toast.LENGTH_SHORT));
            return;
        }

        Friendship[] friendships = (Friendship[]) response;
        ((MainActivity) ((FriendsFragment) SharedData.misc.get("fragment")).requireActivity()).friends = new User[friendships.length];

        if (friendships.length != 0) {
            ((FriendsFragment) SharedData.misc.get("fragment")).requireActivity().runOnUiThread(((LinearLayout) SharedData.misc.get("container"))::removeAllViews);
        }

        for (Friendship friendship : friendships) {
            JSONObject friendRequest = new JSONObject();
            friendRequest.put("type", RequestType.GetUser);
            friendRequest.put("selector", "id");
            friendRequest.put("data", new User(friendship.FriendID, null, null, null, null, null));

            NetworkerService.SendRequest(SharedData.nsMessenger, new NetworkerService.Request(FriendsFragment::getFriendshipCallback_addFriendToArray, friendRequest));
        }
    }
    
    private static void getFriendshipCallback_addFriendToArray(Result result, Object response) {
        if (result == Result.FAILED) {
            ((FriendsFragment) SharedData.misc.get("fragment")).requireActivity().runOnUiThread(() -> Toast.makeText(((FriendsFragment) SharedData.misc.get("fragment")).requireContext(), "Something went wrong :(", Toast.LENGTH_SHORT));
            return;
        }

        // Add the friend to the friends array
        for (int x = 0; x < ((MainActivity) ((FriendsFragment) SharedData.misc.get("fragment")).requireActivity()).friends.length; x++) {
            if (((MainActivity) ((FriendsFragment) SharedData.misc.get("fragment")).requireActivity()).friends[x] == null) {
                ((MainActivity) ((FriendsFragment) SharedData.misc.get("fragment")).requireActivity()).friends[x] = (User) response;
                break;
            }
        }

        Bundle bundle = new Bundle();
        bundle.putSerializable("user", (User) response);

        ((FriendsFragment) SharedData.misc.get("fragment")).getChildFragmentManager().beginTransaction()
                .setReorderingAllowed(true)
                .add(R.id.FriendsContainer, FriendFragment.class, bundle)
                .commit();
    }
}
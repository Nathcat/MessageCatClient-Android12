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
import com.nathcat.messagecat_database_entities.ChatInvite;
import com.nathcat.messagecat_database_entities.FriendRequest;
import com.nathcat.messagecat_database_entities.User;
import com.nathcat.messagecat_server.RequestType;

import org.json.simple.JSONObject;

public class InvitationsFragment extends Fragment {

    private ChatInvite[] chatInvites;
    private FriendRequest[] friendRequests;

    public InvitationsFragment() {
        super(R.layout.fragment_invitations);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {



        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_invitations, container, false);
    }

    @Override
    public void onStart() {
        super.onStart();

        ((MainActivity) requireActivity()).invitationFragments.clear();
        ((MainActivity) requireActivity()).invitationsFragment = this;

        // Set the title on the action bar
        ((AppCompatActivity) requireActivity()).getSupportActionBar().setTitle("Invitations");

        // Load the invites
        reloadInvites();
    }

    /**
     * Load the invites from the server and display them onto the page
     */
    public void reloadInvites() {
        // Destroy all the currently displayed invites
        ((LinearLayout) requireView().findViewById(R.id.InvitationsContainer)).removeAllViews();
        // Remove all the invites from the main activity array
        ((MainActivity) requireActivity()).invitationFragments.clear();

        // Request incoming friend requests from the server
        JSONObject request = new JSONObject();
        request.put("type", RequestType.GetFriendRequests);
        request.put("data", new FriendRequest(-1, -1, SharedData.user.UserID, -1));
        request.put("selector", "recipientID");

        SharedData.misc.put("fragment", this);

        NetworkerService.SendRequest(SharedData.nsMessenger, new NetworkerService.Request(InvitationsFragment::getFriendRequestsCallback, request));

        // Request incoming chat requests from the server
        JSONObject chatInviteRequest = new JSONObject();
        chatInviteRequest.put("type", RequestType.GetChatInvite);
        chatInviteRequest.put("data", new ChatInvite(-1, -1, -1, SharedData.user.UserID, -1, -1));
        chatInviteRequest.put("selector", "recipientID");

        NetworkerService.SendRequest(SharedData.nsMessenger, new NetworkerService.Request(InvitationsFragment::getChatInvitesCallback, chatInviteRequest));
    }

    //
    // Callbacks
    //

    private static void getFriendRequestsCallback(Result result, Object response) {
        if (result == Result.FAILED) {
            ((InvitationsFragment) SharedData.misc.get("fragment")).requireActivity().runOnUiThread(() -> Toast.makeText(((InvitationsFragment) SharedData.misc.get("fragment")).requireContext(), "Something went wrong :(", Toast.LENGTH_SHORT).show());
            System.exit(1);
        }

        ((InvitationsFragment) SharedData.misc.get("fragment")).friendRequests = (FriendRequest[]) response;
        if (((InvitationsFragment) SharedData.misc.get("fragment")).friendRequests.length != 0) {
            ((InvitationsFragment) SharedData.misc.get("fragment")).requireActivity().runOnUiThread(() -> { try { ((InvitationsFragment) SharedData.misc.get("fragment")).requireView().findViewById(R.id.noInvitesMessage).setVisibility(View.GONE); } catch (NullPointerException ignored) {} });
        }

        for (FriendRequest fr : ((InvitationsFragment) SharedData.misc.get("fragment")).friendRequests) {
            JSONObject userRequest = new JSONObject();
            userRequest.put("type", RequestType.GetUser);
            userRequest.put("selector", "id");
            userRequest.put("data", new User(fr.SenderID, "", "", "", "", ""));

            NetworkerService.SendRequest(SharedData.nsMessenger, new NetworkerService.Request((Result r1, Object r2) -> {
                if (r1 == Result.FAILED) {
                    ((InvitationsFragment) SharedData.misc.get("fragment")).requireActivity().runOnUiThread(() -> Toast.makeText(((InvitationsFragment) SharedData.misc.get("fragment")).requireContext(), "Something went wrong :(", Toast.LENGTH_SHORT).show());
                    System.exit(1);
                }

                User user = (User) r2;
                Bundle bundle = new Bundle();
                bundle.putSerializable("invite", fr);
                bundle.putString("text", user.DisplayName + " wants to be friends!");

                ((InvitationsFragment) SharedData.misc.get("fragment")).getChildFragmentManager().beginTransaction()
                        .setReorderingAllowed(true)
                        .add(R.id.InvitationsContainer, InvitationFragment.class, bundle)
                        .commit();
            }, userRequest));
        }
    }

    private static void getChatInvitesCallback(Result result, Object response) {
        if (result == Result.FAILED) {
            ((InvitationsFragment) SharedData.misc.get("fragment")).requireActivity().runOnUiThread(() -> Toast.makeText(((InvitationsFragment) SharedData.misc.get("fragment")).requireContext(), "Something went wrong :(", Toast.LENGTH_SHORT).show());
            System.exit(1);
        }

        ((InvitationsFragment) SharedData.misc.get("fragment")).chatInvites = (ChatInvite[]) response;

        if (((InvitationsFragment) SharedData.misc.get("fragment")).chatInvites.length != 0) {
            ((InvitationsFragment) SharedData.misc.get("fragment")).requireActivity().runOnUiThread(() -> { try { ((InvitationsFragment) SharedData.misc.get("fragment")).requireView().findViewById(R.id.noInvitesMessage).setVisibility(View.GONE); } catch (NullPointerException ignored) {} });
        }

        for (ChatInvite inv : ((InvitationsFragment) SharedData.misc.get("fragment")).chatInvites) {
            JSONObject userRequest = new JSONObject();
            userRequest.put("type", RequestType.GetUser);
            userRequest.put("selector", "id");
            userRequest.put("data", new User(inv.SenderID, "", "", "", "", ""));

            NetworkerService.SendRequest(SharedData.nsMessenger, new NetworkerService.Request((Result r1, Object r2) -> {
                if (r1 == Result.FAILED) {
                    ((InvitationsFragment) SharedData.misc.get("fragment")).requireActivity().runOnUiThread(() -> Toast.makeText(((InvitationsFragment) SharedData.misc.get("fragment")).requireContext(), "Something went wrong :(", Toast.LENGTH_SHORT).show());
                    System.exit(1);
                }

                User user = (User) r2;
                Bundle bundle = new Bundle();
                bundle.putSerializable("invite", inv);
                bundle.putString("text", user.DisplayName + " wants to chat!");

                ((InvitationsFragment) SharedData.misc.get("fragment")).getChildFragmentManager().beginTransaction()
                        .setReorderingAllowed(true)
                        .add(R.id.InvitationsContainer, InvitationFragment.class, bundle)
                        .commit();
            }, userRequest));
        }
    }
}
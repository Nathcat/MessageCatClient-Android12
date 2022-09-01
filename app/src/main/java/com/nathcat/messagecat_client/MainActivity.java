package com.nathcat.messagecat_client;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.view.MenuItem;
import android.view.View;
import android.view.Menu;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.navigation.NavigationView;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentContainerView;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.appcompat.app.AppCompatActivity;

import com.nathcat.RSA.ObjectContainer;
import com.nathcat.messagecat_client.databinding.ActivityMainBinding;
import com.nathcat.messagecat_database.Result;
import com.nathcat.messagecat_database_entities.FriendRequest;
import com.nathcat.messagecat_database_entities.Friendship;
import com.nathcat.messagecat_database_entities.User;
import com.nathcat.messagecat_server.RequestType;

import org.json.simple.JSONObject;

import java.util.Arrays;
import java.util.Date;
import java.util.Random;

public class MainActivity extends AppCompatActivity {

    private ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            // Get the networker service instance
            networkerService = ((NetworkerService.NetworkerServiceBinder) iBinder).getService();

            // Set the display name in the nav header to the user's display name
            ((TextView) ((NavigationView) findViewById(R.id.nav_view))
                    .getHeaderView(0).findViewById(R.id.displayName))
                    .setText(networkerService.user.DisplayName);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            networkerService = null;
        }
    };

    public NetworkerService networkerService;
    private AppBarConfiguration mAppBarConfiguration;
    private ActivityMainBinding binding;

    // Used on the "find people page"
    private User[] searchResults;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Bind to the networker service
        bindService(
                new Intent(this, NetworkerService.class),
                connection,
                BIND_AUTO_CREATE
        );

        // Set up the drawer and action bar (the bar at the top of the application)
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.appBarMain.toolbar);

        DrawerLayout drawer = binding.drawerLayout;
        NavigationView navigationView = binding.navView;
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        mAppBarConfiguration = new AppBarConfiguration.Builder(
                R.id.chatsFragment, R.id.findPeopleFragment)
                .setOpenableLayout(drawer)
                .build();
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        NavigationUI.setupActionBarWithNavController(this, navController, mAppBarConfiguration);
        NavigationUI.setupWithNavController(navigationView, navController);

        ((NavigationView) findViewById(R.id.nav_view)).setNavigationItemSelectedListener(new NavigationView.OnNavigationItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                NavController navController = Navigation.findNavController(MainActivity.this, R.id.nav_host_fragment_content_main);
                switch (item.getItemId()) {
                    case R.id.nav_chats:
                        navController.navigate(R.id.chatsFragment);
                        break;

                    case R.id.nav_find_people:
                        navController.navigate(R.id.findPeopleFragment);
                        break;
                }
                return false;
            }
        });

        // Set the text in the nav header to show the app is waiting for the networker service
        // It is unlikely that the user will actually see this message
        ((TextView) ((NavigationView) findViewById(R.id.nav_view))
                .getHeaderView(0).findViewById(R.id.displayName))
                .setText("Waiting for networker service");
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onSupportNavigateUp() {
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        return NavigationUI.navigateUp(navController, mAppBarConfiguration)
                || super.onSupportNavigateUp();
    }

    public void onSearchButtonClicked(View v) {
        View fragmentView = (View) v.getParent().getParent();

        // Get the display name entered into the search box
        String displayName = ((EditText) fragmentView.findViewById(R.id.UserSearchBar).findViewById(R.id.userSearchDisplayNameEntry)).getText().toString();
        // If the entry is empty, ask the user to enter a name and then end the method
        if (displayName.contentEquals("")) {
            Toast.makeText(this, "Please enter a name first!", Toast.LENGTH_SHORT).show();
            return;
        }

        // Create a request to search users by display name
        JSONObject request = new JSONObject();
        request.put("type", RequestType.GetUser);
        request.put("selector", "displayName");
        request.put("data", new ObjectContainer(new User(-1, null, null, displayName, null, null)));

        // Send the request
        networkerService.SendRequest(new NetworkerService.Request(
                new NetworkerService.IRequestCallback() {
                    @Override
                    public void callback(Result result, Object response) {
                        Runnable action;

                        if (result == Result.FAILED) {
                            action = () -> Toast.makeText(MainActivity.this, "Something went wrong :(", Toast.LENGTH_SHORT).show();
                        }
                        else {
                            action = () -> {

                                User[] results = (User[]) response;

                                // Get the fragment container linear layout to add the result fragments to
                                LinearLayout fragmentContainerLayout = fragmentView.findViewById(R.id.SearchResultFragmentContainer);

                                fragmentContainerLayout.removeAllViews();

                                // If the list of results is empty, hide the no results message
                                if (results.length == 0) {
                                    TextView message = new TextView(MainActivity.this);
                                    message.setText(R.string.no_search_results_message);

                                    ((LinearLayout) fragmentView.findViewById(R.id.SearchResultFragmentContainer)).addView(message);
                                }

                                // Clean the results of invalid results
                                int numberRemoved = 0;
                                for (int i = 0; i < results.length; i++) {
                                    // Check if this user is the logged in user
                                    if (results[i].UserID == networkerService.user.UserID) {
                                        results[i] = null;
                                        numberRemoved++;
                                    }
                                }
                                searchResults = new User[results.length - numberRemoved];
                                int finalIndex = 0;
                                for (int i = 0; i < results.length; i++) {
                                    if (results[i] != null) {
                                        searchResults[finalIndex] = results[i];
                                        finalIndex++;
                                    }
                                }

                                // Add each of the results to the fragment container layout
                                for (User user : searchResults) {
                                    // Generate a random id for the fragment container
                                    int id = new Random().nextInt();

                                    FragmentContainerView fragmentContainer = new FragmentContainerView(MainActivity.this);
                                    fragmentContainer.setId(id);

                                    // Create the argument bundle to pass to the fragment
                                    Bundle bundle = new Bundle();
                                    bundle.putSerializable("user", user);

                                    // Add the fragment to the fragment container view
                                    MainActivity.this.getSupportFragmentManager().beginTransaction()
                                            .setReorderingAllowed(true)
                                            .add(id, UserSearchFragment.class, bundle)
                                            .commit();

                                    // Add the fragment container view to the linear layout
                                    fragmentContainerLayout.addView(fragmentContainer);
                                }
                            };
                        }

                        // Run the predetermined action on the UI thread
                        MainActivity.this.runOnUiThread(action);
                    }

                },
                request));
    }

    public void onAddFriendButtonClicked(View v) {
        FragmentContainerView fragmentContainerView = (FragmentContainerView) v.getParent().getParent();
        LinearLayout ll = (LinearLayout) fragmentContainerView.getParent();
        User user = null;

        for (int i = 0; i < ll.getChildCount(); i++) {
            if (fragmentContainerView.equals(ll.getChildAt(i))) {
                user = searchResults[i];
            }
        }

        assert user != null;

        // Create a request to send a friend request
        JSONObject request = new JSONObject();
        request.put("type", RequestType.SendFriendRequest);
        request.put("data", new ObjectContainer(new FriendRequest(-1, networkerService.user.UserID, user.UserID, new Date().getTime())));

        // Send the request to the server
        networkerService.SendRequest(new NetworkerService.Request(
                new NetworkerService.IRequestCallback() {
                    @Override
                    public void callback(Result result, Object response) {
                        // Notify the user of the result
                        if (result == Result.SUCCESS) {
                            MainActivity.this.runOnUiThread(() -> Toast.makeText(MainActivity.this, "Friend request sent!", Toast.LENGTH_SHORT).show());
                        }
                        else {
                            MainActivity.this.runOnUiThread(() -> Toast.makeText(MainActivity.this, "Something went wrong :(", Toast.LENGTH_SHORT).show());
                        }
                    }
                }, request
        ));
    }
}
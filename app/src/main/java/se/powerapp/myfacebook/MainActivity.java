package se.powerapp.myfacebook;


import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.widget.SimpleCursorAdapter;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.Toast;

import com.facebook.Session;
import com.facebook.SessionState;
import com.facebook.UiLifecycleHelper;
import com.facebook.android.AsyncFacebookRunner;
import com.facebook.android.DialogError;
import com.facebook.android.Facebook;
import com.facebook.android.FacebookError;
import com.facebook.widget.LoginButton;
import com.facebook.widget.ProfilePictureView;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

import static android.widget.Toast.LENGTH_SHORT;

public class MainActivity extends FragmentActivity {

    public static final String TAG = "Johanna";
    private static final int CAPTURE_PHOTO_REQUEST_CODE = 1001;
    private Uri mCapturedPhotoUri;
    private static String APP_ID = "729814163702991"; // Replace your App ID here

    // Instance of Facebook Class
    private Facebook facebook;
    private AsyncFacebookRunner mAsyncRunner;
    String FILENAME = "AndroidSSO_data";
    private SharedPreferences mPrefs;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        facebook = new Facebook(APP_ID);
        mAsyncRunner = new AsyncFacebookRunner(facebook);

        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .add(R.id.container, new FacebookLogInFragment())
                    .commit();
        }
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {

        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == CAPTURE_PHOTO_REQUEST_CODE && resultCode == RESULT_OK) {
            Log.d(TAG, "Photo captured: " + mCapturedPhotoUri);
            FacebookService.startPostFacebookPhoto(this, mCapturedPhotoUri);
        }
    }

    public void onFacebookRefreshClicked(View view) {
        FacebookService.startActionUpdateFromWall(this);
    }

    public void onPostPhotoClicked(View view) {
        Session session = Session.getActiveSession();
        if (session.isOpened()) {
            session.requestNewPublishPermissions(
                    new Session.NewPermissionsRequest(this,
                            "publish_stream"));

            Intent takePhotoIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            mCapturedPhotoUri = getOutputPhotoUri(this);

            takePhotoIntent.putExtra(MediaStore.EXTRA_OUTPUT, mCapturedPhotoUri);

            startActivityForResult(takePhotoIntent, CAPTURE_PHOTO_REQUEST_CODE);
        }
    }

    private static Uri getOutputPhotoUri(Context context) {
        File mediaStorageDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);

        // Create a media file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        StringBuilder stringBuilder = new StringBuilder();
        return Uri.fromFile(new File(stringBuilder.append(mediaStorageDir.getPath())
                .append(File.separator)
                .append("IMG_")
                .append(timeStamp)
                .append(".jpg").toString()));
    }

    public void onPostStatusClicked(View v) {
        postToWall();
    }

    public void postToWall() {

        Session session = Session.getActiveSession();
        if (session.isOpened()) {
            session.requestNewPublishPermissions(
                    new Session.NewPermissionsRequest(this,
                            "publish_stream"));
            // post on user's wall.
            facebook.dialog(this, "feed", new Facebook.DialogListener() {

                @Override
                public void onFacebookError(FacebookError e) {
                }

                @Override
                public void onError(DialogError e) {
                }

                @Override
                public void onComplete(Bundle values) {
                }

                @Override
                public void onCancel() {
                }
            });

        }
    }


    public static class FacebookLogInFragment extends Fragment implements LoaderManager.LoaderCallbacks<Cursor> {


        private Session.StatusCallback mCallback = new Session.StatusCallback() {


            @Override
            public void call(Session session, SessionState state, Exception exception) {
                onSessionStateChange(session, state, exception);
            }
        };


        public FacebookLogInFragment() {
        }

        private UiLifecycleHelper mUiHelper;
        private SimpleCursorAdapter mListAdapter;


        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            mUiHelper = new UiLifecycleHelper(getActivity(), mCallback);
            mUiHelper.onCreate(savedInstanceState);


        }

        @Override
        public void onResume() {
            super.onResume();

            Session session = Session.getActiveSession();
            if (session != null && (session.isOpened()) || session.isClosed()) {
                onSessionStateChange(session, session.getState(), null);
            }

            mUiHelper.onResume();
        }

        private void onSessionStateChange(Session session, SessionState state, Exception e) {
            View refreshButton = getActivity().findViewById(R.id.update_button);
            View photoButton = getActivity().findViewById(R.id.post_photo_button);
            View statusButton = getActivity().findViewById(R.id.post_status_button);


            if (session.isOpened()) {
                refreshButton.setEnabled(true);
                photoButton.setEnabled(true);
                statusButton.setEnabled(true);
                Toast.makeText(getActivity(), "Du är inloggad", LENGTH_SHORT).show();
                FacebookService.startActionUpdateFromWall(getActivity());

            } else if (session.isClosed()) {
                Toast.makeText(getActivity(), "Du är utloggad", LENGTH_SHORT).show();
                photoButton.setEnabled(false);
                refreshButton.setEnabled(false);
                statusButton.setEnabled(false);
                FacebookService.startActionUserLogout(getActivity());


            }
        }

        @Override
        public void onActivityResult(int requestCode, int resultCode, Intent data) {
            super.onActivityResult(requestCode, resultCode, data);
            mUiHelper.onActivityResult(requestCode, resultCode, data);

        }

        @Override
        public void onPause() {
            super.onPause();
            mUiHelper.onPause();
        }


        @Override
        public void onDestroy() {
            super.onDestroy();
            mUiHelper.onDestroy();
        }

        @Override
        public void onSaveInstanceState(Bundle outState) {
            super.onSaveInstanceState(outState);
            mUiHelper.onSaveInstanceState(outState);
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {


            View rootView = inflater.inflate(R.layout.facebook_login, container, false);
            LoginButton loginButton = (LoginButton) rootView.findViewById(R.id.facebook_login_button);
            loginButton.setFragment(this);
            loginButton.setReadPermissions("user_status", "user_friends",
                    "friends_status", "read_stream", "user_location", "friends_location");

            mListAdapter = new SimpleCursorAdapter(getActivity(),
                    R.layout.facebook_listview,
                    null,
                    new String[]{FacebookFeed.Contract.FROM_NAME,
                            FacebookFeed.Contract.MESSAGE, FacebookFeed.Contract.FROM_ID, FacebookFeed.Contract.PLACE},
                    new int[]{R.id.from_name, R.id.message, R.id.profile_picture, R.id.location}, 0);
            mListAdapter.setViewBinder(new SimpleCursorAdapter.ViewBinder() {
                @Override
                public boolean setViewValue(View view, Cursor cursor, int columnIndex) {
                    if (columnIndex == cursor.getColumnIndex(FacebookFeed.Contract.FROM_ID)) {
                        ((ProfilePictureView) view).setProfileId(cursor.getString(columnIndex));
                        return true;
                    }
                    return false;
                }
            });

            ListView facebookMessages = (ListView) rootView.findViewById(R.id.newsfeed_listview);
            facebookMessages.setAdapter(mListAdapter);

            getLoaderManager().initLoader(0, null, this);

            return rootView;
        }

        @Override
        public Loader<Cursor> onCreateLoader(int i, Bundle bundle) {
            return new CursorLoader(getActivity(),
                    FacebookFeed.Contract.FACEBOOK_WALL_URI,
                    new String[]{FacebookFeed.Contract.ID,
                            FacebookFeed.Contract.FROM_NAME,
                            FacebookFeed.Contract.FROM_ID,
                            FacebookFeed.Contract.MESSAGE,
                            FacebookFeed.Contract.PLACE},
                    null, null, null);
        }

        @Override
        public void onLoadFinished(Loader<Cursor> objectLoader, Cursor cursor) {
            mListAdapter.swapCursor(cursor);
            Log.d(TAG, "Loader finished: " + cursor.getCount());
        }

        @Override
        public void onLoaderReset(Loader<Cursor> objectLoader) {
            Log.d(TAG, "Loader reset!");
        }

    }

}


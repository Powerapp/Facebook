package se.powerapp.myfacebook;

import android.app.AlarmManager;
import android.app.IntentService;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import com.facebook.HttpMethod;
import com.facebook.Request;
import com.facebook.Response;
import com.facebook.Session;
import com.facebook.model.GraphObject;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.FileNotFoundException;

public class FacebookService extends IntentService {
    // IntentService can perform, e.g. ACTION_FETCH_NEW_ITEMS
    private static final String ACTION_UPDATE_FROM_WALL = "se.powerapp.myfacebook.action.UPDATE_FROM_WALL";
    private static final String ACTION_USER_LOGOUT = "se.powerapp.myfacebook.action.USER_LOGOUT";
    private static final String ACTION_POST_PHOTO = "se.powerapp.myfacebook.action.POST_PHOTO";
    private static final String FACEBOOK_PREFS = "facebook_settings";
    private static final String NEXT_SINCE_VALUE = "nextSinceValue";


    public static void startActionUpdateFromWall(Context context) {
        Intent intent = new Intent(context, FacebookService.class);
        intent.setAction(ACTION_UPDATE_FROM_WALL);
        PendingIntent pendingIntent = PendingIntent.getService(context, 0, intent, 0);
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(ALARM_SERVICE);
        alarmManager.setInexactRepeating(AlarmManager.ELAPSED_REALTIME, 0,
                AlarmManager.INTERVAL_FIFTEEN_MINUTES, pendingIntent);

        context.startService(intent);
    }


    public static void startActionUserLogout(Context context) {
        Intent intent = new Intent(context, FacebookService.class);
        intent.setAction(ACTION_USER_LOGOUT);
        context.startService(intent);

        PendingIntent pendingIntent = PendingIntent.getService(context, 0, intent, 0);
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(ALARM_SERVICE);
        alarmManager.cancel(pendingIntent);
    }

    public static void startPostFacebookPhoto(Context context, Uri photoUri) {
        Intent intent = new Intent(context, FacebookService.class);
        intent.setAction(ACTION_POST_PHOTO);
        intent.setData(photoUri);
        context.startService(intent);
    }

    public FacebookService() {
        super(MainActivity.TAG);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent != null) {
            final String action = intent.getAction();
            if (ACTION_UPDATE_FROM_WALL.equals(action)) {
                handleActionUpdateFromWall();
            } else if (ACTION_POST_PHOTO.equals(action)) {
                handlePhotoUpload(intent.getData());
            } else if (ACTION_USER_LOGOUT.equals(action)) {
                handleActionUserLogout();
            }
        }
    }

    private void handlePhotoUpload(Uri photoUri) {
        Session session = Session.getActiveSession();
        boolean isOpened = session.isOpened();
        Log.d(MainActivity.TAG, "Logged in to facebook: " + isOpened);
        if (session != null && isOpened && photoUri != null) {
            try {
                ContentResolver resolver = getContentResolver();
                Bitmap bitmap = BitmapFactory.decodeStream(resolver.openInputStream(photoUri));
                Request request = Request.newUploadPhotoRequest(session, bitmap, new Request.Callback() {
                    @Override
                    public void onCompleted(Response response) {
                        Log.d(MainActivity.TAG, "Response: " + response.getError());
                    }
                });
                Response response = request.executeAndWait();
                GraphObject graphObject = response.getGraphObject();
                if (graphObject != null) {
                    Log.d(MainActivity.TAG, graphObject.toString());
                } else {
                    Log.d(MainActivity.TAG, "Response: " + response);
                }

            } catch (FileNotFoundException e) {
                Log.e(MainActivity.TAG, "Error uploading photo to Facebook!");
            }
        }
    }

    private void handleActionUpdateFromWall() {
        // Get active Facebook Session
        Session session = Session.getActiveSession();
        boolean isOpened = session.isOpened();
        Log.d(MainActivity.TAG, "Logged in to facebook: " + isOpened);
        if (session != null && isOpened) {
            SharedPreferences preferences
                    = getSharedPreferences(FACEBOOK_PREFS, MODE_PRIVATE);
            long nextSinceValue = preferences.getLong(NEXT_SINCE_VALUE, -1);
            Bundle params = new Bundle();
            params.putString("fields", "id,from,message,type,place");
            params.putInt("limit", 50);
            String graphPath = "me/home";
            if (nextSinceValue > 0) {
                params.putLong("since", nextSinceValue);
            }
            Request request = new Request(session, graphPath, params, HttpMethod.GET);
            Response response = request.executeAndWait();
            Log.d(MainActivity.TAG, "Response: " + response.getError());

            // Fetch current time to use in next request!

            GraphObject graphObject = response.getGraphObject();
//            Log.d(MainActivity.TAG, "Got graphObject: " + graphObject);
            if (graphObject != null) {
                long nowInSeconds = System.currentTimeMillis() / 1000;
                preferences.edit().putLong(NEXT_SINCE_VALUE, nowInSeconds).apply();

                JSONArray dataArray = (JSONArray) graphObject.getProperty("data");

                int length = dataArray.length();
                for (int i = 0; i < length; i++) {
                    JSONObject wallMessage = null;
                    try {
                        wallMessage = dataArray.getJSONObject(i);
                        storeWallMessage(wallMessage);
                    } catch (JSONException e) {
                        Log.e(MainActivity.TAG,
                                "Invalid message format: " + wallMessage, e);
                    }
                }
            }
        }
    }

    private void storeWallMessage(JSONObject wallMessage) throws JSONException {
        String messageId = wallMessage.getString("id");
        JSONObject from = wallMessage.getJSONObject("from");
        String fromId = from.getString("id");
        String fromName = from.getString("name");
        String type = wallMessage.getString("type");
        String message = null;
        try {
            message = wallMessage.getString("message");
        } catch (JSONException e) {
            e.printStackTrace();
        }
        String createdTime = wallMessage.getString("created_time");
        String placeName = null;
        try {
            JSONObject place = wallMessage.getJSONObject("place");
            placeName = place.getString("name");
        } catch (JSONException e) {
            e.printStackTrace();
        }

        ContentValues values = new ContentValues();
        values.put(FacebookFeed.Contract.MESSAGE_ID, messageId);
        values.put(FacebookFeed.Contract.FROM_ID, fromId);
        values.put(FacebookFeed.Contract.FROM_NAME, fromName);
        values.put(FacebookFeed.Contract.MESSAGE, message);
        values.put(FacebookFeed.Contract.TYPE, type);
        values.put(FacebookFeed.Contract.CREATED_TIME, createdTime);
        values.put(FacebookFeed.Contract.PLACE, placeName);
        Uri newMessage = getContentResolver()
                .insert(FacebookFeed.Contract.FACEBOOK_WALL_URI,
                        values);
        if (newMessage == null) {
            Log.e(MainActivity.TAG, "Invalid message!");
        } else {
            Log.d(MainActivity.TAG, "Inserted: " + newMessage);
        }
    }

    /**
     * Handle action Baz in the provided background thread with the provided
     * parameters.
     */
    private void handleActionUserLogout() {
        getContentResolver()
                .delete(FacebookFeed.Contract.FACEBOOK_WALL_URI,
                        null, null);
        SharedPreferences preferences
                = getSharedPreferences(FACEBOOK_PREFS, MODE_PRIVATE);
        preferences.edit().remove(NEXT_SINCE_VALUE).apply();
    }
}

package com.example.android.wearablemessageapiexample;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.util.Log;
import android.view.View;
//import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.Wearable;

import org.codehaus.plexus.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class WearActivity extends Activity {
    private final String COMMAND_PATH = "/command";
    private static final int VOICE_RECOGNITION_REQUEST_CODE = 1001;
    private GoogleApiClient apiClient;
    private String remoteNodeId, calcStr;
    private ImageButton mbtSpeak;

    private TextView mTextSmall, mTextBig, mTextDraw, mTextClear;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_activity);

        mbtSpeak    = (ImageButton) findViewById(R.id.btSpeak);
        mTextSmall  = (TextView) findViewById(R.id.textSmall);
        mTextDraw   = (TextView) findViewById(R.id.textDraw);
        mTextBig    = (TextView) findViewById(R.id.textBig);
        mTextClear  = (TextView) findViewById(R.id.textClear);

        apiClient = apiClientFactory();
        checkVoiceRecognition();
    }

    public void checkVoiceRecognition() {
        // Check if voice recognition is present
        PackageManager pm = getPackageManager();
        List<ResolveInfo> activities = pm.queryIntentActivities(new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH), 0);
        if (activities.size() == 0) {
            mbtSpeak.setEnabled(false);
            showToastMessage("Voice recognizer not present");
        }
    }

    public void speak(View view) {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);

        // Specify the calling package to identify your application
        intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, getClass()
                .getPackage().getName());

        // Given an hint to the recognizer about what the user is going to say
        //There are two form of language model available
        //1.LANGUAGE_MODEL_WEB_SEARCH : For short phrases
        //2.LANGUAGE_MODEL_FREE_FORM  : If not sure about the words or phrases and its domain.
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH);

        // Specify how many results you want to receive. The results will be
        // sorted where the first result is the one with higher confidence.
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        //Start the Voice recognizer activity for the result.
        startActivityForResult(intent, VOICE_RECOGNITION_REQUEST_CODE);
    }

    // Bind: click
    public void recordSmall(View view) {
        updateLog("small");
    }
    public void recordDraw(View view) {
        updateLog("draw");
    }
    public void recordBig(View view) {
        updateLog("big");
    }
    public void recordClear(View view) {
        updateLog("clear");
    }

    public void updateLog(String mode) {
        final String recordType;

        if (mode == "small") {
            recordType = "recordSmall";
        } else if (mode == "big") {
            recordType = "recordBig";
        } else if (mode == "draw") {
            recordType = "recordDraw";
        } else {
            recordType = "recordClear";
        }

        // Log prepare
        SharedPreferences sharedPref = this.getPreferences(Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();

        if (recordType == "recordClear") {
            // 1. Do confirm first (advance)
            // Clear log history
            editor.remove("recordSmall")
                .remove("recordBig")
                .remove("recordDraw")
            .apply();

            // Clear board
            ((TextView)findViewById(R.id.textBoard)).setText("Record empty");

            // btn - init
            ((TextView) findViewById(R.id.textSmall)).setText("S");
            ((TextView) findViewById(R.id.textBig)).setText("B");
            ((TextView) findViewById(R.id.textDraw)).setText("D");
        } else {
            // Print status to user
            ((TextView)findViewById(R.id.textBoard)).setText("updating " + mode + " ...");

            /* Update record */
            // Get log
            int result = sharedPref.getInt(recordType, 0);
            // Write log
            editor.putInt(recordType, (result + 1));
            // Get log
            int resultUpdated = sharedPref.getInt(recordType, 0);

            /* Show analytics in delay 0.5s */
            if(editor.commit()) {
                // Hide btn when updating
                if(recordType == "recordSmall")
                    ((TextView) findViewById(R.id.textSmall)).setText("S");
                if(recordType == "recordBig")
                    ((TextView) findViewById(R.id.textBig)).setText("B");
                if(recordType == "recordDraw")
                    ((TextView) findViewById(R.id.textDraw)).setText("D");

                // Get results
                final int logSmall = sharedPref.getInt("recordSmall", 0);
                final int logBig = sharedPref.getInt("recordBig", 0);
                final int logDraw = sharedPref.getInt("recordDraw", 0);
                int logTotal[] = {logSmall, logBig, logDraw};
                Arrays.sort(logTotal);
                final String updatedMsg = "s" + logSmall + ", " + "b" + logBig + ", " + "d" + logDraw ;

                new android.os.Handler().postDelayed(
                    new Runnable() {
                        public void run() {
                            ((TextView) findViewById(R.id.textBoard)).setText(updatedMsg);

                            // btn - Rewrite
                            if(recordType == "recordSmall")
                                ((TextView) findViewById(R.id.textSmall)).setText("S (" + logSmall + ")");
                            if(recordType == "recordBig")
                            ((TextView) findViewById(R.id.textBig)).setText("(" + logBig + ") B");
                            if(recordType == "recordDraw")
                            ((TextView) findViewById(R.id.textDraw)).setText("D (" + logDraw + ")");
                        }
                    }, 500);

                // Log read - DEBUG // ***
//                Log.d("recordBig", "" + logRecord);
            }
        }
    }

    public void boardUpdate(final String str) {

    }

    /**
     * Allows the app to send asyncronic messages and no blocking the UI thread
     */
    private class SendMessage extends AsyncTask<String, Void, Void> {

        @Override
        protected Void doInBackground(String... params) {
            Wearable.MessageApi.sendMessage(apiClient, remoteNodeId, COMMAND_PATH, params[0].getBytes());
            return null;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        //If Voice recognition is successful then it returns RESULT_OK
        if (requestCode == VOICE_RECOGNITION_REQUEST_CODE)
        {
            if (resultCode == RESULT_OK) {

                ArrayList<String> textMatchList = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);

            if (!textMatchList.isEmpty()) {
                String voiceMessage = StringUtils.join(textMatchList.iterator(), "#");
                Wearable.MessageApi.sendMessage(apiClient, remoteNodeId, COMMAND_PATH, voiceMessage.getBytes());
            }
            //Result code for various error.
            } else if (resultCode == RecognizerIntent.RESULT_AUDIO_ERROR) {
                showToastMessage("Audio Error");
            } else if (resultCode == RecognizerIntent.RESULT_CLIENT_ERROR) {
                showToastMessage("Client Error");
            } else if (resultCode == RecognizerIntent.RESULT_NETWORK_ERROR) {
                showToastMessage("Network Error");
            } else if (resultCode == RecognizerIntent.RESULT_NO_MATCH) {
                showToastMessage("No Match");
            } else if (resultCode == RecognizerIntent.RESULT_SERVER_ERROR) {
                showToastMessage("Server Error");
            }

            //TODO: no message sent may be possible.
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    /**
     * Helper method to show the toast message
     **/
    void showToastMessage(String message){
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Check is Google Play Services available
        int connectionResult = GooglePlayServicesUtil.isGooglePlayServicesAvailable(getApplicationContext());

        if (connectionResult != ConnectionResult.SUCCESS) {
            // Google Play Services is NOT available. Show appropriate error dialog
            GooglePlayServicesUtil.showErrorDialogFragment(connectionResult, this, 0, new DialogInterface.OnCancelListener() {
                @Override
                public void onCancel(DialogInterface dialog) {
                    finish();
                }
            });
        } else {
            apiClient.connect();
        }
    }

    @Override
    protected void onPause() {
        // Unregister Node and Message listeners, disconnect GoogleApiClient and disable buttons
        Wearable.NodeApi.removeListener(apiClient, null);
        apiClient.disconnect();
        super.onPause();
    }

    /**
     * Create GoogleApiClient
     */
    private GoogleApiClient apiClientFactory() {
        return new GoogleApiClient.Builder(getApplicationContext()).addConnectionCallbacks(new GoogleApiClient.ConnectionCallbacks() {
            @Override
            public void onConnected(Bundle bundle) {
                // Register Node and Message listeners
                Wearable.NodeApi.addListener(apiClient, null);
                // If there is a connected node, get it's id that is used when sending messages
                Wearable.NodeApi.getConnectedNodes(apiClient).setResultCallback(new ResultCallback<NodeApi.GetConnectedNodesResult>() {
                    @Override
                    public void onResult(NodeApi.GetConnectedNodesResult getConnectedNodesResult) {
                        if (getConnectedNodesResult.getStatus().isSuccess() && getConnectedNodesResult.getNodes().size() > 0) {
                            remoteNodeId = getConnectedNodesResult.getNodes().get(0).getId();
                        }
                    }
                });
            }

            @Override
            public void onConnectionSuspended(int i) {

            }
        }).addApi(Wearable.API).build();
    }
}

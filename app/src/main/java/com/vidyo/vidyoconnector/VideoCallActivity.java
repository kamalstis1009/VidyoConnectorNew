package com.vidyo.vidyoconnector;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.ToggleButton;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.vidyo.VidyoClient.Connector.Connector;
import com.vidyo.VidyoClient.Connector.ConnectorPkg;
import com.vidyo.VidyoClient.Endpoint.LogRecord;
import com.vidyo.VidyoClient.Endpoint.Participant;

import java.util.ArrayList;

import pub.devrel.easypermissions.AfterPermissionGranted;
import pub.devrel.easypermissions.EasyPermissions;

public class VideoCallActivity extends AppCompatActivity implements MyNetworkReceiver.NetworkListener, Connector.IConnect, Connector.IRegisterLogEventListener, Connector.IRegisterParticipantEventListener {

    private static final String TAG = "VideoCallActivity";

    private static final int RC_SETTINGS_SCREEN_PERM = 123;
    private static final int RC_VIDEO_APP_PERM = 124;

    enum VIDYO_CONNECTOR_STATE {
        VC_CONNECTED,
        VC_DISCONNECTED,
        VC_DISCONNECTED_UNEXPECTED,
        VC_CONNECTION_FAILURE
    }

    private VIDYO_CONNECTOR_STATE mVidyoConnectorState = VIDYO_CONNECTOR_STATE.VC_DISCONNECTED;
    private boolean mVidyoConnectorConstructed = false;
    private boolean mVidyoClientInitialized = false;
    private Logger mLogger = Logger.getInstance();
    private Connector mVidyoConnector = null;
    private ToggleButton mToggleConnectButton;
    private ProgressBar mConnectionSpinner;
    private LinearLayout mToolbarLayout;
    private TextView mToolbarStatus;
    private FrameLayout mVideoFrame;
    private FrameLayout mToggleToolbarFrame;

    private String mHost = "prod.vidyo.io", mDisplayName = "Zamal", mResourceId = "kamal123456";
    private boolean mAutoJoin = false;
    private boolean mAllowReconnect = true;
    private String mReturnURL = null;

    /*
     *  Operating System Events
     */

    private MyNetworkReceiver mNetworkReceiver;
    private String mVidyoToken;
    private boolean isEnabled;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_call);

        //--------------------------------------------| Network
        mNetworkReceiver = new MyNetworkReceiver(this, this);

        if (getIntent().getExtras() != null) {
            mVidyoToken = getIntent().getStringExtra("token");
        }

        requestPermissions();
    }

    @Override
    public void onNetworkLatency(String latency, boolean isConnected) {
        Log.d(TAG, "onNetworkLatency 1: " + latency + " - " + isConnected + " - " + isEnabled);

        if (mVidyoConnector != null) {
            if (latency.equals("2G")) {
            }
        }
        if (isConnected && isEnabled) {
            mToggleConnectButton.setEnabled(true);
            mToggleConnectButton.performClick();
            Log.d(TAG, "onNetworkLatency 2: " + latency + " - " + isConnected + " - " + isEnabled);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this);
    }

    @AfterPermissionGranted(RC_VIDEO_APP_PERM)
    private void requestPermissions() {
        String[] perms = { Manifest.permission.INTERNET, Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO, Manifest.permission.WRITE_EXTERNAL_STORAGE };
        if (EasyPermissions.hasPermissions(this, perms)) {

            // initialize view objects from your layout
            mToggleConnectButton = (ToggleButton) findViewById(R.id.toggleConnectButton);
            mToolbarLayout = (LinearLayout) findViewById(R.id.toolbarLayout);
            mVideoFrame = (FrameLayout) findViewById(R.id.videoFrame);
            mToggleToolbarFrame = (FrameLayout) findViewById(R.id.toggleToolbarFrame);
            mToolbarStatus = (TextView) findViewById(R.id.toolbarStatusText);
            mConnectionSpinner = (ProgressBar) findViewById(R.id.connectionSpinner);

            // Suppress keyboard
            getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);

            // Initialize the VidyoClient
            ConnectorPkg.setApplicationUIContext(this);
            mVidyoClientInitialized = ConnectorPkg.initialize();

            // initialize and connect to the session
            onVidyoStart();

            startConnect();

        } else {
            EasyPermissions.requestPermissions(this, "This app needs access to your camera and mic to make video calls", RC_VIDEO_APP_PERM, perms);
        }
    }

    //=============================================================| onStart(), onPause(), onResume(), onStop()
    @Override
    protected void onResume() {
        super.onResume();

        // NetworkReceiver
        registerReceiver(mNetworkReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));

        //startConnect();
    }

    @Override
    protected void onPause() {
        super.onPause();

        // NetworkReceiver
        try {
            unregisterReceiver(mNetworkReceiver);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        if (mVidyoConnector != null) {
            mVidyoConnector.setMode((Connector.ConnectorMode.VIDYO_CONNECTORMODE_Foreground));
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mVidyoConnector != null) {
            mVidyoConnector.setMode(Connector.ConnectorMode.VIDYO_CONNECTORMODE_Background);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mVidyoConnector != null) {
            //Connector.Uninitialize();
            mVidyoConnector.disconnect();
        }
    }

    private void onVidyoStart() {
        // Enable toggle connect button
        mToggleConnectButton.setEnabled(true);

    }

    private void startConnect() {
        ViewTreeObserver viewTreeObserver = mVideoFrame.getViewTreeObserver();
        if (viewTreeObserver.isAlive()) {
            viewTreeObserver.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    mVideoFrame.getViewTreeObserver().removeOnGlobalLayoutListener(this);

                    // If the vidyo connector was not previously successfully constructed then construct it

                    if (!mVidyoConnectorConstructed) {

                        if (mVidyoClientInitialized) {

                            mVidyoConnector = new Connector(mVideoFrame,
                                    Connector.ConnectorViewStyle.VIDYO_CONNECTORVIEWSTYLE_Default,
                                    16,
                                    "info@VidyoClient info@VidyoConnector warning",
                                    "",
                                    0);

                            if (mVidyoConnector != null) {
                                mVidyoConnectorConstructed = true;

                                // Set initial position
                                RefreshUI();

                                // Register for log callbacks
                                if (!mVidyoConnector.registerLogEventListener(VideoCallActivity.this, "info@VidyoClient info@VidyoConnector warning")) {
                                    Log.d(TAG, "VidyoConnector RegisterLogEventListener failed");
                                }
                            } else {
                                Log.d(TAG, "VidyoConnector Construction failed - cannot connect...");
                            }
                        } else {
                            Log.d(TAG, "ERROR: VidyoClientInitialize failed - not constructing VidyoConnector ...");
                        }

                        Logger.getInstance().Log("onResume: mVidyoConnectorConstructed => " + (mVidyoConnectorConstructed ? "success" : "failed"));
                    }

                    // If configured to auto-join, then simulate a click of the toggle connect button
                    if (mVidyoConnectorConstructed && mAutoJoin) {
                        mToggleConnectButton.performClick();
                    }
                }
            });
        }
    }

    //=============================================================| orientation onConfigurationChanged
    // The device interface orientation has changed
    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        Log.d(TAG, "onConfigurationChanged");
        super.onConfigurationChanged(newConfig);

        // Refresh the video size after it is painted
        ViewTreeObserver viewTreeObserver = mVideoFrame.getViewTreeObserver();
        if (viewTreeObserver.isAlive()) {
            viewTreeObserver.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    mVideoFrame.getViewTreeObserver().removeOnGlobalLayoutListener(this);

                    // Width/height values of views not updated at this point so need to wait
                    // before refreshing UI

                    RefreshUI();
                }
            });
        }
    }

    //=============================================================| RefreshUI
    /*
     * Private Utility Functions
     */

    // Refresh the UI
    private void RefreshUI() {
        // Refresh the rendering of the video
        mVidyoConnector.showViewAt(mVideoFrame, 0, 0, mVideoFrame.getWidth(), mVideoFrame.getHeight());
        Log.d(TAG, "VidyoConnectorShowViewAt: x = 0, y = 0, w = " + mVideoFrame.getWidth() + ", h = " + mVideoFrame.getHeight());
    }

    //=============================================================| ConnectorStateUpdated
    // The state of the VidyoConnector connection changed, reconfigure the UI.
    // If connected, dismiss the controls layout
    private void ConnectorStateUpdated(VIDYO_CONNECTOR_STATE state, final String statusText) {
        Log.d(TAG, "ConnectorStateUpdated, state = " + state.toString());

        mVidyoConnectorState = state;

        // Execute this code on the main thread since it is updating the UI layout

        runOnUiThread(new Runnable() {
            @Override
            public void run() {

                // Update the toggle connect button to either start call or end call image
                mToggleConnectButton.setChecked(mVidyoConnectorState == VIDYO_CONNECTOR_STATE.VC_CONNECTED);

                // Set the status text in the toolbar
                mToolbarStatus.setText(statusText);

                if (mVidyoConnectorState == VIDYO_CONNECTOR_STATE.VC_CONNECTED) {
                    // Enable the toggle toolbar control
                    mToggleToolbarFrame.setVisibility(View.VISIBLE);

                } else {
                    // VidyoConnector is disconnected

                    // Disable the toggle toolbar control
                    mToggleToolbarFrame.setVisibility(View.GONE);

                    // If a return URL was provided as an input parameter, then return to that application
                    if (mReturnURL != null) {
                        // Provide a callstate of either 0 or 1, depending on whether the call was successful
                        Intent returnApp = getPackageManager().getLaunchIntentForPackage(mReturnURL);
                        returnApp.putExtra("callstate", (mVidyoConnectorState == VIDYO_CONNECTOR_STATE.VC_DISCONNECTED) ? 1 : 0);
                        startActivity(returnApp);
                    }

                    // If the allow-reconnect flag is set to false and a normal (non-failure) disconnect occurred,
                    // then disable the toggle connect button, in order to prevent reconnection.
                    if (!mAllowReconnect && (mVidyoConnectorState == VIDYO_CONNECTOR_STATE.VC_DISCONNECTED)) {
                        mToggleConnectButton.setEnabled(false);
                        mToolbarStatus.setText("Call ended");
                    }
                }

                // Hide the spinner animation
                mConnectionSpinner.setVisibility(View.INVISIBLE);
            }
        });
    }

    //=============================================================| Button Event
    /*
     * Button Event Callbacks
     */

    // The Connect button was pressed.
    // If not in a call, attempt to connect to the backend service.
    // If in a call, disconnect.
    public void ToggleConnectButtonPressed(View v) {
        if (mToggleConnectButton.isChecked()) {
            mToolbarStatus.setText("Connecting...");

            // Display the spinner animation
            mConnectionSpinner.setVisibility(View.VISIBLE);

            final boolean status = mVidyoConnector.connect(
                    mHost,
                    mVidyoToken,
                    mDisplayName,
                    mResourceId,
                    this);
            if (!status) {
                // Hide the spinner animation
                mConnectionSpinner.setVisibility(View.INVISIBLE);

                ConnectorStateUpdated(VIDYO_CONNECTOR_STATE.VC_CONNECTION_FAILURE, "Connection failed");
            }
            Log.d(TAG, "VidyoConnectorConnect status = " + status);

            mVidyoConnector.registerParticipantEventListener(this);
        } else {
            // The button just switched to the callStart image: The user is either connected to a resource
            // or is in the process of connecting to a resource; call VidyoConnectorDisconnect to either disconnect
            // or abort the connection attempt.
            // Change the button back to the callEnd image because do not want to assume that the Disconnect
            // call will actually end the call. Need to wait for the callback to be received
            // before swapping to the callStart image.
            mToggleConnectButton.setChecked(true);

            mToolbarStatus.setText("Disconnecting...");

            mVidyoConnector.unregisterParticipantEventListener();

            mVidyoConnector.disconnect();
        }
    }

    // Toggle the microphone privacy
    public void MicrophonePrivacyButtonPressed(View v) {
        mVidyoConnector.setMicrophonePrivacy(((ToggleButton) v).isChecked());
    }

    // Toggle the camera privacy
    public void CameraPrivacyButtonPressed(View v) {
        mVidyoConnector.setCameraPrivacy(((ToggleButton) v).isChecked());
    }

    // Handle the camera swap button being pressed. Cycle the camera.
    public void CameraSwapButtonPressed(View v) {
        mVidyoConnector.cycleCamera();
    }

    // Toggle visibility of the toolbar
    public void ToggleToolbarVisibility(View v) {
        if (mVidyoConnectorState == VIDYO_CONNECTOR_STATE.VC_CONNECTED) {
            if (mToolbarLayout.getVisibility() == View.VISIBLE) {
                mToolbarLayout.setVisibility(View.INVISIBLE);
            } else {
                mToolbarLayout.setVisibility(View.VISIBLE);
            }
        }
    }

    //=============================================================| Connection State
    /*
     *  Connector Events
     */

    // Handle successful connection.
    public void onSuccess() {
        Log.d(TAG, "OnSuccess: successfully connected.");
        ConnectorStateUpdated(VIDYO_CONNECTOR_STATE.VC_CONNECTED, "Connected");
        isEnabled = false;
    }

    // Handle attempted connection failure.
    public void onFailure(Connector.ConnectorFailReason reason) {
        Log.d(TAG, "OnFailure: connection attempt failed, reason = " + reason.toString());

        // Update UI to reflect connection failed
        ConnectorStateUpdated(VIDYO_CONNECTOR_STATE.VC_CONNECTION_FAILURE, "Connection failed");
    }

    // Handle an existing session being disconnected.
    public void onDisconnected(Connector.ConnectorDisconnectReason reason) {
        if (reason == Connector.ConnectorDisconnectReason.VIDYO_CONNECTORDISCONNECTREASON_Disconnected) {
            Log.d(TAG, "OnDisconnected: successfully disconnected, reason = " + reason.toString());
            ConnectorStateUpdated(VIDYO_CONNECTOR_STATE.VC_DISCONNECTED, "Disconnected");
        } else {
            Log.d(TAG, "OnDisconnected: unexpected disconnection, reason = " + reason.toString());
            ConnectorStateUpdated(VIDYO_CONNECTOR_STATE.VC_DISCONNECTED_UNEXPECTED, "Unexpected disconnection");
            isEnabled = true;
        }
    }

    // Handle a message being logged.
    public void onLog(LogRecord logRecord) {
        mLogger.LogClientLib(logRecord.message);
    }

    //=============================================================| ParticipantEventListener
    @Override
    public void onParticipantJoined(final Participant participant) {
        Log.d(TAG, "joined participant id : " + participant.getId());
        Log.d(TAG, "joined participant name : " + participant.getName());
        Log.d(TAG, "joined participant userId : " + participant.getUserId());
        Log.d(TAG, "joined participant object ptr : " + participant.GetObjectPtr());
        Log.d(TAG, "joined participant isHidden : " + participant.isHidden());
        Log.d(TAG, "joined participant isLocal : " + participant.isLocal());
        Log.d(TAG, "joined participant isRecording : " + participant.isRecording());
        Log.d(TAG, "joined participant isSelectable : " + participant.isSelectable());
    }

    @Override
    public void onParticipantLeft(Participant participant) {
        Log.d(TAG, "left participant id : " + participant.getId());
        Log.d(TAG, "left participant name : " + participant.getName());
        Log.d(TAG, "left participant userId : " + participant.getUserId());
        Log.d(TAG, "left participant object ptr : " + participant.GetObjectPtr());
        Log.d(TAG, "left participant isHidden : " + participant.isHidden());
        Log.d(TAG, "left participant isLocal : " + participant.isLocal());
        Log.d(TAG, "left participant isRecording : " + participant.isRecording());
        Log.d(TAG, "left participant isSelectable : " + participant.isSelectable());
    }

    @Override
    public void onDynamicParticipantChanged(ArrayList<Participant> arrayList) {
        for (Participant participant : arrayList) {
            Log.d(TAG, "Participant : " + participant.getName());
        }

        /*for (RemoteCamera remoteCamera : arrayList1) {
            Log.d(TAG, "remote camera : " + remoteCamera.getName());
        }*/
    }

    @Override
    public void onLoudestParticipantChanged(Participant vidyoParticipant, boolean b) {
        Log.d(TAG, "loudest participant id : " + vidyoParticipant.getId());
        Log.d(TAG, "loudest participant name : " + vidyoParticipant.getName());
        Log.d(TAG, "loudest participant userId : " + vidyoParticipant.getUserId());
        Log.d(TAG, "loudest participant object ptr : " + vidyoParticipant.GetObjectPtr());
        Log.d(TAG, "loudest participant isHidden : " + vidyoParticipant.isHidden());
        Log.d(TAG, "loudest participant isLocal : " + vidyoParticipant.isLocal());
        Log.d(TAG, "loudest participant isRecording : " + vidyoParticipant.isRecording());
        Log.d(TAG, "loudest participant isSelectable : " + vidyoParticipant.isSelectable());

        Log.d(TAG, "boolean : " + b);
    }
}

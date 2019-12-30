package com.vidyo.vidyoconnector;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;

import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    //Runtime Permissions
    private String[] PERMISSIONS = { android.Manifest.permission.CAMERA, android.Manifest.permission.RECORD_AUDIO, android.Manifest.permission.WRITE_EXTERNAL_STORAGE };
    private PermissionUtility mPermissions;
    private MyNetworkReceiver mNetworkReceiver;
    private String mVidyoToken;
    private boolean isGranted = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //--------------------------------------------| Network, permissions
        mPermissions = new PermissionUtility(this, PERMISSIONS); //Runtime permissions

        //--------------------------------------------| Vidyo.io server token
        getVidyoToken();

        //--------------------------------------------| Runtime Permissions
        if(mPermissions.arePermissionsEnabled()){
            isGranted = true;
            Log.d(TAG, "Permission granted 1");
        } else {
            mPermissions.requestMultiplePermissions();
        }

    }

    //=============================================================| Vidyo.io server token
    public void getVidyoToken() {
        String url = "https://us-central1-mobidoc-e1c92.cloudfunctions.net/getVidyoTokenByKamal";
        StringRequest stringRequest = new StringRequest(url, new Response.Listener<String>() {
            @Override
            public void onResponse(String response) {
                try {
                    JSONObject jsonObject = new JSONObject(response);
                    mVidyoToken = jsonObject.getString("token");
                    Log.d(TAG, mVidyoToken);
                    if (isGranted) {
                        goVideoActivity();
                    }
                    goVideoActivity();
                } catch (JSONException e) {
                    e.printStackTrace();
                    Log.e(TAG, e.getMessage());
                }
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Log.e(TAG, error.getMessage());
            }
        });
        RequestQueue requestQueue = Volley.newRequestQueue(this);
        requestQueue.add(stringRequest);
    }

    //=============================================================| Runtime permissions
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if(mPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults)) {
            goVideoActivity();
            Log.d(TAG, "Permission granted 2");
        }
    }

    private void goVideoActivity() {
        if (mVidyoToken != null) {
            Intent intent = new Intent(this, VideoCallActivity.class);
            intent.putExtra("token", mVidyoToken);
            startActivity(intent);
        }
    }
}

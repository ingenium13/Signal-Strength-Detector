package com.lordsutch.android.signaldetector;

// Android Packages
import java.lang.ref.WeakReference;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.WindowManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.widget.TextView;

import com.lordsutch.android.signaldetector.SignalDetectorService.LocalBinder;
import com.lordsutch.android.signaldetector.SignalDetectorService.signalInfo;

public final class HomeActivity extends Activity
{
	public static final String TAG = HomeActivity.class.getSimpleName();
	public static final String EMAIL = "lordsutch@gmail.com";
	    
    private static WebView leafletView = null;
    
    private boolean bsmarker = false;
	
	/** Called when the activity is first created. */
	@SuppressLint("SetJavaScriptEnabled")
	@Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        
    	leafletView = (WebView) findViewById(R.id.leafletView);
    	leafletView.loadUrl("file:///android_asset/leaflet.html");
    	
    	WebSettings webSettings = leafletView.getSettings();
    	// webSettings.setAllowFileAccessFromFileURLs(true);
    	webSettings.setJavaScriptEnabled(true);    	   

    	// Enable client caching
    	leafletView.setWebChromeClient(new WebChromeClient() {
    	      @Override
    	      public void onReachedMaxAppCacheSize(long spaceNeeded, long totalUsedQuota,
    	                   WebStorage.QuotaUpdater quotaUpdater)
    	      {
    	            quotaUpdater.updateQuota(spaceNeeded * 2);
    	      }
    	});
    	 
    	webSettings.setDomStorageEnabled(true);
    	 
    	// Set cache size to 2 mb by default. should be more than enough
    	webSettings.setAppCacheMaxSize(1024*1024*2);
    	 
    	// This next one is crazy. It's the DEFAULT location for your app's cache
    	// But it didn't work for me without this line.
    	// UPDATE: no hardcoded path. Thanks to Kevin Hawkins
    	String appCachePath = getApplicationContext().getCacheDir().getAbsolutePath();
    	webSettings.setAppCachePath(appCachePath);
    	webSettings.setAllowFileAccess(true);
    	webSettings.setAppCacheEnabled(true);
    }
    
	SignalDetectorService mService;
	boolean mBound = false;
	
    @Override
    protected void onStart() {
        super.onStart();
        
        // This verification should be done during onStart() because the system calls
        // this method when the user returns to the activity, which ensures the desired
        // location provider is enabled each time the activity resumes from the stopped state.
        LocationManager locationManager =
                (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        final boolean gpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);

        if (!gpsEnabled) {
            // Build an alert dialog here that requests that the user enable
            // the location services, then when the user clicks the "OK" button,
            // call enableLocationSettings()
            new EnableGpsDialogFragment().show(getFragmentManager(), "enableLocationSettings");
        }
        
        // Bind cell tracking service
        Intent intent = new Intent(this, SignalDetectorService.class);
        
        startService(intent);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE | Context.BIND_IMPORTANT);
    }
    
    @Override
    protected void onResume() {
    	super.onResume();

    	Log.d(TAG, "Resuming");
        if(mSignalInfo != null)
        	updateGui(mSignalInfo);
    }

    private void enableLocationSettings() {
        Intent settingsIntent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
        startActivity(settingsIntent);
    }

    private ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            LocalBinder binder = (LocalBinder) service;
            mService = binder.getService();
            mBound = true;
            mService.setMessenger(mMessenger);
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mBound = false;
        }
    };

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.mainmenu, menu);
        
        MenuItem x = menu.findItem(R.id.mapbasestation);
        if(bsmarker)
        	x.setTitle(R.string.hide_base_station);
        
        return true;
    }

    final private Boolean validSignalStrength(int strength)
    {
    	return (strength > -900 && strength < 900);
    }
    
	private double bslat = 999;
	private double bslon = 999;
    
    /**
     * Activity Handler of incoming messages from service.
     */
	static class IncomingHandler extends Handler {
		private final WeakReference<HomeActivity> mActivity; 

		IncomingHandler(HomeActivity activity) {
	        mActivity = new WeakReference<HomeActivity>(activity);
	    }
		
		@Override
        public void handleMessage(Message msg) {
        	HomeActivity activity = mActivity.get();
            switch (msg.what) {
                case SignalDetectorService.MSG_SIGNAL_UPDATE:
                	if(activity != null)
                		activity.updateSigInfo((signalInfo) msg.obj);
                    break;
                default:
                    super.handleMessage(msg);
            }
        }
    }

    final Messenger mMessenger = new Messenger(new IncomingHandler(this));

    private signalInfo mSignalInfo = null;
    
    public void updateSigInfo(signalInfo signal) {
    	mSignalInfo = signal;
    	updateGui(signal);
    }
    
    private void updateGui(signalInfo signal) {
    	bslat = signal.bslat;
    	bslon = signal.bslon;

		double latitude = signal.latitude;
		double longitude = signal.longitude;
		
		TextView latlon = (TextView) findViewById(R.id.positionLatLon);
		
		latlon.setText(String.format("%3.6f", Math.abs(latitude))+"\u00b0"+(latitude >= 0 ? "N" : "S") + " " +
			String.format("%3.6f", Math.abs(longitude))+"\u00b0"+(longitude >= 0 ? "E" : "W"));

		TextView servingid = (TextView) findViewById(R.id.cellid);
		TextView strength = (TextView) findViewById(R.id.sigstrength);

		TextView strengthLabel = (TextView) findViewById(R.id.sigStrengthLabel);
		TextView bsLabel = (TextView) findViewById(R.id.bsLabel);
		
		TextView cdmaBS = (TextView) findViewById(R.id.cdma_sysinfo);
		TextView cdmaStrength = (TextView) findViewById(R.id.cdmaSigStrength);

		if(!signal.cellID.isEmpty() || signal.physCellID >= 0) {
			if(signal.physCellID >= 0) {
				servingid.setText(signal.cellID + " " + String.valueOf(signal.physCellID));
			} else {
				servingid.setText(signal.cellID);
			}
		} else {
			servingid.setText(R.string.none);
		}
		
		if(validSignalStrength(signal.lteSigStrength)) {
			strength.setText(String.valueOf(signal.lteSigStrength) + "\u2009dBm");
		} else {
			strength.setText(R.string.no_signal);
		}

		if(validSignalStrength(signal.cdmaSigStrength) && signal.phoneType == TelephonyManager.PHONE_TYPE_CDMA) {
			strengthLabel.setText(R.string._1xrtt_signal_strength);
			cdmaStrength.setText(String.valueOf(signal.cdmaSigStrength) + "\u2009dBm");
		} else if (validSignalStrength(signal.gsmSigStrength)) {
			strengthLabel.setText(R.string._2g_3g_signal);
			cdmaStrength.setText(String.valueOf(signal.gsmSigStrength) + "\u2009dBm");
    	} else {
			cdmaStrength.setText(R.string.no_signal);
		}
		
		if(signal.sid >= 0 && signal.nid >= 0 && signal.bsid >= 0 && (signal.phoneType == TelephonyManager.PHONE_TYPE_CDMA)) {
			bsLabel.setText(R.string.cdma_1xrtt_base_station);
			cdmaBS.setText(String.format("SID\u00A0%d, NID\u00A0%d, BSID\u00A0%d", signal.sid, signal.nid, signal.bsid));
		} else if(signal.phoneType == TelephonyManager.PHONE_TYPE_GSM) {
			bsLabel.setText(R.string._2g_3g_tower);

			String bstext = "MNC\u00A0"+signal.operator;
			
			if(signal.lac > 0)
				bstext += ", LAC\u00A0"+String.valueOf(signal.lac);
			
			if(signal.rnc > 0 && signal.rnc != signal.lac)
				bstext += ", RNC\u00A0"+String.valueOf(signal.rnc);
				
			if(signal.cid > 0)
				bstext += ", CID\u00A0"+String.valueOf(signal.cid);
			
			if(signal.psc > 0)
				bstext += ", PSC\u00A0"+String.valueOf(signal.psc);

			cdmaBS.setText(bstext);
		} else {
			cdmaBS.setText(R.string.none);
		}

		if(Math.abs(signal.latitude) <= 200)
			centerMap(signal.latitude, signal.longitude, signal.accuracy, signal.speed);
    	addBsMarker();
    }
    
	private static void centerMap(double latitude, double longitude, double accuracy, double speed) {
		leafletView.loadUrl(String.format("javascript:recenter(%f,%f,%f,%f)",
				latitude, longitude, accuracy, speed));
    }
    
    private void addBsMarker() {
    	if(bsmarker && Math.abs(bslat) <= 90 && Math.abs(bslon) <= 190)
    		leafletView.loadUrl(String.format("javascript:placeMarker(%f,%f)",
    				bslat, bslon));
    	else
    		leafletView.loadUrl("javascript:clearMarker()");
    }
    
    public void toggleBsMarker(MenuItem x) {
    	bsmarker = !bsmarker;
    	
    	if(!bsmarker) {
    		x.setTitle(R.string.show_base_station);
    		leafletView.loadUrl("javascript:clearMarker()");
    	} else {
    		x.setTitle(R.string.hide_base_station);
    		addBsMarker();
    	}
    	invalidateOptionsMenu();
    }    
    
    public void exitApp(MenuItem x) {
    	if(mBound) {
    		unbindService(mConnection);
    		mBound = false;
    	}

    	Intent intent = new Intent(this, SignalDetectorService.class);
        stopService(intent);
    	finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if(mBound) {
        	unbindService(mConnection);
        	mBound = false;
        }
    }

    private String STATE_SHOWBS ="showBSlocation";
    
    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        // Save the user's current game state
        savedInstanceState.putBoolean(STATE_SHOWBS, bsmarker);
        
        // Always call the superclass so it can save the view hierarchy state
        super.onSaveInstanceState(savedInstanceState);
    }
    
    public void onRestoreInstanceState(Bundle savedInstanceState) {
        // Always call the superclass so it can restore the view hierarchy
        super.onRestoreInstanceState(savedInstanceState);
     
        bsmarker = savedInstanceState.getBoolean(STATE_SHOWBS, false);
        invalidateOptionsMenu();
    }
    
    /**
     * Dialog to prompt users to enable GPS on the device.
     */
    public class EnableGpsDialogFragment extends DialogFragment {
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            return new AlertDialog.Builder(getActivity())
                    .setTitle(R.string.enable_gps)
                    .setMessage(R.string.enable_gps_dialog)
                    .setPositiveButton(R.string.enable_gps, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            enableLocationSettings();
                        }
                    })
                    .create();
        }
    }

}
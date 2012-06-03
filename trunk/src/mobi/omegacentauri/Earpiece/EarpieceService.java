package mobi.omegacentauri.Earpiece;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import mobi.omegacentauri.Earpiece.R;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.location.Address;
import android.media.AudioManager;
import android.media.audiofx.Equalizer;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.util.Log;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnFocusChangeListener;
import android.view.Gravity;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

public class EarpieceService extends Service {
	
	private final Messenger messenger = new Messenger(new IncomingHandler());
	private SharedPreferences options;
	private Equalizer eq;
	private short bands;
	private short rangeLow;
	private short rangeHigh;
	private AudioManager am;
	
	public class IncomingHandler extends Handler {
		public static final int MSG_OFF = 0;
		public static final int MSG_ON = 1;
		public static final int MSG_RELOAD_SETTINGS = 2;
		
		@Override 
		public void handleMessage(Message m) {
			Earpiece.log("Message: "+m.what);
			switch(m.what) {
			case MSG_RELOAD_SETTINGS:
				loadEqualizer();
				break;
			default:
				super.handleMessage(m);
			}
		}
	}
	
	@Override
	public IBinder onBind(Intent arg0) {
		return messenger.getBinder();
	}
	
    void setupEqualizer() {
    	try {
	        if (Build.VERSION.SDK_INT<9)
	        	throw(new UnsupportedOperationException("SDK<9"));
	
	        eq = new Equalizer(0, 0);
			bands = eq.getNumberOfBands();
			
			rangeLow = eq.getBandLevelRange()[0];
			rangeHigh = eq.getBandLevelRange()[1];
    	}
    	catch(UnsupportedOperationException e) {
			Log.e("EarpieceService", "Equalizer: "+e.toString());
			eq = null;
    	}
	}
    
    private void loadEqualizer() {
    	if (eq == null)
    		return;
    	
    	short boostValue = (short)options.getInt(Options.PREF_BOOST, 0);
    	
    	Earpiece.log("boost to "+boostValue);
    	if (boostValue < 0)
    		boostValue = 0;
    	if (boostValue > rangeHigh)
    		boostValue = rangeHigh;
    	
    	for (short i=0; i<bands; i++) {
    		eq.setBandLevel(i, (short)boostValue);
    	}
    	
    	eq.setEnabled(boostValue > 0);
    }
	
	@Override
	public void onCreate() {
		Earpiece.log("Creating service");
		options = PreferenceManager.getDefaultSharedPreferences(this);
		
        am = (AudioManager)getSystemService(AUDIO_SERVICE);

        setupEqualizer();
        loadEqualizer();

        Notification n = new Notification(
				R.drawable.equalizer,
				"Earpiece", 
				System.currentTimeMillis());
		Intent i = new Intent(this, Earpiece.class);
		i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		n.flags = Notification.FLAG_NO_CLEAR | Notification.FLAG_ONGOING_EVENT; 
		n.setLatestEventInfo(this, "Earpiece", "Equalizer is on", 
				PendingIntent.getActivity(this, 0, i, 0));
		Earpiece.log("notify from service "+n.toString());

		startForeground(Earpiece.NOTIFICATION_ID, n);
		
	}
	
	@Override
	public void onDestroy() {
		if (eq != null) {
			eq.setEnabled(false);
			eq = null;
		}
		Earpiece.log("Destroying service, destroying notification =" + (Options.getNotify(options) != Options.NOTIFY_ALWAYS));
		stopForeground(Options.getNotify(options) != Options.NOTIFY_ALWAYS);
	}
	
	@Override
	public void onStart(Intent intent, int flags) {
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		onStart(intent, flags);
		return START_STICKY;
	}

}

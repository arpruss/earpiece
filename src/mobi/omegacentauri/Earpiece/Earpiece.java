package mobi.omegacentauri.Earpiece;

import mobi.omegacentauri.Earpiece.R;
import mobi.omegacentauri.Earpiece.EarpieceService.IncomingHandler;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.audiofx.Equalizer;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.SeekBar;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class Earpiece extends Activity implements ServiceConnection {
	private static boolean DEBUG = true;
	AudioManager am;
	CheckBox earpieceBox;
	CheckBox equalizerBox;
	Equalizer eq;
	private SharedPreferences options;
	private boolean equalizerActive;
	private Messenger messenger;
	private NotificationManager notificationManager;
	private short bands;
	private short rangeLow;
	private short rangeHigh;
	private int SLIDER_MAX = 10000;
	private SeekBar boostBar;
	private View equalizerScroll;
	private LinearLayout equalizerInside;
	
	static final int NOTIFICATION_ID = 1;

	public static void log(String s) {
		if (DEBUG )
			Log.v("Earpiece", s);
	}
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
		this.requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.main);

		options = PreferenceManager.getDefaultSharedPreferences(this);
		am = (AudioManager)getSystemService(AUDIO_SERVICE);
    	notificationManager = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);

    	boostBar = (SeekBar)findViewById(R.id.boost);
        earpieceBox = (CheckBox)findViewById(R.id.earpiece);
        equalizerBox = (CheckBox)findViewById(R.id.equalizer);
        equalizerScroll = (ScrollView)findViewById(R.id.equalizer_scroll);
        equalizerInside = (LinearLayout)findViewById(R.id.equalizer_inside);

    }
    
    void updateEqualizer(boolean value) {
		
		if (value) {
			restartService(true);
			equalizerScroll.setVisibility(View.VISIBLE);
    	}
		else {
			stopService();
			equalizerScroll.setVisibility(View.GONE);			
		}

    }
    
    private void updateBoostText(int progress) {
		String t = "Boost: "+((progress*100+SLIDER_MAX/2)/SLIDER_MAX)+"%"; 
		((TextView)findViewById(R.id.boost_value)).setText(t);
    }

    void setupEqualizer() throws UnsupportedOperationException {
        if (Build.VERSION.SDK_INT<9)
        	throw(new UnsupportedOperationException("SDK<9"));

        eq = new Equalizer(0, 0);
		bands = eq.getNumberOfBands();
		
		rangeLow = eq.getBandLevelRange()[0];
		rangeHigh = eq.getBandLevelRange()[1];
		
		equalizerBox.setVisibility(View.VISIBLE);
		
		boolean active = options.getBoolean(Options.PREF_EQUALIZER_ACTIVE, false); 
		
    	equalizerBox.setOnCheckedChangeListener(new OnCheckedChangeListener(){

    		@Override
    		public void onCheckedChanged(CompoundButton button, boolean value) {
    			options.edit().putBoolean(Options.PREF_EQUALIZER_ACTIVE, value).commit();
    			updateEqualizer(value);
    		}});
    	
    	equalizerBox.setChecked(active);
    	updateEqualizer(active);
    	
		boostBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){

			@Override
			public void onProgressChanged(SeekBar seekBar, int progress,
					boolean fromUser) {
				if (fromUser) {
					options.edit().putInt(Options.PREF_BOOST, fromSlider(progress,0,rangeHigh)).commit();
					sendMessage(IncomingHandler.MSG_RELOAD_SETTINGS, 0, 0);
				}
				updateBoostText(progress);
			}

			@Override
			public void onStartTrackingTouch(SeekBar seekBar) {
			}

			@Override
			public void onStopTrackingTouch(SeekBar seekBar) {
			}
		});

		int progress = toSlider(options.getInt(Options.PREF_BOOST, 0), 0,
				rangeHigh);
		boostBar.setProgress(progress);
		updateBoostText(progress);
    }
    
    private int fromSlider(int value, int min, int max) {
    	return (min * (SLIDER_MAX - value) + max * value + SLIDER_MAX/2) / SLIDER_MAX;
    }

    private int toSlider(int value, int min, int max) {
    	return ((value-min)*SLIDER_MAX + (max-min)/2) / (max-min);
    }

    
    @Override
    public void onResume() {
    	equalizerActive = options.getBoolean(Options.PREF_EQUALIZER_ACTIVE, false);
    	
    	super.onResume();
    	try {
    		setupEqualizer();
    	}
    	catch(UnsupportedOperationException e) {
    		Log.e("Earpiece", "Equalizer: "+e.toString());
    		eq = null;
    		equalizerBox.setVisibility(View.GONE);
    		equalizerScroll.setVisibility(View.GONE);
    	}

    	earpieceBox.setChecked(getEarpieceValue());		
    	earpieceBox.setOnCheckedChangeListener(new OnCheckedChangeListener(){

    		@Override
    		public void onCheckedChanged(CompoundButton button, boolean value) {
    			earpiece(value);
    		}});    	
    }
    
    @Override
    public void onPause() {
    	super.onPause();

    	if (messenger != null) {
			log("unbind");
			unbindService(this);
		}

    }
    
    boolean getEarpieceValue() {
    	return am.getMode() == AudioManager.MODE_IN_CALL;
    }
    
    void earpiece(boolean value) {
		am.setSpeakerphoneOn(false);
		if (value) {
			am.setMode(AudioManager.MODE_IN_CALL);
			am.setRouting(AudioManager.MODE_NORMAL, AudioManager.ROUTE_EARPIECE, 
					AudioManager.ROUTE_ALL);
		}
		else {
			am.setMode(AudioManager.MODE_NORMAL);
			am.setRouting(AudioManager.MODE_NORMAL, AudioManager.ROUTE_SPEAKER, 
					AudioManager.ROUTE_ALL);
		}
    }


	public static void setNotification(Context c, NotificationManager nm, boolean active) {
		Notification n = new Notification(
				active?R.drawable.equalizer:R.drawable.equalizeroff,
				"Earpiece", 
				System.currentTimeMillis());
		Intent i = new Intent(c, Earpiece.class);		
		i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		n.flags = Notification.FLAG_NO_CLEAR | Notification.FLAG_ONGOING_EVENT; 
		n.setLatestEventInfo(c, "Earpiece", "Equalizer is "+(active?"on":"off"), 
				PendingIntent.getActivity(c, 0, i, 0));
		nm.notify(NOTIFICATION_ID, n);
		log("notify "+n.toString());
	}
	
	private void updateNotification() {
		updateNotification(this, options, notificationManager, equalizerActive);
	}
	
	public static void updateNotification(Context c, 
			SharedPreferences options, NotificationManager nm, boolean active) {
		log("notify "+Options.getNotify(options));
		switch(Options.getNotify(options)) {
		case Options.NOTIFY_NEVER:
			nm.cancelAll();
			break;
		case Options.NOTIFY_AUTO:
			if (active)
				setNotification(c, nm, active);
			else {
				log("trying to cancel notification");
				nm.cancelAll();
			}
			break;
		case Options.NOTIFY_ALWAYS:
			setNotification(c, nm, active);
			break;
		}
	}

	void stopService() {
		log("stop service");
		stopService(new Intent(this, EarpieceService.class));
	}
	
	void saveSettings() {
	}
	
	void bind() {
		log("bind");
		Intent i = new Intent(this, EarpieceService.class);
		bindService(i, this, 0);
	}
	
	void restartService(boolean bind) {
		stopService();
		saveSettings();		
		log("starting service");
		Intent i = new Intent(this, EarpieceService.class);
		startService(i);
		if (bind) {
			bind();
		}
	}
	
	void setActive(boolean value, boolean bind) {
		SharedPreferences.Editor ed = options.edit();
		ed.putBoolean(Options.PREF_EQUALIZER_ACTIVE, value);
		ed.commit();
		if (value) {
			restartService(bind);
		}
		else {
			stopService();
		}
		equalizerActive = value;
		updateNotification();
	}
	
	public void sendMessage(int n, int arg1, int arg2) {
		if (messenger == null) 
			return;
		
		try {
			log("message "+n+" "+arg1+" "+arg2);
			messenger.send(Message.obtain(null, n, arg1, arg2));
		} catch (RemoteException e) {
		}
	}
	
	@Override
	public void onServiceConnected(ComponentName classname, IBinder service) {
		log("connected");
		messenger = new Messenger(service);
//		try {
//			messenger.send(Message.obtain(null, IncomingHandler.MSG_ON, 0, 0));
//		} catch (RemoteException e) {
//		}
	}

	@Override
	public void onServiceDisconnected(ComponentName name) {
		log("disconnected"); 
//		stopService(new Intent(this, EarpieceService.class));
		messenger = null;		
	}
}
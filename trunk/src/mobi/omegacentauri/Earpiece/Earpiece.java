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
	CheckBox earpieceBox;
	CheckBox proximityBox;
	CheckBox equalizerBox;
	private SharedPreferences options;
	private Messenger messenger;
	private NotificationManager notificationManager;
	private int SLIDER_MAX = 10000;
	private SeekBar boostBar;
	private View equalizerScroll;
	private LinearLayout equalizerInside;
	private Settings settings;
	
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
		settings = new Settings(this);
		
    	notificationManager = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);

    	boostBar = (SeekBar)findViewById(R.id.boost);
        earpieceBox = (CheckBox)findViewById(R.id.earpiece);
        proximityBox = (CheckBox)findViewById(R.id.proximity);
        equalizerBox = (CheckBox)findViewById(R.id.equalizer);
        equalizerScroll = (ScrollView)findViewById(R.id.equalizer_scroll);
        equalizerInside = (LinearLayout)findViewById(R.id.equalizer_inside);

    }
    
    void updateService(boolean value) {		
		if (value) {
			restartService(true);
    	}
		else {
			stopService();
		}
    }
    
    void updateService() {
    	updateService(settings.needService());
    }
    
    private void updateBoostText(int progress) {
		String t = "Boost: "+((progress*100+SLIDER_MAX/2)/SLIDER_MAX)+"%"; 
		((TextView)findViewById(R.id.boost_value)).setText(t);
    }
    
    private void updateEqualizerDisplay() {
    	if (settings.isEqualizerActive())
    		equalizerScroll.setVisibility(View.VISIBLE);
    	else
    		equalizerScroll.setVisibility(View.GONE);    		
    }

    void setupEqualizer() {
    	log("setupEqualizer");

    	if (!settings.haveEqualizer()) {
        	log("no equalizer");
    		equalizerBox.setVisibility(View.GONE);
    		return;
    	}
    	
		equalizerBox.setVisibility(View.VISIBLE);
		
		
    	equalizerBox.setOnCheckedChangeListener(new OnCheckedChangeListener(){

    		@Override
    		public void onCheckedChanged(CompoundButton button, boolean value) {
    			settings.equalizerActive = value;
    			settings.save(options);
    			updateEqualizerDisplay();
    			updateService();
    		}});
    	
    	equalizerBox.setChecked(settings.equalizerActive);
    	
		boostBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){

			@Override
			public void onProgressChanged(SeekBar seekBar, int progress,
					boolean fromUser) {
				if (fromUser) {
					settings.boostValue = fromSlider(progress,0,settings.rangeHigh);
					settings.save(options);
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
				settings.rangeHigh);
		boostBar.setProgress(progress);
		updateEqualizerDisplay();
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
    	super.onResume();

    	settings.load(options);
    	settings.setEarpiece();

    	setupEqualizer();
    	updateService();

    	earpieceBox.setChecked(settings.earpieceActive);		
    	earpieceBox.setOnCheckedChangeListener(new OnCheckedChangeListener(){

    		@Override
    		public void onCheckedChanged(CompoundButton button, boolean value) {
    			settings.earpieceActive = value;
    			settings.save(options);
    			settings.setEarpiece();
    			if (value && settings.haveProximity()) {
    				proximityBox.setVisibility(View.VISIBLE);
    			}
    			else {
    				proximityBox.setVisibility(View.INVISIBLE);
    			}
    			updateService();
    		}});    	

    	proximityBox.setChecked(settings.proximity);		
    	proximityBox.setOnCheckedChangeListener(new OnCheckedChangeListener(){

    		@Override
    		public void onCheckedChanged(CompoundButton button, boolean value) {
    			settings.proximity = value;
    			settings.save(options);
    			log("proximity="+value+" needService()"+settings.needService());
    			updateService();
    		}});    	
    }
    
    @Override
    public void onPause() {
    	super.onPause();
    	
    	if (messenger != null) {
			log("unbind");
			unbindService(this);
			messenger = null;
		}

    }
    
	public static void setNotification(Context c, NotificationManager nm, Settings s) {
		Notification n = new Notification(
				s.needService()?R.drawable.equalizer:R.drawable.equalizeroff,
				"Earpiece", 
				System.currentTimeMillis());
		Intent i = new Intent(c, Earpiece.class);		
		i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		n.flags = Notification.FLAG_NO_CLEAR | Notification.FLAG_ONGOING_EVENT; 
		n.setLatestEventInfo(c, "Earpiece", s.describe(), 
				PendingIntent.getActivity(c, 0, i, 0));
		nm.notify(NOTIFICATION_ID, n);
		log("notify "+n.toString());
	}
	
//	private void updateNotification() {
//		updateNotification(this, options, notificationManager, 
//				settings);
//	}
	
	public static void updateNotification(Context c, 
			SharedPreferences options, NotificationManager nm, Settings s) {
		log("notify "+Options.getNotify(options));
		switch(Options.getNotify(options)) {
		case Options.NOTIFY_NEVER:
			nm.cancelAll();
			break;
		case Options.NOTIFY_AUTO:
			if (s.needService())
				setNotification(c, nm, s);
			else {
				log("trying to cancel notification");
				nm.cancelAll();
			}
			break;
		case Options.NOTIFY_ALWAYS:
			setNotification(c, nm, s);
			break;
		}
	}

	void stopService() {
		log("stop service");
		if (messenger != null) {
			unbindService(this);
			messenger = null;
		}
		
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
	
//	void setActive(boolean value, boolean bind) {
//		SharedPreferences.Editor ed = options.edit();
//		ed.putBoolean(Options.PREF_EQUALIZER_ACTIVE, value);
//		ed.commit();
//		if (value) {
//			restartService(bind);
//		}
//		else {
//			stopService();
//		}
//		equalizerActive = value;
//		updateNotification();
//	}
	
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

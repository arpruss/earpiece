package mobi.omegacentauri.Earpiece;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import mobi.omegacentauri.Earpiece.R;
import mobi.omegacentauri.Earpiece.EarpieceService.IncomingHandler;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.media.AudioManager;
import android.media.audiofx.Equalizer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.text.Html;
import android.util.Log;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.view.ViewConfiguration;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.SeekBar;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class Earpiece extends Activity implements ServiceConnection {
	private static boolean DEBUG = true;
	static final String MARKET = "Market";
	CheckBox earpieceBox;
	CheckBox proximityBox;
	CheckBox equalizerBox;
	private SharedPreferences options;
	private Messenger messenger;
	private int SLIDER_MAX = 10000;
	private SeekBar boostBar;
	private View equalizerContainer;
	private Settings settings;
//	private TextView ad;
	private int versionCode;
	private LinearLayout main;
	
	static final int NOTIFICATION_ID = 1;

	public static void log(String s) {
		if (DEBUG )
			Log.v("Earpiece", s);
	}
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
		try {
			versionCode = getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
		} catch (NameNotFoundException e) {
			versionCode = 0;
		} 
		
		setVolumeControlStream(AudioManager.STREAM_MUSIC);
		
//		Equalizer e2 = new Equalizer(Integer.MIN_VALUE, 79) ;
//		Log.v("CurrentPreset", "e2:"+e2.getProperties()+" "+e2.hasControl());
//		Equalizer e = new Equalizer(Integer.MAX_VALUE, 79) ;
//		Log.v("CurrentPreset", "e:"+e.getProperties()+" "+e.hasControl());
//		Log.v("CurrentPreset", "e2:"+e2.getProperties()+" "+e2.hasControl());
//		e.usePreset((short)0);
//		e.setBandLevel((short)0, (short)-1500) ;
//		e.setBandLevel((short)1, (short)-1500);
//		e.setBandLevel((short)2, (short)-1500);
//		e.setBandLevel((short)3, (short)-1500);
//		e.setBandLevel((short)4, (short)-1500);
//		e.setEnabled(true);
//		Log.v("CurrentPreset", ""+e.getProperties());
		
		this.requestWindowFeature(Window.FEATURE_NO_TITLE);
		main = (LinearLayout)getLayoutInflater().inflate(R.layout.main, null);
		setContentView(main);
		
		options = PreferenceManager.getDefaultSharedPreferences(this);
		settings = new Settings(this, false);
		
    	boostBar = (SeekBar)findViewById(R.id.boost);
        earpieceBox = (CheckBox)findViewById(R.id.earpiece);
        
        earpieceBox.setVisibility(settings.haveTelephony() ? 
        		   View.VISIBLE : View.GONE); 
        
        proximityBox = (CheckBox)findViewById(R.id.proximity);
        proximityBox.setVisibility(
        		settings.haveProximity()? View.VISIBLE : View.GONE);
        equalizerBox = (CheckBox)findViewById(R.id.equalizer);
        equalizerContainer = (View)findViewById(R.id.equalizer_inside);
        
        findViewById(R.id.more).setVisibility(
        		(false&&settings.hasMenuKey()) ?
        				View.GONE : View.VISIBLE);
//        
//        ad = (TextView)findViewById(R.id.ad);
//        
//        ad.setOnClickListener(new OnClickListener(){
//
//			@Override
//			public void onClick(View arg0) {
//				market();
//			}});
//        
        versionUpdate();
    }
    
    void market() {
    	Intent i = new Intent(Intent.ACTION_VIEW);
    	i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    	if (MARKET.contains("arket"))
    		i.setData(Uri.parse("market://search?q=pub:\"Omega Centauri Software\""));
    	else
    		i.setData(Uri.parse("http://www.amazon.com/gp/mas/dl/android?p=mobi.omegacentauri.ScreenDim.Full&showAll=1"));            		
    	startActivity(i);    	
    }
    
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.main, menu);
		
		return true;
	}

	private void message(String title, String msg) {
		AlertDialog alertDialog = new AlertDialog.Builder(this).create();

		alertDialog.setTitle(title);
		alertDialog.setMessage(Html.fromHtml(msg));
		alertDialog.setButton(DialogInterface.BUTTON_POSITIVE, 
				"OK", 
				new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {} });
		alertDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
			public void onCancel(DialogInterface dialog) {} });
		alertDialog.show();

	}
	
	private void show(String title, String filename) {
		message(title, getAssetFile(filename));
	}
	
	private void warning() {
		AlertDialog alertDialog = new AlertDialog.Builder(this).create();

		settings.boostValue = 0;
		settings.saveBoost(options);
		settings.disableEqualizer();
		boostBar.setProgress(0);
		reloadSettings();

		alertDialog.setTitle("Warning");
		alertDialog.setMessage(Html.fromHtml(getAssetFile("warning.html")));
		alertDialog.setButton(DialogInterface.BUTTON_POSITIVE, 
				"Yes", 
				new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
				options.edit().putInt(Options.PREF_WARNED_LAST_VERSION, versionCode).commit();
			} });
		alertDialog.setButton(DialogInterface.BUTTON_NEGATIVE, 
				"No", 
				new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
				Earpiece.this.finish();
			} });
		alertDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
			public void onCancel(DialogInterface dialog) {
			Earpiece.this.finish();
			} });
		alertDialog.show();

	}
	
	private void versionUpdate() {
		log("version "+versionCode);
		
		if (options.getInt(Options.PREF_LAST_VERSION, 0) != versionCode) {
			options.edit().putInt(Options.PREF_LAST_VERSION, versionCode).commit();
			show("Change log", "changelog.html");
		}
		if (options.getInt(Options.PREF_WARNED_LAST_VERSION, 0) != versionCode) {
			warning();
		}			
	}
	

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch(item.getItemId()) {
		case R.id.options:
			Intent i = new Intent(this, Options.class);
			startActivity(i);
			return true;
		case R.id.change_log:
			show("Change log", "changelog.html");
			return true;
		case R.id.help:
			show("Questions and answers", "help.html");
			return true;
		case R.id.please_buy:
			market();
			return true;
		default:
			return false;
		}
	}

	void updateService(boolean value) {		
		if (value) {
			restartService(true);
    	}
		else {
			stopService();
	    	updateNotification();
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
    		equalizerContainer.setVisibility(View.VISIBLE);
    	else
    		equalizerContainer.setVisibility(View.GONE);    		
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
					reloadSettings();
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
    
    protected void reloadSettings() {
		sendMessage(IncomingHandler.MSG_RELOAD_SETTINGS, 0, 0);
	}

	private int fromSlider(int value, int min, int max) {
    	return (min * (SLIDER_MAX - value) + max * value + SLIDER_MAX/2) / SLIDER_MAX;
    }

    private int toSlider(int value, int min, int max) {
    	return ((value-min)*SLIDER_MAX + (max-min)/2) / (max-min);
    }

    void resize() {
    	LinearLayout ll = main;
    	FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams)ll.getLayoutParams();

    	int h = getWindowManager().getDefaultDisplay().getHeight();
    	int w = getWindowManager().getDefaultDisplay().getWidth();
    	
    	int min = h<w ? h : w;
    	
    	int desiredMin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 256, getResources().getDisplayMetrics());
    	lp.width = min * 4 / 5;
    	if (lp.width < desiredMin) {
    		lp.width = desiredMin;
    		if (w < desiredMin)
    			 lp.width = w;
    	}
    	
//    	if (w>h) {
//    		lp.setMargins((w-h)/2,0,(w-h)/2,0);
//    	}
//    	else {
//    		lp.setMargins(0,0,0,0);
//    	}
		ll.setLayoutParams(lp);
    }
    
    
    @Override
    public void onResume() {
    	super.onResume();
    	
    	resize();

    	settings.load(options);

    	boostBar.setMax(SLIDER_MAX * settings.maximumBoostPercent / 100);
    	if (settings.boostValue > settings.rangeHigh * settings.maximumBoostPercent / 100) {
    		settings.boostValue = settings.rangeHigh * settings.maximumBoostPercent / 100;
    		settings.save(options);
    	}

    	settings.setEarpiece();

    	setupEqualizer();		
		updateService();
		updateNotification();

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

		if (settings.earpieceActive && settings.haveProximity()) {
			proximityBox.setVisibility(View.VISIBLE);
		}
		else {
			log("hide proximity box");
			proximityBox.setVisibility(View.INVISIBLE);
		}
		
//		ad.setVisibility(havePaidApp() ? View.GONE : View.VISIBLE);
		
    }
    
//    private boolean have(String p) {
//    	try {
//			return getPackageManager().getPackageInfo(p, 0) != null;
//		} catch (NameNotFoundException e) {
//			return false;
//		}    	
//    }
//    
//    private boolean havePaidApp() {
//    	return have("mobi.omegacentauri.ScreenDim.Full") ||
//    		have("mobi.pruss.force2sd") ||
//    		have("mobi.omegacentauri.LunarMap.HD");
//	}

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
				s.somethingOn()?R.drawable.equalizer:R.drawable.equalizeroff,
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
	
	private void updateNotification() {
		updateNotification(this, options, 
				(NotificationManager)getSystemService(NOTIFICATION_SERVICE), 
				settings);
	}
	
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

	static private String getStreamFile(InputStream stream) {
		BufferedReader reader;
		try {
			reader = new BufferedReader(new InputStreamReader(stream));

			String text = "";
			String line;
			while (null != (line=reader.readLine()))
				text = text + line;
			return text;
		} catch (IOException e) {
			// TODO Auto-generated catch block
			return "";
		}
	}
	
	public String getAssetFile(String assetName) {
		try {
			return getStreamFile(getAssets().open(assetName));
		} catch (IOException e) {
			// TODO Auto-generated catch block
			return "";
		}
	}
	
	public void optionsClick(View v) {
		openOptionsMenu();		
	}	
}

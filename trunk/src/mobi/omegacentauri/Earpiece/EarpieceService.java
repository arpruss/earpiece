package mobi.omegacentauri.Earpiece;

import android.app.KeyguardManager;
import android.app.KeyguardManager.KeyguardLock;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.preference.PreferenceManager;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;

public class EarpieceService extends Service implements SensorEventListener   
{	
	private final Messenger messenger = new Messenger(new IncomingHandler());
	private static final int PROXIMITY_SCREEN_OFF_WAKE_LOCK = 32;
	private SharedPreferences options;
	private Settings settings;
	private PowerManager pm;
	private KeyguardManager km;
	private TelephonyManager tm;
	private WakeLock wakeLock = null;
	private KeyguardLock guardLock = null;
	private boolean closeToPhoneValid = false;
	private boolean closeToPhone = false;
	private Sensor proximitySensor = null;
	private PhoneStateListener phoneStateListener;
	private long t0;
	protected boolean phoneOn = false;
	private static final String PROXIMITY_TAG = "mobi.omegacentauri.Earpiece.EarpieceService.proximity";
	private static final String GUARD_TAG = "mobi.omegacentauri.Earpiece.EarpieceService.guard";
 	
	public class IncomingHandler extends Handler {
		public static final int MSG_OFF = 0;
		public static final int MSG_ON = 1;
		public static final int MSG_RELOAD_SETTINGS = 2;
		
		@Override 
		public void handleMessage(Message m) {
			Earpiece.log("Message: "+m.what);
			switch(m.what) {
			case MSG_RELOAD_SETTINGS:
				settings.load(options);
				settings.setEqualizer();
				updateProximity();
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
	
	@Override
	public void onCreate() {
		t0 = System.currentTimeMillis();
		Earpiece.log("create service "+t0);
		options = PreferenceManager.getDefaultSharedPreferences(this);
	    pm = (PowerManager)getSystemService(POWER_SERVICE);
	    km = (KeyguardManager)getSystemService(KEYGUARD_SERVICE);
		settings = new Settings(this);
		settings.load(options);

		if (settings.haveTelephony())
	    	tm = (TelephonyManager)getSystemService(TELEPHONY_SERVICE);
		else
			tm = null;
		

		if (Options.getNotify(options) != Options.NOTIFY_NEVER) {
	        Notification n = new Notification(
					R.drawable.equalizer,
					"Earpiece", 
					System.currentTimeMillis());
			Intent i = new Intent(this, Earpiece.class);
			i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			n.flags = Notification.FLAG_NO_CLEAR | Notification.FLAG_ONGOING_EVENT; 
			n.setLatestEventInfo(this, "Earpiece", 
					settings.describe(), 
					PendingIntent.getActivity(this, 0, i, 0));
			Earpiece.log("notify from service "+n.toString());
	
			startForeground(Earpiece.NOTIFICATION_ID, n);
		}
		else {			
		}
		
		if (settings.isEqualizerActive())
			settings.setEqualizer();
		else
			settings.disableEqualizer();
		
		updateProximity();
		
		if (tm != null) {
			phoneStateListener = new PhoneStateListener() {
				@Override
				public
				void onCallStateChanged(int state, String incomingNumber) {
					Earpiece.log("phone state:" + state);
					phoneOn = ( state == TelephonyManager.CALL_STATE_OFFHOOK );
					closeToPhoneValid = false;
					if (phoneOn) {
						if (proximitySensor == null) {
							proximitySensor = settings.proximitySensor;
							settings.sensorManager.registerListener(EarpieceService.this, proximitySensor, SensorManager.SENSOR_DELAY_NORMAL);
							Earpiece.log("Registering proximity sensor");
						}
					}
					else {
						if (proximitySensor != null) {
							disableProximitySensor();
						}
					}
					updateSpeakerPhone();
				}
			};
		}
		
		updateAutoSpeakerPhone();
		if (options.getBoolean(Options.PREF_DISABLE_KEYGUARD, false)) {
			enableDisableKeyguard();
		}
	}
	
	private void disableProximitySensor() {
		if (proximitySensor == null)
			return;
		
		Earpiece.log("Unregistering proximity sensor");		
		settings.sensorManager.unregisterListener(EarpieceService.this, proximitySensor);
		proximitySensor = null;
	}
	
	private void updateSpeakerPhone() {
		if (settings.isAutoSpeakerPhoneActive()) {
			Earpiece.log("updateSpeakerPhone "+phoneOn+" "+closeToPhone);
			Earpiece.log("Speaker phone "+(phoneOn && !closeToPhone));
			if (closeToPhoneValid)
				settings.audioManager.setSpeakerphoneOn(phoneOn && !closeToPhone);
			updateProximity();
		}
	}
	
	private void updateAutoSpeakerPhone() {		
		if (settings.isAutoSpeakerPhoneActive()) {
			Earpiece.log("Auto speaker phone mode on");
			tm.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);
		}
		else {
			Earpiece.log("Auto speaker phone mode off");
			tm.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE);
		}
	}
	
	@Override
	public void onDestroy() {
		Earpiece.log("stop service "+t0);

		settings.load(options);
//		if (settings.isEqualizerActive()) {
			Earpiece.log("disabling equalizer");
			settings.disableEqualizer();
//		}
		disableProximity();
		disableDisableKeyguard();
		disableProximitySensor();
		if (tm != null && phoneStateListener != null) {
			tm.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE);
		}
		
		if(Options.getNotify(options) != Options.NOTIFY_NEVER)
			stopForeground(true);
	}
	
	@Override
	public void onStart(Intent intent, int flags) {
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		onStart(intent, flags);
		return START_STICKY;
	}
	
	private void activateProximity() {
		if (wakeLock == null) {
			Earpiece.log("activating proximity "+t0);
			wakeLock = pm.newWakeLock(PROXIMITY_SCREEN_OFF_WAKE_LOCK, 
					PROXIMITY_TAG);
			wakeLock.setReferenceCounted(false);
			wakeLock.acquire();
		}
		enableDisableKeyguard();
	}
	
	private void disableDisableKeyguard() {
		if (null != guardLock) {
			guardLock.reenableKeyguard();
			guardLock = null;
		}
	}
	
	private void enableDisableKeyguard() {
		if (guardLock == null) {
			guardLock = km.newKeyguardLock(GUARD_TAG);
			guardLock.disableKeyguard();
		}
	}
	
	private void disableProximity() {
//		if (null == wakeLock) {
//			wakeLock = pm.newWakeLock(PROXIMITY_SCREEN_OFF_WAKE_LOCK, 
//					PROXIMITY_TAG);
//			wakeLock.setReferenceCounted(false);
//		}
		if (null != wakeLock) {
			Earpiece.log("disabling proximity "+t0);
			wakeLock.release();
			wakeLock = null;
		}

		if (! settings.disableKeyguardActive) 
			disableDisableKeyguard();
	}
	
	private void updateProximity() {
		if (settings.isProximityActive() || 
				(settings.isAutoSpeakerPhoneActive() && phoneOn))
			activateProximity();
		else
			disableProximity();		
	}

	@Override
	public void onAccuracyChanged(Sensor arg0, int arg1) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onSensorChanged(SensorEvent event) {
		if (event.sensor == settings.proximitySensor) {
			closeToPhone = event.values[0] < settings.proximitySensor.getMaximumRange();
			closeToPhoneValid = true;
			phoneOn = (tm.getCallState() == TelephonyManager.CALL_STATE_OFFHOOK);
			Earpiece.log("onSensorChanged, phone = "+tm.getCallState());
			updateSpeakerPhone();
		}
	}
}

package mobi.omegacentauri.Earpiece;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import mobi.omegacentauri.Earpiece.EarpieceService.ScreenOnOffReceiver;

import android.annotation.SuppressLint;
import android.app.KeyguardManager;
import android.app.KeyguardManager.KeyguardLock;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.preference.PreferenceManager;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.EventLog;
import android.util.Log;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;

public class EarpieceService extends Service implements SensorEventListener   
{	
	private final Messenger messenger = new Messenger(new IncomingHandler());
	private boolean quietedCamera = false;
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
	private Process logProcess = null;
	private boolean interruptReader = false;
	private Thread logThread = null;
	private static final String PROXIMITY_TAG = "mobi.omegacentauri.Earpiece.EarpieceService.proximity";
	private static final String GUARD_TAG = "mobi.omegacentauri.Earpiece.EarpieceService.guard";
	private Handler unquietCameraHandler = new Handler();
	private ScreenOnOffReceiver screenOnOffReceiver;	
	

	private final Runnable unquietCameraRunnable = new Runnable() {
		@Override
		public void run() {
			if (quietedCamera) {
				settings.setEqualizer();
				settings.setEarpiece();
				quietedCamera = false;
				Earpiece.log("unquieting camera...");
			}			
		}
			
	};
 	

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
	
	@SuppressLint("NewApi")
	@Override
	public void onCreate() {
		t0 = System.currentTimeMillis();
		Earpiece.log("create service "+t0);
		options = PreferenceManager.getDefaultSharedPreferences(this);
	    pm = (PowerManager)getSystemService(POWER_SERVICE);
	    km = (KeyguardManager)getSystemService(KEYGUARD_SERVICE);
		settings = new Settings(this, true);
		settings.load(options);
		if (settings.needScreenOnOffReceiver()) {
			screenOnOffReceiver = new ScreenOnOffReceiver();
			IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_ON);
			filter.addAction(Intent.ACTION_SCREEN_OFF);
			registerReceiver(screenOnOffReceiver, filter);
		}
		else {
			screenOnOffReceiver = null;
		}

		if (settings.haveTelephony())
	    	tm = (TelephonyManager)getSystemService(TELEPHONY_SERVICE);
		else
			tm = null;
		

//		if (Options.getNotify(options) != Options.NOTIFY_NEVER) {
	        Notification n = new Notification(
					Options.getNotify(options) != Options.NOTIFY_NEVER ? R.drawable.equalizer : 0,
					"Earpiece", 
					System.currentTimeMillis());
			Intent i = new Intent(this, Earpiece.class);
			i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			n.flags = Notification.FLAG_NO_CLEAR | Notification.FLAG_ONGOING_EVENT; 
			n.setLatestEventInfo(this, "Earpiece", 
					settings.describe(), 
					PendingIntent.getActivity(this, 0, i, 0));
//			Earpiece.log("notify from service "+n.toString());
	
			if (Build.VERSION.SDK_INT >= 5)
				startForeground(Earpiece.NOTIFICATION_ID, n);
//		}
//		else {			
//		}
		
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
					closeToPhoneValid = true; // false
					closeToPhone = false;     // remove?
					if (phoneOn) {
						if (proximitySensor == null) {
							proximitySensor = settings.proximitySensor;
							settings.sensorManager.registerListener(EarpieceService.this, proximitySensor, /*SensorManager.SENSOR_DELAY_NORMAL*/
									SensorManager.SENSOR_DELAY_UI);
							Earpiece.log("Registering proximity sensor");
						}
					}
					else {
						if (proximitySensor != null) {
							disableProximitySensor();
							Earpiece.log("Closing proximity sensor");
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

		if (settings.quietCamera) {
	        Runnable logRunnable = new Runnable(){
	        	@Override
	        	public void run() {
	                interruptReader = false;
					monitorLog();
				}};  
			logThread = new Thread(logRunnable);
			
			logThread.start();
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
		if (tm == null)
			return;
             
		if (settings.isAutoSpeakerPhoneActive()) {
			Earpiece.log("Auto speaker phone mode on");
			tm.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);
		}
		else {
			Earpiece.log("Auto speaker phone mode off");
			tm.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE);
		}
	}
	
	@SuppressLint("NewApi")
	@Override
	public void onDestroy() {
		Earpiece.log("stop service "+t0);
		super.onDestroy();

		settings.load(options);
		
		if (screenOnOffReceiver != null) {
			unregisterReceiver(screenOnOffReceiver);
			screenOnOffReceiver = null;
			if (settings.notifyLightOnlyWhenOff)
				setNotificationLight(true);
		}

		if (quietedCamera) {
			settings.setEarpiece(false);
			quietedCamera = false;
			unquietCameraHandler.removeCallbacks(unquietCameraRunnable);
		}
		
		Earpiece.log("disabling equalizer");
		settings.destroyEqualizer();
		disableProximity();
		disableDisableKeyguard();
		disableProximitySensor();
		if (tm != null && phoneStateListener != null) {
			tm.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE);
		}
		
		if (logThread != null) {
			interruptReader = true;
			try {
				if (logProcess != null) {
					Earpiece.log("Destroying service, killing reader");
					logProcess.destroy();
				}
//				logThread = null;
			}
			catch (Exception e) {
			}  
		}
		
		
//		if(Options.getNotify(options) != Options.NOTIFY_NEVER)
		if (Build.VERSION.SDK_INT >= 5)
			stopForeground(true);
		
	}
	
	private void setNotificationLight(boolean b) {
		android.provider.Settings.System.putInt(getContentResolver(), 
				"notification_light_pulse", b ? 1 : 0);
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
		Earpiece.log("wakeLock "+(null != wakeLock));
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
				(settings.isAutoSpeakerPhoneActive() && phoneOn)) {
			Earpiece.log("Activate proximity");
			activateProximity();
		}
		else {
			Earpiece.log("Disable proximity");
			disableProximity();		
		}
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

	private boolean needToQuiet(String line) {
		if (line.startsWith("E/AudioPolicyManager"))
			return line.contains("media stream unmute for camera sound (enforce stream)");
		else if (line.startsWith("I/ShotSingle"))
			return line.contains("ShotSingle::takePicture start");
		else if (line.startsWith("E/AXLOG"))
			return line.contains("Total-Shot2Shot**StartU");
		else if (line.startsWith("V/CameraEngine"))
			return line.contains("scheduleAutoFocus");
		else
			return false;
	}

	private void monitorLog() {
		Random x = new Random();
		BufferedReader logReader;
		String endBlock = null;
		String logMarker = "m:"+System.currentTimeMillis()+":"+x.nextLong()+":"+t0;
		
		Log.v("hook","set hook");
		  Runtime.getRuntime().addShutdownHook( 
	                new Thread(
	                        new Runnable() {
	                                public void run() {
	                                        Log.v("hook", "shutDownHook");
	                                }       
	                        }
	                )
	        );


		for(;;) {
			logProcess = null;

			String marker = "mobi.omegacentauri.Earpiece:marker:"+System.currentTimeMillis()+":"+x.nextLong()+":";
			
			try {
				Earpiece.log("logcat monitor starting");
				Log.i("EarpieceMarker", marker);
				String[] cmd2;
				if (Build.VERSION.SDK_INT >= 16) {
					 cmd2 = new String[] { "su", "-c", "logcat", "-b", "main", "EarpieceMarker:I", "AudioPolicyManager:E", 
								"ShotSingle:I", "AXLOG:E", "CameraEngine:V", "*:S" };
				}
				else {
				 cmd2 = new String[] { "logcat", "-b", "main", "EarpieceMarker:I", "AudioPolicyManager:E", 
						"ShotSingle:I", "AXLOG:E", "CameraEngine:V", "*:S" };
				}
				logProcess = Runtime.getRuntime().exec(cmd2);
				logReader = new BufferedReader(new InputStreamReader(logProcess.getInputStream()));
				Earpiece.log("reading");

				String line;
				while (null != (line = logReader.readLine())) {
					if (interruptReader)
						break;
					
					if (marker != null) {
						if (line.contains(marker))
							marker = null;
						continue;
					}
					else if (
							needToQuiet(line)
					) {
						settings.setEarpiece(true);
						settings.setEqualizer(settings.rangeLow);
						quietedCamera = true;
						unquietCameraHandler.removeCallbacks(unquietCameraRunnable);
						unquietCameraHandler.postDelayed(unquietCameraRunnable, 2000);
					}
//					else if (endBlock != null && line.contains(endBlock)) {
//						settings.setEqualizer();
//						settings.setEarpiece();
//						Earpiece.log("Ending block");
//						quietedCamera = false;
//						endBlock = null;
//					}
//					else if (line.contains("Total-Shot2Shot**StartU") &&
//						! line.contains("Earpiece")) {
//						settings.setEarpiece(true);
//						settings.setEqualizer(settings.rangeLow);
//						endBlock = "Total-Shot2Shot**EndU";
//						quietedCamera = true;
//						Earpiece.log("Starting block[total]");
//					}
//					else if (!quietedCamera && line.contains("Shot2Shot-Autofocus**StartU")) {
//						settings.setEarpiece(true);
//						settings.setEqualizer(settings.rangeLow);
//						endBlock = "Shot2Shot-Autofocus**EndU";
//						quietedCamera = true;
//						Earpiece.log("Starting block[af]");
//					}
				}

				logReader.close();
				logReader = null;
			}
			catch(IOException e) {
				Earpiece.log("logcat: "+e);

				if (logProcess != null)
					logProcess.destroy();
			}

            
			if (interruptReader) {
				Earpiece.log("reader interrupted");
			    return;
			}

			Earpiece.log("logcat monitor died");
			
			try {
				Thread.sleep(5000);
			} catch (InterruptedException e) {
			}
		}
	}


	class ScreenOnOffReceiver extends BroadcastReceiver {
//		KeyguardLock kg = km.newKeyguardLock("EarpieceNoKeyguardHack");
		
		public ScreenOnOffReceiver() {
			super();
		}

		@Override
		public void onReceive(Context context, Intent intent) {
			if (intent.getAction().equals(Intent.ACTION_SCREEN_ON))
				handleScreen(context,true);
			else if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF))
				handleScreen(context,false);
		}
		
		void handleScreen(Context context, boolean on) {
			if (settings.notifyLightOnlyWhenOff) {
				Earpiece.log("setting notify light status "+on);
				setNotificationLight(!on);				
			}
//			kg.disableKeyguard();
		}
	}


//	public class IWindowManagerWrapper {
//		private Object iWindowManager;
//		private Method getAnimationScale;
//		private Method setAnimationScale;
//		private Method disableKeyguard;
//		private Class cIWindowManager;
//		
//		public IWindowManagerWrapper() {
//			try {
//				Class cServiceManager = Class.forName("android.os.ServiceManager");
//				Method ServiceManager_getService;
//				ServiceManager_getService = cServiceManager.getMethod("getService", 
//						new Class[] { String.class });
//				IBinder windowService = (IBinder) ServiceManager_getService.invoke(null, "window");
//				Class cIWindowManager_Stub = Class.forName("android.view.IWindowManager$Stub");
//				Method IWindowManager_Stub_asInterface =
//					cIWindowManager_Stub.getMethod("asInterface", IBinder.class);
//
//				iWindowManager = IWindowManager_Stub_asInterface.invoke(null, windowService);
//				cIWindowManager = Class.forName("android.view.IWindowManager");
//				
//				getAnimationScale = cIWindowManager.getMethod("getAnimationScale", 
//						Integer.TYPE);
//				setAnimationScale = cIWindowManager.getMethod("setAnimationScale", 
//						Integer.TYPE, Float.TYPE);
//				disableKeyguard = cIWindowManager.getMethod("disableKeyguard", 
//						IBinder.class, String.class);
//				
//				Earpiece.log("got IWindowManager");
//				
//				
//			} catch (InvocationTargetException e) {
//				Log.e("IWindowManagerWrapper", ""+e.getCause());
//				iWindowManager = null;
//			}
//			catch (Exception e) {
//				Log.e("IWindowManagerWrapper", ""+e);
//				iWindowManager = null;
//				return;
//			}		
//		}
//		
//		public float getAnimationScale(int which) {
//			if (iWindowManager == null)
//				return Float.NaN;
//			
//			try {
//				Float value = (Float)getAnimationScale.invoke(iWindowManager, which);
//				return (float)value;
//			} catch (InvocationTargetException e) {
//				Log.e("IWindowManagerWrapper.getAnimationScale", ""+e.getCause());
//				return Float.NaN;
//			} catch (Exception e) {
//				Log.e("IWindowManagerWrapper.getAnimationScale", ""+e);
//				return Float.NaN;
//			} 
//		}
//		
//		public void setAnimationScale(int which, float value) {
//			if (iWindowManager == null)
//				return;
//			
//			try {
//				setAnimationScale.invoke(iWindowManager, which, value);
//			} catch (InvocationTargetException e) {
//				Log.e("IWindowManagerWrapper.setAnimationScale", ""+e.getCause());
//			} catch (Exception e) {
//				Log.e("IWindowManagerWrapper.setAnimationScale", ""+e);
//			} 
//		}
//		
//		public void disableKeyguard(IBinder token, String tag) {
//			if (iWindowManager == null)
//				return;
//			
//			try {
//				disableKeyguard.invoke(iWindowManager, token, tag);
//				Log.v("IWindowManagerWrapper.disableKeyguard", "success");
//			} catch (InvocationTargetException e) {
//				Log.e("IWindowManagerWrapper.disableKeyguard", ""+e.getCause());
//			} catch (Exception e) {
//				Log.e("IWindowManagerWrapper.disableKeyguard", ""+e);
//			} 
//			
//		}
//	}

}

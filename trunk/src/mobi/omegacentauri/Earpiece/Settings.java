package mobi.omegacentauri.Earpiece;

import java.util.List;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.media.audiofx.Equalizer;
import android.os.Build;
import android.telephony.TelephonyManager;
import android.view.ViewConfiguration;

public class Settings {
	public boolean earpieceActive;
	public boolean equalizerActive;
	public boolean disableKeyguardActive;
	public boolean autoSpeakerPhoneActive;
	public int boostValue;
	public short bands;
	public short rangeLow;
	public short rangeHigh;
	public boolean proximity;
	public boolean quietCamera;
	public int maximumBoostPercent;

	public Sensor proximitySensor;
	public AudioManager audioManager;
	public SensorManager sensorManager;
	private PackageManager pm;
	private Equalizer eq;
	private Context context;
	private boolean shape = true;

	public Settings(Context context) {
		this.context = context;
		audioManager = (AudioManager)context.getSystemService(Context.AUDIO_SERVICE);
		sensorManager = (SensorManager)context.getSystemService(Context.SENSOR_SERVICE);
		pm = (PackageManager)context.getPackageManager();
		
		proximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
		
		eq = null;
		
		if (9 <= Build.VERSION.SDK_INT) {
			try {
		        eq = new Equalizer(87654321, 0);
				bands = eq.getNumberOfBands();
				
				rangeLow = eq.getBandLevelRange()[0];
				rangeHigh = eq.getBandLevelRange()[1];
			}
			catch (UnsupportedOperationException e) {
				eq = null;
			}
		}
	}
	
	public void load(SharedPreferences pref) {
    	equalizerActive = pref.getBoolean(Options.PREF_EQUALIZER_ACTIVE, false);
    	earpieceActive = pref.getBoolean(Options.PREF_EARPIECE_ACTIVE, false);
    	autoSpeakerPhoneActive = pref.getBoolean(Options.PREF_AUTO_SPEAKER_PHONE, false)
    	   && haveProximity(); 
    	proximity = pref.getBoolean(Options.PREF_PROXIMITY, false) && haveProximity();
    	boostValue = pref.getInt(Options.PREF_BOOST, 0);
    	disableKeyguardActive = pref.getBoolean(Options.PREF_DISABLE_KEYGUARD, false);
    	shape = pref.getBoolean(Options.PREF_SHAPE, true);
    	quietCamera = pref.getBoolean(Options.PREF_QUIET_CAMERA, false);
    	maximumBoostPercent = Options.getMaximumBoost(pref);
    	Earpiece.log("max boost = "+maximumBoostPercent);
	}
	
	public void save(SharedPreferences pref) {
    	SharedPreferences.Editor ed = pref.edit();
    	ed.putBoolean(Options.PREF_EARPIECE_ACTIVE, earpieceActive);
    	ed.putBoolean(Options.PREF_EQUALIZER_ACTIVE, equalizerActive);
    	ed.putBoolean(Options.PREF_AUTO_SPEAKER_PHONE, autoSpeakerPhoneActive);
    	ed.putBoolean(Options.PREF_PROXIMITY, proximity);
    	ed.putBoolean(Options.PREF_DISABLE_KEYGUARD, disableKeyguardActive);
    	ed.putInt(Options.PREF_BOOST, boostValue);
    	ed.putBoolean(Options.PREF_SHAPE, shape);
    	ed.putBoolean(Options.PREF_QUIET_CAMERA, quietCamera);
    	ed.putString(Options.PREF_MAXIMUM_BOOST, ""+maximumBoostPercent);
    	ed.commit();
	}
	
	public void setEarpiece() {
		setEarpiece(earpieceActive);
	}
	
	public void setEarpiece(boolean value) {
		if (!haveTelephony())
			return;
		
		audioManager.setSpeakerphoneOn(false);
		
		if (value) {
			Earpiece.log("Earpiece mode on");
			audioManager.setMode(AudioManager.MODE_IN_CALL);
			audioManager.setSpeakerphoneOn(false);
			Earpiece.log(audioManager.getParameters("mute"));
			Earpiece.log(audioManager.getParameters("noise_suppression"));

//			audioManager.setRouting(AudioManager.MODE_IN_CALL, AudioManager.ROUTE_EARPIECE,
//					AudioManager.ROUTE_ALL);
		}
		else {
			Earpiece.log("Earpiece mode off	");
			audioManager.setMode(AudioManager.MODE_NORMAL);
//			audioManager.setRouting(AudioManager.MODE_NORMAL, AudioManager.ROUTE_SPEAKER, 
//					AudioManager.ROUTE_ALL);
		}		
	}
	
	public void setEqualizer(short v) {
		if (eq == null)
			return;

    	for (short i=0; i<bands; i++) {
        	
        	short adj = v;
        	
        	if (shape && 0 <= v) {
	    		int hz = eq.getCenterFreq(i)/1000;
	        	if (hz < 150)
	        		adj = 0;
	        	else if (hz < 250)
	        		adj = (short)(v/2);
	        	else if (hz > 8000)
	        		adj = (short)(3*(int)v/4);
        	}

        	Earpiece.log("boost "+i+" ("+(eq.getCenterFreq(i)/1000)+"hz) to "+adj);        	

        	eq.setBandLevel(i, adj);
    	}
    	
    	eq.setEnabled(v != 0);		
	}
	
	public void setEqualizer() {
		Earpiece.log("setEqualizer "+boostValue);
		
		if (eq == null) 
			return;
		
		short v;
		
		v = (short)boostValue;

		if (v < 0 || !equalizerActive)
    		v = 0;
    	
    	if (v > rangeHigh)
    		v = rangeHigh;
    	
    	setEqualizer(v);
	}
	
	public void setAll() {
		setEarpiece();
		setEqualizer();
	}

	public boolean haveEqualizer() {
		return eq != null;
	}

	public void disableEqualizer() {
		if (eq != null) {
			Earpiece.log("Closing equalizer");
			eq.setEnabled(false);
		}
	}
	
	public boolean haveProximity() {
		return proximitySensor != null;
	}
	
	public boolean isEqualizerActive() {
		return eq != null && equalizerActive;
	}
	
	public boolean isDisableKeyguardActive() {
		return disableKeyguardActive;
	}
	
	public boolean isProximityActive() {
		return haveProximity() && earpieceActive && proximity;
	}
	
	public boolean isAutoSpeakerPhoneActive() {
		return haveProximity() && autoSpeakerPhoneActive;
	}
	
	public boolean isQuietCameraActive() {
		return eq != null && quietCamera;		
	}
	
	public boolean needService() {
		return isEqualizerActive() || isProximityActive() ||
			isAutoSpeakerPhoneActive() || isDisableKeyguardActive() ||
			isQuietCameraActive();
	}
	
//    private static String onoff(boolean v) {
//    	return v ? "on" : "off";
//    }
    
    public boolean somethingOn() {
    	return earpieceActive || isEqualizerActive() || isAutoSpeakerPhoneActive() || 
    	isDisableKeyguardActive() || isQuietCameraActive();
    }
    
	public String describe() {
		if (! somethingOn())
			return "Earpiece application is off";
		
		String[] list = new String[6];
		int count;
		
		count = 0;
		if (earpieceActive)
			list[count++] = "earpiece";
		if (isProximityActive())
			list[count++] = "proximity";
		if (isEqualizerActive())
			list[count++] = "boost";
		if (isAutoSpeakerPhoneActive())
			list[count++] = "auto speaker";
		if (isDisableKeyguardActive())
			list[count++] = "no lock";
		if (isQuietCameraActive())
			list[count++] = "quiet camera";
		
		String out = "";
		for (int i=0; i<count; i++) {
			out = out + list[i];
			if (i+1<count)
				out += ", ";
		}
		
		return out;
	}

	public boolean haveTelephony() {
		return pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY);	
	}
	
	public static boolean isKindle() {
		return Build.MODEL.equalsIgnoreCase("Kindle Fire");		
	}
	
	public boolean hasMenuKey() {
		if (isKindle() || Build.VERSION.SDK_INT < 14)
			return true;
		return ViewConfiguration.get(this.context).hasPermanentMenuKey();
		
	}
}

package mobi.omegacentauri.Earpiece;

import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.media.audiofx.Equalizer;
import android.os.Build;

public class Settings {
	public boolean earpieceActive;
	public boolean equalizerActive;
	public int boostValue;
	public short bands;
	public short rangeLow;
	public short rangeHigh;
	public boolean proximity;
	
	private AudioManager am;
	private SensorManager sm;
	private Equalizer eq;

	public Settings(Context context) {
		am = (AudioManager)context.getSystemService(Context.AUDIO_SERVICE);
		sm = (SensorManager)context.getSystemService(Context.SENSOR_SERVICE);
		eq = null;
		
		if (9 <= Build.VERSION.SDK_INT) {
			try {
		        eq = new Equalizer(0, 0);
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
    	proximity = pref.getBoolean(Options.PREF_PROXIMITY, false) && haveProximity();
    	boostValue = pref.getInt(Options.PREF_BOOST, 0);
	}
	
	public void save(SharedPreferences pref) {
    	SharedPreferences.Editor ed = pref.edit();
    	ed.putBoolean(Options.PREF_EARPIECE_ACTIVE, earpieceActive);
    	ed.putBoolean(Options.PREF_EQUALIZER_ACTIVE, equalizerActive);
    	ed.putBoolean(Options.PREF_PROXIMITY, proximity);
    	ed.putInt(Options.PREF_BOOST, boostValue);
    	ed.commit();
	}
	
	public void setEarpiece() {
		am.setSpeakerphoneOn(false);
		
		if (earpieceActive) {
			am.setMode(AudioManager.MODE_IN_CALL);
			am.setSpeakerphoneOn(false);

//			am.setParameters("noise_suppression=on");

//			am.setRouting(AudioManager.MODE_IN_CALL, AudioManager.ROUTE_EARPIECE,
//					AudioManager.ROUTE_ALL);
		}
		else {
			am.setMode(AudioManager.MODE_NORMAL);
//			am.setRouting(AudioManager.MODE_NORMAL, AudioManager.ROUTE_SPEAKER, 
//					AudioManager.ROUTE_ALL);
		}		
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

    	for (short i=0; i<bands; i++) {
        	Earpiece.log("boost "+i+" to "+v);

    		eq.setBandLevel(i, v);
    	}
    	
    	eq.setEnabled(v > 0);
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
		return true;//sm.getSensorList(SensorManager.SENSOR_PROXIMITY).isEmpty();
	}
	
	public boolean isEqualizerActive() {
		return eq != null && equalizerActive;
	}
	
	public boolean isProximityActive() {
		return haveProximity() && earpieceActive && proximity;
	}
	
	public boolean needService() {
		return isEqualizerActive() || isProximityActive();
	}
	
//    private static String onoff(boolean v) {
//    	return v ? "on" : "off";
//    }
    
    public boolean somethingOn() {
    	return earpieceActive || isEqualizerActive();
    }
    
	public String describe() {
		if (! somethingOn())
			return "Earpiece application is off";
		
		String[] list = new String[3];
		int count;
		
		count = 0;
		if (earpieceActive)
			list[count++] = "earpiece";
		if (isProximityActive())
			list[count++] = "proximity";
		if (isEqualizerActive())
			list[count++] = "equalizer";
		
		String out = "";
		for (int i=0; i<count; i++) {
			out = out + list[i];
			if (i+1<count)
				out += ", ";
		}
		
		return out;
	}
}

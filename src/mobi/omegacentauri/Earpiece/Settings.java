package mobi.omegacentauri.Earpiece;

import android.content.Context;
import android.content.SharedPreferences;
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
	
	private AudioManager am;
	private Equalizer eq;

	public Settings(Context context) {
		am = (AudioManager)context.getSystemService(Context.AUDIO_SERVICE);
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
    	boostValue = pref.getInt(Options.PREF_BOOST, 0);
	}
	
	public void save(SharedPreferences pref) {
    	SharedPreferences.Editor ed = pref.edit();
    	ed.putBoolean(Options.PREF_EARPIECE_ACTIVE, earpieceActive);
    	ed.putBoolean(Options.PREF_EQUALIZER_ACTIVE, equalizerActive);
    	ed.putInt(Options.PREF_BOOST, boostValue);
    	ed.commit();
	}
	
	public void setEarpiece() {
		am.setSpeakerphoneOn(false);
		
		if (earpieceActive) {
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

	public void closeEqualizer() {
		if (eq != null) {
			Earpiece.log("Closing equalizer");
			eq.setEnabled(false);
			eq = null;
		}
	}

	public boolean isEqualizerActive() {
		return eq != null && equalizerActive;
	}
}

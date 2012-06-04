package mobi.omegacentauri.Earpiece;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

public class OnBootReceiver extends BroadcastReceiver {
	
	@Override
	public void onReceive(Context context, Intent intent) {
		Settings settings = new Settings(context);
		settings.load(PreferenceManager.getDefaultSharedPreferences(context));
		settings.setEarpiece();
		
		if (settings.isEqualizerActive()) {
			Intent i = new Intent(context, EarpieceService.class);
			context.startService(i);
		}
	}
}

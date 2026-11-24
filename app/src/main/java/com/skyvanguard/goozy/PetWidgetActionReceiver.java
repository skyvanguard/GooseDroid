package com.skyvanguard.goozy;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Recibe los botones del widget (alimentar, jugar, dormir).
 *
 * Está separado de {@link PetWidget} porque este último tiene que ser exportado
 * para recibir las actualizaciones del sistema; este no lo es, así que ninguna
 * otra app puede alimentar a la mascota por su cuenta.
 */
public class PetWidgetActionReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent != null ? intent.getAction() : null;
        if (action == null) return;

        // Con el proceso recién nacido el estado en memoria son valores por defecto
        PetRepository.ensureLoaded(context);

        switch (action) {
            case PetWidget.ACTION_FEED:
                PetNeeds.get().feed();
                break;
            case PetWidget.ACTION_PLAY:
                if (PetNeeds.get().energy > PetWidget.MIN_ENERGY_TO_PLAY) {
                    PetNeeds.get().play();
                }
                break;
            case PetWidget.ACTION_SLEEP:
                PetNeeds.get().sleep();
                break;
            case PetWidget.ACTION_REFRESH:
                PetWidget.updateAllWidgets(context);
                return;
            default:
                return;
        }
        PetRepository.save(context, null);
        PetWidget.updateAllWidgets(context);
    }
}

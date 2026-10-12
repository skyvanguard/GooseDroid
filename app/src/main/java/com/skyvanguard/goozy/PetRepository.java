package com.skyvanguard.goozy;

import android.content.Context;
import android.util.Log;

import com.skyvanguard.goozy.engine.TheGoose;

import java.io.File;
import java.io.IOException;
import java.util.Properties;

/**
 * Única puerta de entrada al estado guardado de la mascota.
 *
 * La Activity, el servicio del overlay y el widget comparten proceso y el mismo
 * {@link PetState} en memoria; esta clase lo carga una sola vez por proceso y lo
 * guarda en config.ini conservando las claves que no le pertenecen.
 */
public final class PetRepository {
    private static final String TAG = "PetRepository";
    private static final String CONFIG_FILE_NAME = "config.ini";

    private static final float DEFAULT_HUNGER = 50f;
    private static final float DEFAULT_ENERGY = 100f;
    private static final float DEFAULT_HAPPINESS = 75f;
    private static final float DEFAULT_MISCHIEF = 50f;
    private static final float DEFAULT_CARE = 100f;
    private static final String DEFAULT_PET_NAME = "Goozy";

    private static boolean isLoaded = false;

    private PetRepository() {
    }

    public static String getConfigPath(Context context) {
        return new File(context.getFilesDir(), CONFIG_FILE_NAME).getAbsolutePath();
    }

    /**
     * Devuelve la configuración leída de disco, creando el archivo con los valores
     * por defecto si todavía no existe.
     */
    public static PetConfigFile readConfig(Context context) throws IOException {
        String path = getConfigPath(context);
        if (!Utils.fileExists(path)) {
            Utils.copyAssetFile(context, CONFIG_FILE_NAME, path);
        }
        PetConfigFile config = new PetConfigFile();
        config.load(new java.io.File(path));
        return config;
    }

    /**
     * Carga el estado de la mascota si este proceso todavía no lo hizo.
     */
    public static synchronized void ensureLoaded(Context context) {
        if (isLoaded) return;
        try {
            loadFrom(readConfig(context));
        } catch (IOException e) {
            Log.e(TAG, "No se pudo leer el estado guardado; se usan valores por defecto", e);
            PetNeeds.get().reset();
            PetPersonality.get().reset();
            PetAppearance.get().reset();
        }
        isLoaded = true;
    }

    /** Fuerza una relectura desde disco (por ejemplo tras restablecer la configuración). */
    public static synchronized void reload(Context context) {
        isLoaded = false;
        ensureLoaded(context);
    }

    private static void loadFrom(PetConfigFile config) {
        boolean isPetModeEnabled = parseBoolean(config.get("PetModeEnabled"), true);
        TheGoose.petModeEnabled = isPetModeEnabled;

        long lastPlayed = parseLong(config.get("LastPlayedTimestamp"), 0L);
        PetNeeds.get().loadState(
                parseFloat(config.get("PetHunger"), DEFAULT_HUNGER),
                parseFloat(config.get("PetEnergy"), DEFAULT_ENERGY),
                parseFloat(config.get("PetHappiness"), DEFAULT_HAPPINESS),
                lastPlayed);
        // Higiene y salud se cargan después: loadState ya aplicó el tiempo offline
        // a lo demás; la higiene offline se aplica al próximo updateOfflineTime.
        PetNeeds.get().loadCare(
                parseFloat(config.get("PetHygiene"), DEFAULT_CARE),
                parseFloat(config.get("PetHealth"), DEFAULT_CARE));

        PetPersonality.get().loadState(
                parseFloat(config.get("PersonalityPlayfulness"), 0f),
                parseFloat(config.get("PersonalityAffection"), 0f),
                parseFloat(config.get("PersonalityBravery"), 0f),
                parseFloat(config.get("PersonalityMischief"), DEFAULT_MISCHIEF),
                parseInt(config.get("TotalPets"), 0),
                parseInt(config.get("TotalPlays"), 0),
                parseInt(config.get("TotalFeeds"), 0),
                lastPlayed > 0 ? lastPlayed : System.currentTimeMillis());

        if (isPetModeEnabled) {
            PetAppearance appearance = PetAppearance.get();
            String petName = config.get("PetName");
            appearance.loadState(
                    appearance.hexToColor(config.get("PetBodyColor")),
                    appearance.hexToColor(config.get("PetAccentColor")),
                    appearance.hexToColor(config.get("PetOutlineColor")),
                    appearance.hexToColor(config.get("PetEyeColor")),
                    parseInt(config.get("PetHatId"), 0),
                    parseInt(config.get("PetAccessoryId"), 0),
                    parseInt(config.get("PetCreatureType"), 0),
                    petName != null ? petName : DEFAULT_PET_NAME);
        }
    }

    /**
     * Guarda el estado de la mascota.
     *
     * @param extra claves adicionales del llamador (configuración de la pantalla,
     *              estadísticas); puede ser null
     */
    public static synchronized void save(Context context, Properties extra) {
        if (!isLoaded) {
            // Nunca pisar el archivo con valores por defecto de un proceso recién nacido
            ensureLoaded(context);
        }

        Properties props = new Properties();
        if (extra != null) {
            props.putAll(extra);
        }

        PetNeeds needs = PetNeeds.get();
        props.setProperty("PetHunger", String.valueOf(needs.hunger));
        props.setProperty("PetEnergy", String.valueOf(needs.energy));
        props.setProperty("PetHappiness", String.valueOf(needs.happiness));
        props.setProperty("PetHygiene", String.valueOf(needs.hygiene));
        props.setProperty("PetHealth", String.valueOf(needs.health));
        props.setProperty("LastPlayedTimestamp", String.valueOf(System.currentTimeMillis()));
        props.setProperty("PetModeEnabled", TheGoose.petModeEnabled ? "True" : "False");

        PetPersonality personality = PetPersonality.get();
        props.setProperty("PersonalityPlayfulness", String.valueOf(personality.playfulness));
        props.setProperty("PersonalityAffection", String.valueOf(personality.affection));
        props.setProperty("PersonalityBravery", String.valueOf(personality.bravery));
        props.setProperty("PersonalityMischief", String.valueOf(personality.mischief));
        props.setProperty("TotalPets", String.valueOf(personality.getTotalPets()));
        props.setProperty("TotalPlays", String.valueOf(personality.getTotalPlays()));
        props.setProperty("TotalFeeds", String.valueOf(personality.getTotalFeeds()));

        PetAppearance appearance = PetAppearance.get();
        props.setProperty("PetBodyColor", appearance.colorToHex(appearance.bodyColor));
        props.setProperty("PetAccentColor", appearance.colorToHex(appearance.accentColor));
        props.setProperty("PetOutlineColor", appearance.colorToHex(appearance.outlineColor));
        props.setProperty("PetEyeColor", appearance.colorToHex(appearance.eyeColor));
        props.setProperty("PetHatId", String.valueOf(appearance.hatId));
        props.setProperty("PetAccessoryId", String.valueOf(appearance.accessoryId));
        props.setProperty("PetCreatureType", String.valueOf(appearance.creatureType));
        props.setProperty("PetName",
                appearance.petName != null ? appearance.petName : DEFAULT_PET_NAME);

        try {
            PetConfigFile.saveMerged(new java.io.File(getConfigPath(context)), props);
            needs.markUpdated();
        } catch (IOException e) {
            Log.e(TAG, "No se pudo guardar el estado de la mascota", e);
        }
    }

    private static boolean parseBoolean(String value, boolean defaultValue) {
        if (value == null || value.isEmpty()) return defaultValue;
        return Boolean.parseBoolean(value.trim().toLowerCase());
    }

    private static float parseFloat(String value, float defaultValue) {
        if (value == null || value.isEmpty()) return defaultValue;
        try {
            return Float.parseFloat(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static int parseInt(String value, int defaultValue) {
        if (value == null || value.isEmpty()) return defaultValue;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static long parseLong(String value, long defaultValue) {
        if (value == null || value.isEmpty()) return defaultValue;
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}

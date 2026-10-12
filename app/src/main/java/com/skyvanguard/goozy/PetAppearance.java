package com.skyvanguard.goozy;

import com.skyvanguard.goozy.engine.TheGoose;

/**
 * Sistema de apariencia personalizable para la mascota virtual.
 * Gestiona colores, accesorios y tipos de criatura.
 */
public class PetAppearance {
    // Colores base (ARGB)
    public int bodyColor = 0xFFFFFFFF;    // Blanco
    public int accentColor = 0xFFFFA500;  // Naranja
    public int outlineColor = 0xFFD3D3D3; // Gris claro
    public int eyeColor = 0xFF000000;     // Negro

    // Accesorios (por ID)
    public int hatId = 0;        // 0=ninguno, 1=lazo, 2=sombrero, 3=corona, 4=gorra
    public int accessoryId = 0;  // 0=ninguno, 1=bufanda, 2=gafas, 3=collar, 4=pajarita

    // Tipo de criatura
    public int creatureType = 0; // 0=ganso, 1=pato, 2=pollito, 3=personalizado

    // Nombre de la mascota
    public String petName = "Goozy";

    // Definiciones de accesorios (constantes estáticas)
    public static final String[] HAT_NAMES = {
        "None", "Bow", "Top Hat", "Crown", "Cap"
    };

    public static final String[] ACCESSORY_NAMES = {
        "None", "Scarf", "Glasses", "Collar", "Bow Tie"
    };

    public static final String[] CREATURE_NAMES = {
        "Goose", "Duck", "Chick", "Custom"
    };

    // Colores predefinidos para tipos de criatura
    private static final int[][] CREATURE_COLORS = {
        // Ganso: blanco con pico naranja
        {0xFFFFFFFF, 0xFFFFA500, 0xFFD3D3D3, 0xFF000000},
        // Pato: amarillo con pico naranja
        {0xFFFFEB3B, 0xFFFFA500, 0xFFE0E0E0, 0xFF000000},
        // Pollito: amarillo claro con pico rosa
        {0xFFFFF9C4, 0xFFFF7043, 0xFFFFE0B2, 0xFF000000},
        // Personalizado: usa colores actuales
        {0xFFFFFFFF, 0xFFFFA500, 0xFFD3D3D3, 0xFF000000}
    };

    public PetAppearance() {
    }

    /**
     * Retorna la instancia singleton via PetState.
     */
    public static PetAppearance get() {
        return PetState.getInstance().appearance;
    }

    /**
     * Aplica los colores actuales al ganso.
     */
    public void applyToGoose() {
        TheGoose.BodyColor = bodyColor;
        TheGoose.FootColor = accentColor;
        TheGoose.MouthColor = accentColor;
        TheGoose.OutLineColor = outlineColor;
        TheGoose.EyeColor = eyeColor;
    }

    /**
     * Cambia el tipo de criatura y aplica colores predeterminados.
     */
    public void setCreatureType(int type) {
        if (type < 0 || type >= CREATURE_COLORS.length) return;

        creatureType = type;

        // Solo cambiar colores si no es tipo personalizado
        if (type != 3) {
            bodyColor = CREATURE_COLORS[type][0];
            accentColor = CREATURE_COLORS[type][1];
            outlineColor = CREATURE_COLORS[type][2];
            eyeColor = CREATURE_COLORS[type][3];
        }

        applyToGoose();
    }

    /**
     * Establece colores personalizados.
     */
    public void setCustomColors(int body, int accent, int outline, int eye) {
        bodyColor = body;
        accentColor = accent;
        outlineColor = outline;
        eyeColor = eye;
        creatureType = 3; // Cambiar a tipo personalizado
        applyToGoose();
    }

    /**
     * Cambia el sombrero.
     */
    public void setHat(int id) {
        if (id >= 0 && id < HAT_NAMES.length) {
            hatId = id;
        }
    }

    /**
     * Cambia el accesorio.
     */
    public void setAccessory(int id) {
        if (id >= 0 && id < ACCESSORY_NAMES.length) {
            accessoryId = id;
        }
    }

    /**
     * Obtiene el nombre del sombrero actual.
     */
    public String getHatName() {
        return HAT_NAMES[hatId];
    }

    /**
     * Obtiene el nombre del accesorio actual.
     */
    public String getAccessoryName() {
        return ACCESSORY_NAMES[accessoryId];
    }

    /**
     * Obtiene el nombre del tipo de criatura.
     */
    public String getCreatureName() {
        return CREATURE_NAMES[creatureType];
    }

    /**
     * Verifica si la mascota tiene accesorios.
     */
    public boolean hasAccessories() {
        return hatId > 0 || accessoryId > 0;
    }

    /**
     * Convierte color int a string hex.
     */
    public static String colorToHex(int color) {
        return String.format("#%08X", color);
    }

    /**
     * Convierte string hex a color int.
     */
    public static int hexToColor(String hex) {
        try {
            if (hex.startsWith("#")) {
                hex = hex.substring(1);
            }
            if (hex.length() == 6) {
                hex = "FF" + hex;
            }
            return (int) Long.parseLong(hex, 16);
        } catch (Exception e) {
            return 0xFFFFFFFF;
        }
    }

    /**
     * Resetea la apariencia a valores por defecto.
     */
    public void reset() {
        bodyColor = 0xFFFFFFFF;
        accentColor = 0xFFFFA500;
        outlineColor = 0xFFD3D3D3;
        eyeColor = 0xFF000000;
        hatId = 0;
        accessoryId = 0;
        creatureType = 0;
        petName = "Goozy";
        applyToGoose();
    }

    /**
     * Carga el estado desde valores guardados.
     */
    public void loadState(int savedBody, int savedAccent, int savedOutline, int savedEye,
                                  int savedHat, int savedAccessory, int savedCreature, String savedName) {
        bodyColor = savedBody;
        accentColor = savedAccent;
        outlineColor = savedOutline;
        eyeColor = savedEye;
        hatId = Math.max(0, Math.min(HAT_NAMES.length - 1, savedHat));
        accessoryId = Math.max(0, Math.min(ACCESSORY_NAMES.length - 1, savedAccessory));
        creatureType = Math.max(0, Math.min(CREATURE_NAMES.length - 1, savedCreature));
        petName = (savedName != null && !savedName.isEmpty()) ? savedName : "Goozy";
        applyToGoose();
    }

    /**
     * Obtiene la descripcion completa de la apariencia.
     */
    public String getDescription() {
        StringBuilder sb = new StringBuilder();
        sb.append(petName).append(" the ").append(getCreatureName());
        if (hatId > 0) {
            sb.append(" with ").append(getHatName());
        }
        if (accessoryId > 0) {
            sb.append(hatId > 0 ? " and " : " with ");
            sb.append(getAccessoryName());
        }
        return sb.toString();
    }
}

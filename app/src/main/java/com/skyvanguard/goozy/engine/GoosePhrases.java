package com.skyvanguard.goozy.engine;

import java.text.Normalizer;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Tablas de frases del ganso en español e inglés.
 *
 * GooseLLM decide la categoría (necesidades, hora, ánimo, etc.) y esta clase solo aporta
 * el texto en el idioma activo. Las claves de pensamientos son los nombres de categoría
 * que usa GooseLLM ("starving", "evolution_egg", "reaction_pet"...); las respuestas, los
 * hitos y la hora favorita usan las constantes de abajo.
 *
 * Sin dependencias de Android para poder testearla en la JVM.
 */
public final class GoosePhrases {

    /** Idiomas disponibles para las frases por plantilla. */
    public enum Language { SPANISH, ENGLISH }

    public static final Language DEFAULT_LANGUAGE = Language.SPANISH;

    // ============== CLAVES ==============

    public static final String AMBIENT = "ambient";

    public static final String RESPONSE_PET_BEST_FRIEND = "response_pet_bestfriend";
    public static final String RESPONSE_PET_HIGH = "response_pet_high";
    public static final String RESPONSE_PET_MID = "response_pet_mid";
    public static final String RESPONSE_PET_LOW = "response_pet_low";
    public static final String RESPONSE_FEED_STARVING = "response_feed_starving";
    public static final String RESPONSE_FEED_HUNGRY = "response_feed_hungry";
    public static final String RESPONSE_FEED_FULL = "response_feed_full";
    public static final String RESPONSE_PLAY_HIGH = "response_play_high";
    public static final String RESPONSE_PLAY_MID = "response_play_mid";
    public static final String RESPONSE_PLAY_TIRED = "response_play_tired";
    public static final String RESPONSE_DRAG = "response_drag";
    public static final String RESPONSE_GREET = "response_greet";
    public static final String RESPONSE_FAREWELL = "response_farewell";
    public static final String RESPONSE_DEFAULT = "response_default";

    public static final String FAVORITE_TIME = "favorite_time";

    // Los hitos tienen una sola frase: se muestran una vez y no se sortean
    public static final String MILESTONE_PETS_1000 = "milestone_pets_1000";
    public static final String MILESTONE_PETS_500 = "milestone_pets_500";
    public static final String MILESTONE_PETS_100 = "milestone_pets_100";
    public static final String MILESTONE_FEEDS_100 = "milestone_feeds_100";
    public static final String MILESTONE_PLAYS_100 = "milestone_plays_100";
    public static final String MILESTONE_DAYS_365 = "milestone_days_365";
    public static final String MILESTONE_DAYS_100 = "milestone_days_100";
    public static final String MILESTONE_DAYS_30 = "milestone_days_30";
    public static final String MILESTONE_DAYS_7 = "milestone_days_7";

    private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{M}+");

    private static final Map<String, String[]> SPANISH = buildSpanish();
    private static final Map<String, String[]> ENGLISH = buildEnglish();

    // Burbujas cortas escritas en inglés en el código (showEmoji): inglés -> español
    private static final Map<String, String> SPANISH_EXPRESSIONS = buildSpanishExpressions();

    private GoosePhrases() {
    }

    // ============== API ==============

    /**
     * Interpreta un nombre de idioma libre ("español", "Spanish", "es", "english"...).
     * Si empieza con "es" o "spa" (sin distinguir mayúsculas ni tildes) es español;
     * cualquier otro texto es inglés. Nulo o vacío se trata como "sin configurar" y
     * devuelve el idioma por defecto (español).
     */
    public static Language parseLanguage(String name) {
        if (name == null) return DEFAULT_LANGUAGE;
        String normalized = stripAccents(name.trim()).toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) return DEFAULT_LANGUAGE;
        if (normalized.startsWith("es") || normalized.startsWith("spa")) {
            return Language.SPANISH;
        }
        return Language.ENGLISH;
    }

    /**
     * Frases de una clave en el idioma pedido (copia defensiva), o null si la clave
     * no tiene frases en ese idioma.
     */
    public static String[] get(String key, Language language) {
        String[] phrases = tableFor(language).get(key);
        return phrases == null ? null : phrases.clone();
    }

    /** Claves disponibles en un idioma (vista inmodificable). */
    public static Set<String> keys(Language language) {
        return tableFor(language).keySet();
    }

    /**
     * Traduce una expresión corta de la burbuja del ganso ("*preen*", "bread?"...).
     * Solo cambia textos conocidos, por coincidencia exacta y solo en español; cualquier
     * otro texto (caritas, onomatopeyas, lo que genera un modelo, null) vuelve igual.
     */
    public static String localizeExpression(String text, Language language) {
        if (text == null || language != Language.SPANISH) return text;
        String translated = SPANISH_EXPRESSIONS.get(text);
        return translated != null ? translated : text;
    }

    /** Expresiones en inglés que tienen traducción (vista inmodificable). */
    public static Set<String> expressionKeys() {
        return SPANISH_EXPRESSIONS.keySet();
    }

    private static Map<String, String[]> tableFor(Language language) {
        return language == Language.ENGLISH ? ENGLISH : SPANISH;
    }

    private static String stripAccents(String text) {
        String decomposed = Normalizer.normalize(text, Normalizer.Form.NFD);
        return COMBINING_MARKS.matcher(decomposed).replaceAll("");
    }

    /** Agrega una categoría y falla si la clave ya existía (evita pisar tablas al copiar). */
    private static void add(Map<String, String[]> table, String key, String... phrases) {
        if (table.put(key, phrases) != null) {
            throw new IllegalStateException("Clave de frases duplicada: " + key);
        }
    }

    /** Agrega una traducción de expresión y falla si ya existía. */
    private static void addExpression(Map<String, String> table, String english, String spanish) {
        if (table.put(english, spanish) != null) {
            throw new IllegalStateException("Expresión duplicada: " + english);
        }
    }

    // ============== ESPAÑOL ==============

    private static Map<String, String[]> buildSpanish() {
        Map<String, String[]> t = new HashMap<>();

        // Necesidades críticas
        add(t, "starving", "¡HAMBRE!!!", "¡DAME DE COMER!", "me muero de hambre", "¡necesito comida!",
                "¡qué hambre!", "¿¡PAN!?", "me estoy muriendo", "hambruna...", "panza vacía", "COMIDAAAA");
        add(t, "hungry", "hambre...", "¿algo pa picar?", "comida porfa", "dame de comer~", "pancita vacía",
                "¿ñam ñam?", "¿tenés pan?", "honk de hambre", "¿hay chipa?", "me ruge la panza");
        add(t, "peckish", "¿hora de picar?", "un poco de hambre", "comería algo", "¿comida?", "¿unas migas?");

        add(t, "exhausted", "qué... cansancio", "no me... puedo mover", "necesito dormir", "zzZZzz",
                "estoy muerto", "*se desploma*", "re cansado", "energía=0", "tengo que descansar",
                "apagándome...");
        add(t, "tired", "tengo sueño...", "bostezo~", "honk cansado", "¿siestita?", "zzz...",
                "*bosteza*", "¿descansamos?", "medio dormido...", "necesito una siesta", "ganso con sueño");
        add(t, "sleepy", "*bostezo*", "medio cansado", "¿zzz?", "siesta pronto", "sueñito~");

        add(t, "sad", "solito...", "ganso triste", "T_T", "haceme caso", "bajoneado",
                "*suspira*", "te extraño", "solo...", ":(", "honk triste");
        add(t, "lonely", "¿hola?", "¿hay alguien?", "solito~", "extraño al humano", "¡volvé!");

        // Según la hora
        add(t, "dawn", "*amanece*", "¡nuevo día!", "¡ya amanece!", "¡madrugador!", "solcito mañanero",
                "despertate~", "¡a empezar!", "¡hola sol!", "primera luz", "honk tempranero");
        add(t, "morning", "¡buen día!", "buenos días", "¡lindo día!", "¿un cocido?", "*se estira*",
                "¡arriba!", "mañanita~", "nuevo día :)", "hola mundo", "honk mañanero");
        add(t, "lunchtime", "¿almorzamos?", "¡hora de comer!", "hambre~", "pausa pa picar", "hora del ñam",
                "¿sopa paraguaya?", "¡almuercito!", "¿me das de comer?", "la panza saluda", "honk de almuerzo");
        add(t, "afternoon", "tardecita~", "¿siesta?", "día de fiaca", "¿un tereré?", "solcito rico",
                "relajado~", "tranqui", "lindo día", "contento~", "onda de tarde");
        add(t, "evening", "cae la tarde~", "¡atardecer!", "se va el día", "¿cenamos?", "a acurrucarse",
                "hora dorada", "linda noche", "bajando un cambio", "anochece~", "honk de tardecita");
        add(t, "night", "a mimir", "hora de dormir", "zzz pronto", "¡luna!", "¡estrellas!",
                "¿a la cama?", "ya oscureció", "¿trasnochamos?", "honk nocturno", "*bosteza*");
        add(t, "weekend", "¡finde!", "¡hoy no se trabaja!", "día tranqui", "relax~", "¡tiempo libre!");

        add(t, "newmonth", "¡mes nuevo!", "borrón y cuenta nueva", "¡cómo pasa el tiempo!",
                "¡nuevo comienzo!", "¡reinicio!");
        add(t, "holiday", "¡fiestas!", "¡ambiente festivo!", "¿pan dulce?", "¿regalitos?", "¡alegría!");
        add(t, "halloween", "¡BU!", "¡qué miedo!", "*fantasma*", "¿dulce o truco?", "ganso aterrador");

        // Etapas de evolución
        add(t, "evolution_egg", "...", "¿*crack*?", "calentito~", "cómodo", "durmiendo...", "...?",
                "*se mueve*");
        add(t, "evolution_hatchling", "¡pío!", "¿mamá?", "honk chiquito", "¡mundo nuevo!", "tengo miedo...",
                "¡frío!", "¡hambre!", "¿dónde estoy?", "tan chiquito", "*pío pío*");
        add(t, "evolution_gosling", "¡creciendo!", "¡ya soy grande!", "aprendiendo~", "¡curioso!",
                "¡a explorar!", "¿qué es eso?", "¡aventura!", "¡seguime!", "¡mirá, mirá!", "¡gansito!");
        add(t, "evolution_adult", "¡HONK!", "¡ya crecí!", "¡fuerte!", "seguro de mí", "ganso adulto",
                "¡maduro!", "grandote", "¡poderoso!", "con experiencia", "medio sabio");
        add(t, "evolution_elder", "*sabiduría*", "alma vieja", "recuerdos...", "vi de todo",
                "honk de anciano", "ganso sabio", "a la antigua", "experiencia", "me acuerdo...",
                "bien conservado");
        add(t, "evolution_legendary", "¡LEGENDARIO!", "¡mítico!", "¡poderoso!", "¡rarísimo!", "¡supremo!",
                "*brillando*", "ascendido", "el más capo", "¡honk épico!", "¡leyenda!");
        add(t, "evolution_cosmic", "¡CÓSMICO!", "*polvo estelar*", "universal", "¡infinito!", "trascendí",
                "nacido de estrellas", "celestial", "¡más allá!", "¡cosmos!", "¡eterno!");

        // Personalidad
        add(t, "playful", "¡a jugar!", "¡diversión!", "¡atrapame!", "¡wiii!", "¡zum!",
                "¿jugamos?", "¿querés jugar?", "¡te toca!", "¡echemos carrera!", "¡boing!");
        add(t, "mischievous", ">:)", "jejeje", "¡caos!", "*tramando*", "problemas~",
                "¡travesura!", "*planeando*", "¡bromas!", "sigiloso~", "honk malvado");
        add(t, "affectionate", "<3", "¡te quiero!", "*mimos*", "¿mimitos?", "abrazos~",
                "dulce~", "¡cariño!", "*se acurruca*", "¡amor!", "te cuido~");
        add(t, "brave", "¡sin miedo!", "¡valiente!", "¡aventura!", "¡a explorar!", "¡coraje!",
                "¡adelante!", "¡atrevido!", "¡heroico!", "¡audaz!", "¡no le temo a nada!");

        // Ánimo
        add(t, "veryhappy", "¡RE FELIZ!", ":D :D :D", "¡EL MEJOR DÍA!", "¡YUPIII!", "¡extasiado!",
                "¡chocho!", "¡UUUJUUU!", "¡increíble!", "¡perfecto!", "*bailando*");
        add(t, "happy", ":D", "¡feliz!", "¡buen día!", "lindo~", "¡contento!",
                "a gusto~", "¡yay!", "¡alegre!", "¡de buenas!", "^_^");
        add(t, "verysad", "T_T", "re triste...", "corazón roto", "destrozado", "llorando...",
                "*sollozos*", "miserable", "desesperado...", "el peor día", "roto...");

        // Aburrimiento
        add(t, "verybored", "¡RE ABURRIDO!", "nada que hacer", "qué aburrido...", "¡entreteneme!",
                "*suspira*", "uf...", "¡ABURRIDO!", "¡hacé algo!", "me muero de aburrimiento");
        add(t, "bored", "aburrido~", "mmm...", "nada...", "¿y ahora?", "*golpea la pata*",
                "esperando...", "lalala~", "*mirando fijo*", "sin hacer nada", "meh");

        // Emoción
        add(t, "excited", "¡EMOCIONADO!", "¡YAY!", "¡no aguanto más!", "¡UUUH!", "¡a full!",
                "*rebotando*", "¡increíble!", "¡re copado!", "¡emocionadísimo!", "¡con todo!");

        // Memoria de largo plazo
        add(t, "loved", "querido <3", "¡qué suerte!", "agradecido~", "¡bendecido!", "¡gracias!");
        add(t, "loyal", "¡leal!", "siempre acá", "juntos~", "¡fiel!", "¡incondicional!");
        add(t, "bestfriends", "¡BFF!", "¡mejores amigos!", "¡para siempre!", "¡almas gemelas!", "¡juntos!");

        // Reacciones a lo que acaba de pasar
        add(t, "reaction_pet", "¡más mimos!", "¡qué lindo!", "¡otra vez!", "¡me encanta!", "mmm~");
        add(t, "reaction_feed", "¡rico!", "¡delicioso!", "¡gracias!", "¡lleno!", "¡satisfecho!");
        add(t, "reaction_play", "¡divertido!", "¡otra vez!", "¡más!", "¡amo jugar!", "¡yay, juegos!");

        // Pensamientos sueltos
        add(t, AMBIENT,
                // Expresiones simples
                "...", "mmm", "?", "~", "!", "dale", ":3", "o_o", "uwu", "owo",
                // Sonidos
                "¡HONK!", "honk~", "*honk*", "¿cuac?", "¡CUAC!", "*graznido*",
                // Acciones
                "*camina como pato*", "*parpadea*", "*se acicala*", "*aletea*", "*se estira*",
                "*mira alrededor*", "*se rasca*", "*se sacude*", "*se esponja*", "*ladea la cabeza*",
                // Pensamientos
                "pensando...", "me pregunto...", "curioso~", "interesante", "¿mm?",
                "¿y si...?", "capaz...", "quizás...", "mmm...", "reflexionando",
                // Observaciones
                "lindo día", "bonito~", "en paz", "calmita~", "silencio...",
                "cómodo~", "relajado", "tranqui~", "sereno", "tranquilo",
                // Frases al azar
                "la la la~", "dubi dubi du", "tra la la", "tarareando~", "bip bup",
                "vida de ganso", "soy ganso", "momento ganso", "solo un ganso", "ganso~",
                // Tonterías
                "¿banana?", "papa", "¡porotos!", "¿fideos?", "¡chipa!",
                "¡random!", "caos~", "¡yeet!", "bro...", "¿buena onda?",
                // Filosofía de ganso
                "¿por qué ganso?", "¿sentido?", "existencia~", "pensamiento profundo", "meta",
                "¿realidad?", "sueños...", "infinito~", "vacío...", "cósmico");

        // Respuestas a acciones del usuario
        add(t, RESPONSE_PET_BEST_FRIEND, "¡mejor amigo!", "te quiero <3", "¡siempre!", "¡mimos eternos!",
                "todo tuyo~");
        add(t, RESPONSE_PET_HIGH, "<3<3<3", "¡AMOR!", "¡más!!!", "*ronronea*", "¡qué placer!",
                "¡el paraíso!", "¡perfecto!", "*se derrite*", "¡qué rico!", "¡no pares!");
        add(t, RESPONSE_PET_MID, "<3", ":)", "lindo~", "¡gracias!", "feliz~",
                "¡bien!", "mmm~", "¡me gusta!", "¡yay!", "dulce~");
        add(t, RESPONSE_PET_LOW, "...lindo", "gracias", "ok", "se agradece", "mejor");

        add(t, RESPONSE_FEED_STARVING, "¡AL FIN!", "¡RIQUÍSIMO!!!", "¡QUÉ HAMBRE!", "¡GRACIAS!",
                "¡ÑAM ÑAM ÑAM!", "¡delicioso!", "¡me salvaste!", "¡lo necesitaba!", "¡increíble!",
                "¡el paraíso!");
        add(t, RESPONSE_FEED_HUNGRY, "¡ñam!", "¡sabroso!", "¡gracias!", "¡buena comida!", "¡ñom!",
                "delicioso~", "¡qué manjar!", "¡satisfecho!", "¡ya casi lleno!", "rico~");
        add(t, RESPONSE_FEED_FULL, "lleno...", "es mucho", "re lleno", "no más", "¿después?",
                "ya comí", "panza llena", "*eructo*", "no me entra", "guardá un poco");

        add(t, RESPONSE_PLAY_HIGH, "¡YAY!", "¡DIVERTIDO!", "¡A JUGAR!!!", "¡vamos!", "¡WIIII!",
                "¡emocionado!", "¡hora de jugar!", "¡listo!", "¡dale nomás!", "¡ZUM!");
        add(t, RESPONSE_PLAY_MID, "¡ok!", "¡dale!", "jugar~", "¡divertido!", "¡juegos!",
                "vamos", "listo~", "¡sí!", "¡uh!", "¡bueno!");
        add(t, RESPONSE_PLAY_TIRED, "cansado...", "¿después?", "*bostezo*", "necesito descansar",
                "con sueño...", "sin energía", "primero descanso", "re cansado", "capaz más tarde",
                "agotado");

        add(t, RESPONSE_DRAG, "¡EPA!", "¡wiii!", "¡eh!", "mareado~", "¡uuuh!",
                "¡girando!", "¡volando!", "¡agarrate!", "¡bajame!", "¡ganso aéreo!");
        add(t, RESPONSE_GREET, "¡hola!", "¡hola! :D", "che~", "¡buenas!", "¡saludos!",
                "¡holis!", "¡mba'éichapa!", "¡llegaste!", "¡qué bueno verte!", "¿qué tal?");
        add(t, RESPONSE_FAREWELL, "¡chau chau!", "¡nos vemos!", "¡adiós!", "¡te voy a extrañar!", "¡volvé!",
                "¡hasta luego!", "¡hasta la próxima!", "¡chau!", "¡no te vayas!", "¡esperá!");
        add(t, RESPONSE_DEFAULT, "?", "!", ":)", "¡ok!", "mmm",
                "¡honk!", "~", "¡anotado!", "¡dale!", "¡sip!");

        add(t, FAVORITE_TIME, "¡mi hora favorita!", "¡las mejores horas!", "¡amo este rato!",
                "¡hora perfecta!", "¡mi momento!");

        // Hitos
        add(t, MILESTONE_PETS_1000, "¡1000 MIMOS! ¡Leyenda!");
        add(t, MILESTONE_PETS_500, "¡500 mimos! ¡BFF!");
        add(t, MILESTONE_PETS_100, "¡100 MIMOS!!! <3");
        add(t, MILESTONE_FEEDS_100, "¡100 comidas! :D");
        add(t, MILESTONE_PLAYS_100, "¡100 juegos! ¡Joya!");
        add(t, MILESTONE_DAYS_365, "¡1 AÑO! ¡INCREÍBLE!");
        add(t, MILESTONE_DAYS_100, "¡100 DÍAS!!!");
        add(t, MILESTONE_DAYS_30, "¡1 MES! ¡GUAU!");
        add(t, MILESTONE_DAYS_7, "¡1 SEMANA! <3");

        return Collections.unmodifiableMap(t);
    }

    // ============== INGLÉS (textos originales) ==============

    private static Map<String, String[]> buildEnglish() {
        Map<String, String[]> t = new HashMap<>();

        // Critical needs
        add(t, "starving", "HUNGRY!!!", "FEED ME!", "starving...", "need food!", "so hungry!",
                "BREAD?!", "dying here", "famine...", "empty belly", "FOOOOOD");
        add(t, "hungry", "hungry...", "snack?", "food plz", "feed me~", "belly empty",
                "nom nom?", "bread?", "hungry honk", "need food", "tummy rumble");
        add(t, "peckish", "snack time?", "lil hungry", "could eat", "food?", "nibbles?");

        add(t, "exhausted", "so... tired", "can't... move", "need sleep", "zzZZzz", "exhausted",
                "*collapses*", "too tired", "energy=0", "must rest", "shutdown...");
        add(t, "tired", "sleepy...", "yawn~", "tired honk", "nap time?", "zzz...",
                "*yawns*", "rest now?", "drowsy...", "need nap", "sleepy goose");
        add(t, "sleepy", "*yawn*", "bit tired", "zzz?", "nap soon", "drowsy~");

        add(t, "sad", "lonely...", "sad goose", "T_T", "notice me", "feeling down",
                "*sigh*", "miss you", "alone...", ":(", "sad honk");
        add(t, "lonely", "hello?", "anyone?", "lonely~", "miss human", "come back");

        // Time-based
        add(t, "dawn", "*sunrise*", "new day!", "dawn!", "early bird!", "morning sun",
                "wake up~", "fresh start", "hello sun!", "first light", "early honk");
        add(t, "morning", "morning!", "buenos dias", "good day!", "coffee?", "*stretch*",
                "rise shine!", "morning~", "new day :)", "hello world", "AM honk");
        add(t, "lunchtime", "lunch?", "food time!", "hungry~", "snack break", "nom time",
                "midday munch", "lunchie!", "feed me?", "belly says hi", "lunch honk");
        add(t, "afternoon", "afternoon~", "siesta?", "lazy day", "chill time", "warm sun",
                "relaxing~", "peaceful", "nice day", "content~", "PM vibes");
        add(t, "evening", "evening~", "sunset!", "day ending", "dinner?", "cozy time",
                "golden hour", "nice night", "winding down", "dusk~", "evening honk");
        add(t, "night", "night night", "sleepy time", "zzz soon", "moon!", "stars!",
                "bedtime?", "dark outside", "night owl?", "late honk", "*yawns*");
        add(t, "weekend", "weekend!", "no work!", "chill day", "relax~", "free time!");

        add(t, "newmonth", "new month!", "fresh start", "time flies!", "new begin!", "reset!");
        add(t, "holiday", "holidays!", "festive!", "cozy time", "presents?", "joy!");
        add(t, "halloween", "BOO!", "spooky!", "*ghost*", "trick treat?", "scary goose");

        // Evolution stages
        add(t, "evolution_egg", "...", "*crack?*", "warm~", "cozy", "sleeping...", "...?", "*wiggle*");
        add(t, "evolution_hatchling", "peep!", "mama?", "tiny honk", "new world!", "scared...",
                "cold!", "hungry!", "where am i", "so small", "*chirp*");
        add(t, "evolution_gosling", "growing!", "big now!", "learning~", "curious!", "explore!",
                "what's that?", "adventure!", "follow me!", "look look!", "gosling!");
        add(t, "evolution_adult", "HONK!", "grown up!", "strong!", "confident", "adult goose",
                "mature!", "full grown", "powerful!", "experienced", "wise-ish");
        add(t, "evolution_elder", "*wisdom*", "old soul", "memories...", "seen much", "elder honk",
                "wise goose", "ancient ways", "experience", "remember...", "aged well");
        add(t, "evolution_legendary", "LEGENDARY!", "mythical!", "powerful!", "rare!", "supreme!",
                "*glowing*", "ascended", "ultimate", "epic honk!", "legend!");
        add(t, "evolution_cosmic", "COSMIC!", "*stardust*", "universal", "infinite!", "transcend",
                "starborn", "celestial", "beyond!", "cosmos!", "eternal!");

        // Personality
        add(t, "playful", "play!", "fun time!", "catch me!", "wheee!", "zoom!",
                "games?", "let's play!", "tag!", "race me!", "boing!");
        add(t, "mischievous", ">:)", "hehehe", "chaos!", "*scheming*", "trouble~",
                "mischief!", "*plotting*", "pranks!", "sneaky~", "evil honk");
        add(t, "affectionate", "<3", "love you!", "*nuzzle*", "cuddles?", "hugs~",
                "sweet~", "affection!", "*snuggle*", "love!", "caring~");
        add(t, "brave", "no fear!", "brave!", "adventure!", "explore!", "courage!",
                "forward!", "daring!", "heroic!", "bold!", "fearless!");

        // Mood
        add(t, "veryhappy", "SO HAPPY!", ":D :D :D", "BEST DAY!", "YAAAY!", "ecstatic!",
                "overjoyed!", "WOOHOO!", "amazing!", "perfect!", "*dancing*");
        add(t, "happy", ":D", "happy!", "good day!", "nice~", "content!",
                "pleased~", "yay!", "joyful!", "good mood!", "^_^");
        add(t, "verysad", "T_T", "so sad...", "heartbroken", "devastated", "crying...",
                "*sobbing*", "miserable", "despair...", "worst day", "broken...");

        // Boredom
        add(t, "verybored", "SO BORED!", "nothing to do", "boring...", "entertain me!",
                "*sighs*", "ugh...", "BORED!", "do something!", "dying of bored");
        add(t, "bored", "bored~", "hmm...", "nothing...", "what now?", "*taps foot*",
                "waiting...", "lalala~", "*staring*", "idle...", "meh");

        // Excitement
        add(t, "excited", "EXCITED!", "YAY!", "can't wait!", "WOOO!", "hyped!",
                "*bouncing*", "amazing!", "so cool!", "thrilled!", "pumped!");

        // Memory-based
        add(t, "loved", "loved <3", "so lucky!", "grateful~", "blessed!", "thank you!");
        add(t, "loyal", "loyal!", "always here", "together~", "faithful!", "devoted!");
        add(t, "bestfriends", "BFF!", "besties!", "forever!", "soulmates!", "together!");

        // Reactions
        add(t, "reaction_pet", "more pets!", "that's nice!", "again!", "love it!", "mmm~");
        add(t, "reaction_feed", "yummy!", "delicious!", "thanks!", "full!", "satisfied!");
        add(t, "reaction_play", "fun!", "again!", "more!", "love play!", "yay games!");

        // Ambient
        add(t, AMBIENT,
                // Simple expressions
                "...", "hmm", "?", "~", "!", "ok", ":3", "o_o", "uwu", "owo",
                // Sounds
                "HONK!", "honk~", "*honk*", "quack?", "QUACK!", "*squawk*",
                // Actions
                "*waddle*", "*blink*", "*preen*", "*flap*", "*stretch*",
                "*look around*", "*scratch*", "*shake*", "*ruffle*", "*tilt head*",
                // Thoughts
                "thinking...", "wonder...", "curious~", "interesting", "hm?",
                "what if...", "maybe...", "perhaps...", "hmm...", "pondering",
                // Observations
                "nice day", "pretty~", "peaceful", "calm~", "quiet...",
                "cozy~", "relaxed", "chill~", "serene", "tranquil",
                // Random phrases
                "la la la~", "doo bee doo", "tra la la", "humming~", "bee boop",
                "goose life", "am goose", "goose moment", "just goose", "goose~",
                // Silly
                "banana?", "potato", "beans!", "spaghetti?", "waffles!",
                "random!", "chaos~", "yeet!", "bruh", "vibe check",
                // Philosophical
                "why goose?", "meaning?", "existence~", "deep thoughts", "meta",
                "reality?", "dreams...", "infinity~", "void...", "cosmic");

        // Responses
        add(t, RESPONSE_PET_BEST_FRIEND, "best friend!", "love you <3", "always!", "forever pets!", "devoted~");
        add(t, RESPONSE_PET_HIGH, "<3<3<3", "LOVE!", "more!!!", "purr~", "bliss!",
                "heaven!", "perfect!", "*melts*", "so good!", "don't stop!");
        add(t, RESPONSE_PET_MID, "<3", ":)", "nice~", "thanks!", "happy~",
                "good!", "mmm~", "like it!", "yay!", "sweet~");
        add(t, RESPONSE_PET_LOW, "...nice", "thanks", "ok", "appreciated", "better");

        add(t, RESPONSE_FEED_STARVING, "FINALLY!", "YUMMY!!!", "SO HUNGRY!", "THANK YOU!", "NOM NOM NOM!",
                "delicious!", "life saver!", "needed this!", "amazing!", "heaven!");
        add(t, RESPONSE_FEED_HUNGRY, "yum!", "tasty!", "thanks!", "good food!", "nom!",
                "delicious~", "nice meal!", "satisfied!", "full soon!", "yummy~");
        add(t, RESPONSE_FEED_FULL, "full...", "too much", "stuffed", "no more", "later?",
                "already ate", "belly full", "*burp*", "can't eat", "save some");

        add(t, RESPONSE_PLAY_HIGH, "YAY!", "FUN!", "PLAY!!!", "let's go!", "WHEEE!",
                "excited!", "game time!", "ready!", "bring it!", "ZOOM!");
        add(t, RESPONSE_PLAY_MID, "ok!", "sure!", "play~", "fun!", "games!",
                "let's go", "ready~", "yeah!", "woo!", "alright!");
        add(t, RESPONSE_PLAY_TIRED, "tired...", "later?", "*yawn*", "need rest", "sleepy...",
                "no energy", "rest first", "too tired", "maybe later", "exhausted");

        add(t, RESPONSE_DRAG, "WHOA!", "wheee!", "hey!", "dizzy~", "wooo!",
                "spinning!", "flying!", "hold on!", "weeee!", "air goose!");
        add(t, RESPONSE_GREET, "hello!", "hi! :D", "hey~", "hola!", "greetings!",
                "heya!", "howdy!", "welcome!", "good to see!", "yo!");
        add(t, RESPONSE_FAREWELL, "bye bye!", "see ya!", "adios!", "miss you!", "come back!",
                "farewell!", "later!", "goodbye!", "don't go!", "wait!");
        add(t, RESPONSE_DEFAULT, "?", "!", ":)", "ok!", "hmm",
                "honk!", "~", "noted!", "sure!", "yep!");

        add(t, FAVORITE_TIME, "fav time!", "best hours!", "love now!", "perfect time!", "my moment!");

        // Milestones
        add(t, MILESTONE_PETS_1000, "1000 PETS! Legend!");
        add(t, MILESTONE_PETS_500, "500 pets! BFF!");
        add(t, MILESTONE_PETS_100, "100 PETS!!! <3");
        add(t, MILESTONE_FEEDS_100, "100 meals! :D");
        add(t, MILESTONE_PLAYS_100, "100 games! Fun!");
        add(t, MILESTONE_DAYS_365, "1 YEAR! AMAZING!");
        add(t, MILESTONE_DAYS_100, "100 DAYS!!!");
        add(t, MILESTONE_DAYS_30, "1 MONTH! WOW!");
        add(t, MILESTONE_DAYS_7, "1 WEEK! <3");

        return Collections.unmodifiableMap(t);
    }

    // ============== EXPRESIONES CORTAS (inglés -> español) ==============

    // Salen de GooseAI, GooseBehaviorTree, GooseTouchHandler, TheGoose, GooseSystemReactions y
    // GooseEasterEggs. Caritas, "ZZZ", "LA LA LA", "HONK" sin signo, "jaja" y "<3" quedan igual.
    private static Map<String, String> buildSpanishExpressions() {
        Map<String, String> t = new HashMap<>();

        // Hambre y comida
        addExpression(t, "hungry", "hambre");
        addExpression(t, "hungry...", "hambre...");
        addExpression(t, "hungry!", "¡hambre!");
        addExpression(t, "HUNGRY!", "¡HAMBRE!");
        addExpression(t, "food?", "¿comida?");
        addExpression(t, "bread?", "¿pan?");
        addExpression(t, "BREAD?", "¿PAN?");
        addExpression(t, "BREAD!", "¡PAN!");
        addExpression(t, "feed me!", "¡dame de comer!");
        addExpression(t, "snack?", "¿algo pa picar?");
        addExpression(t, "*stomach growl*", "*ruge la panza*");
        addExpression(t, "YUM", "ÑAM");
        addExpression(t, "lunch?", "¿almorzamos?");
        addExpression(t, "snack time!", "¡hora de picar!");
        addExpression(t, "food time?", "¿hora de comer?");

        // Sueño y hora del día
        addExpression(t, "sleepy...", "tengo sueño...");
        addExpression(t, "*yawn*", "*bosteza*");
        addExpression(t, "tired...", "cansado...");
        addExpression(t, "nap time?", "¿siestita?");
        addExpression(t, "bedtime?", "¿a la cama?");
        addExpression(t, "zzz?", "¿zzz?");
        addExpression(t, "ZZZ?", "¿ZZZ?");
        addExpression(t, "morning!", "¡buen día!");
        addExpression(t, "coffee?", "¿un cocido?");
        addExpression(t, "good day!", "¡lindo día!");

        // Soledad, tristeza y aburrimiento
        addExpression(t, "lonely...", "solito...");
        addExpression(t, "*sigh*", "*suspira*");
        addExpression(t, "*sniff*", "*snif*");
        addExpression(t, "why...", "¿por qué...?");
        addExpression(t, "sad goose", "ganso triste");
        addExpression(t, "hello?", "¿hola?");
        addExpression(t, "pet me?", "¿me mimás?");
        addExpression(t, "PET ME", "MIMAME");
        addExpression(t, "*poke*", "*toquecito*");
        addExpression(t, "notice me!", "¡haceme caso!");
        addExpression(t, "BORED", "ABURRIDO");

        // Alegría y cariño
        addExpression(t, "YAY", "YUPI");
        addExpression(t, "YAY!", "¡YUPI!");
        addExpression(t, "hehe", "jeje");
        addExpression(t, "hehe!", "¡jeje!");
        addExpression(t, "hehehe", "jejeje");
        addExpression(t, "WOOP!", "¡YUJU!");
        addExpression(t, "HAPPY!", "¡FELIZ!");
        addExpression(t, "*happy*", "*feliz*");
        addExpression(t, "~happy~", "~feliz~");
        addExpression(t, "best day!", "¡el mejor día!");
        addExpression(t, "LOVE!", "¡AMOR!");
        addExpression(t, "love u!", "¡te quiero!");
        addExpression(t, "*nuzzle*", "*mimos*");
        addExpression(t, "BEST!", "¡LO MÁS!");
        addExpression(t, "purr~", "*ronronea*");
        addExpression(t, "bliss!", "¡qué placer!");
        addExpression(t, "BLISS!", "¡QUÉ PLACER!");
        addExpression(t, "heaven~", "el paraíso~");
        addExpression(t, "thanks!", "¡gracias!");
        addExpression(t, "HI!", "¡HOLA!");

        // Juego, movimiento y toques
        addExpression(t, "ZOOM!", "¡ZUM!");
        addExpression(t, "zoom!", "¡zum!");
        addExpression(t, "WHEEE!", "¡WIIII!");
        addExpression(t, "wheee!", "¡wiii!");
        addExpression(t, "wheee~", "wiii~");
        addExpression(t, "~dance~", "~baile~");
        addExpression(t, "stretch~", "estiro~");
        addExpression(t, "*peck*", "*picotea*");
        addExpression(t, "*preen*", "*se acicala*");
        addExpression(t, "*waddle*", "*se bambolea*");
        addExpression(t, "*bounce*", "*rebota*");
        addExpression(t, "*boop*", "*bup*");
        addExpression(t, "dizzy~", "mareado~");
        addExpression(t, "whoa!", "¡epa!");
        addExpression(t, "WHOA!", "¡EPA!");
        addExpression(t, "oof!", "¡uf!");
        addExpression(t, "hey!", "¡eh!");
        addExpression(t, "HEY!", "¡EH!");
        addExpression(t, "oh!", "¡oh!");
        addExpression(t, "ok!", "¡listo!");
        addExpression(t, "hmm", "mmm");
        addExpression(t, "AGAIN!", "¡OTRA VEZ!");
        addExpression(t, "HONK!", "¡HONK!");

        // Eventos al azar y evolución
        addExpression(t, "EGG!", "¡HUEVO!");
        addExpression(t, "FIGHT!", "¡PELEA!");
        addExpression(t, "NEW!", "¡NUEVO!");
        addExpression(t, "SMOOTH", "SUAVECITO");
        addExpression(t, "FLOP!", "¡PLAF!");
        addExpression(t, "TADA!", "¡TARÁN!");
        addExpression(t, "EVOLVED!", "¡EVOLUCIONÉ!");

        // Reacciones del sistema (batería, combos) y fechas especiales
        addExpression(t, "PHEW", "UFF");
        addExpression(t, "FULL!", "¡A FULL!");
        addExpression(t, "WOW!", "¡GUAU!");
        addExpression(t, "NICE!", "¡JOYA!");
        addExpression(t, "PERFECT!", "¡PERFECTO!");
        addExpression(t, "BOO!", "¡BU!");
        addExpression(t, "MERRY HONKMAS!", "¡FELIZ NAVIHONK!");
        addExpression(t, "HAPPY NEW HONK!", "¡FELIZ AÑO HONK!");
        addExpression(t, "PRANKED!", "¡CAÍSTE!");

        return Collections.unmodifiableMap(t);
    }
}

package com.cfks.goosedroid.GooseDesktop;

import android.graphics.*;
import com.cfks.goosedroid.PetNeeds;
import com.cfks.goosedroid.core.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Sistema de minijuegos para la mascota.
 * Incluye juegos de alimentar, perseguir, atrapar, y rhythm games.
 */
public class MiniGames {

    private static final String TAG = "MiniGames";

    // ============== TIPOS DE JUEGO ==============

    public enum GameType {
        FEEDING("Feeding Time", "Tap food to feed the goose!", 50, 30),
        CHASING("Chase Game", "Catch the targets!", 5, 20),
        CATCHING("Catch & Collect", "Catch falling items!", 100, 30),
        HIDE_SEEK("Hide & Seek", "Find the goose!", 3, 45),
        HONK_HERO("Honk Hero", "Tap to the rhythm!", 100, 40),
        OBSTACLE_RUN("Obstacle Run", "Avoid the obstacles!", 60, 30),
        MEMORY_HONK("Memory Honk", "Repeat the pattern!", 5, 60);

        public final String name;
        public final String description;
        public final int targetScore;
        public final int timeLimit;

        GameType(String name, String description, int targetScore, int timeLimit) {
            this.name = name;
            this.description = description;
            this.targetScore = targetScore;
            this.timeLimit = timeLimit;
        }
    }

    // ============== OBJETOS DEL JUEGO ==============

    public static class GameObject {
        public float x, y;
        public float width, height;
        public int points;
        public int color;
        public String emoji;
        public float velocityY;
        public float lifetime;
        public boolean active = true;

        public GameObject(float x, float y, float size, int points, int color, String emoji) {
            this.x = x;
            this.y = y;
            this.width = size;
            this.height = size;
            this.points = points;
            this.color = color;
            this.emoji = emoji;
            this.velocityY = 0;
            this.lifetime = 5f;
        }

        public boolean contains(float px, float py) {
            return px >= x - width/2 && px <= x + width/2 &&
                   py >= y - height/2 && py <= y + height/2;
        }
    }

    // ============== ESTADO ==============

    private static GameType currentGame = null;
    private static boolean isPlaying = false;
    private static int score = 0;
    private static float timeRemaining = 0;
    private static float gameTime = 0;

    private static int screenWidth = 1080;
    private static int screenHeight = 1920;

    private static List<GameObject> gameObjects = new ArrayList<>();
    private static Random random = new Random();

    // Honk Hero específico
    private static List<Integer> honkPattern = new ArrayList<>();
    private static List<Integer> playerPattern = new ArrayList<>();
    private static int currentPatternIndex = 0;
    private static boolean showingPattern = false;
    private static float patternTimer = 0;
    private static int patternRound = 1;

    // Memory específico
    private static int memoryLevel = 1;

    // Obstacle Run específico
    private static float playerY = 0;
    private static float obstacleSpeed = 200f;

    // Callback
    private static GameCallback callback;

    // ============== CALLBACK ==============

    public interface GameCallback {
        void onGameStarted(GameType game);
        void onGameEnded(GameType game, int score, boolean won);
        void onScoreChanged(int newScore);
        Vec2 getGoosePosition();
        void setGooseTarget(Vec2 target);
        void triggerHappiness(float amount);
    }

    public static void setCallback(GameCallback cb) {
        callback = cb;
    }

    public static void setScreenSize(int width, int height) {
        screenWidth = width;
        screenHeight = height;
    }

    // ============== CONTROL ==============

    public static void startGame(GameType type) {
        currentGame = type;
        isPlaying = true;
        score = 0;
        timeRemaining = type.timeLimit;
        gameTime = 0;
        gameObjects.clear();

        // Reset específicos
        honkPattern.clear();
        playerPattern.clear();
        currentPatternIndex = 0;
        showingPattern = false;
        patternTimer = 0;
        patternRound = 1;
        memoryLevel = 1;
        playerY = screenHeight / 2f;
        obstacleSpeed = 200f;

        // Inicializar según tipo
        switch (type) {
            case FEEDING:
                spawnFoodItems(5);
                break;
            case CHASING:
                spawnChaseTarget();
                break;
            case CATCHING:
                // Items spawn during update
                break;
            case HIDE_SEEK:
                // Goose hides itself
                break;
            case HONK_HERO:
                generateNewPattern();
                break;
            case OBSTACLE_RUN:
                spawnObstacle();
                break;
            case MEMORY_HONK:
                generateMemoryPattern();
                break;
        }

        if (callback != null) {
            callback.onGameStarted(type);
        }

        Sound.playful();
    }

    /** Termina el juego en curso sin premio, sonido ni callback. */
    public static void cancelGame() {
        isPlaying = false;
        currentGame = null;
    }

    public static void endGame(boolean won) {
        isPlaying = false;

        if (won) {
            PetNeeds.get().happiness = Math.min(100, PetNeeds.get().happiness + 20);
            Sound.achievement();
        } else {
            Sound.sad();
        }

        if (callback != null) {
            callback.onGameEnded(currentGame, score, won);
        }

        currentGame = null;
    }

    public static boolean isPlaying() {
        return isPlaying;
    }

    public static GameType getCurrentGame() {
        return currentGame;
    }

    public static int getScore() {
        return score;
    }

    public static float getTimeRemaining() {
        return timeRemaining;
    }

    // ============== UPDATE ==============

    public static void update(float deltaTime) {
        if (!isPlaying || currentGame == null) return;

        gameTime += deltaTime;
        timeRemaining -= deltaTime;

        // Check timeout
        if (timeRemaining <= 0) {
            endGame(score >= currentGame.targetScore);
            return;
        }

        // Update based on game type
        switch (currentGame) {
            case FEEDING:
                updateFeeding(deltaTime);
                break;
            case CHASING:
                updateChasing(deltaTime);
                break;
            case CATCHING:
                updateCatching(deltaTime);
                break;
            case HIDE_SEEK:
                updateHideSeek(deltaTime);
                break;
            case HONK_HERO:
                updateHonkHero(deltaTime);
                break;
            case OBSTACLE_RUN:
                updateObstacleRun(deltaTime);
                break;
            case MEMORY_HONK:
                updateMemoryHonk(deltaTime);
                break;
        }

        // Check win condition
        if (score >= currentGame.targetScore) {
            endGame(true);
        }
    }

    // ============== GAME-SPECIFIC UPDATES ==============

    private static void updateFeeding(float deltaTime) {
        // Remove expired food
        gameObjects.removeIf(obj -> {
            obj.lifetime -= deltaTime;
            return obj.lifetime <= 0 || !obj.active;
        });

        // Spawn new food if needed
        if (gameObjects.size() < 3 && random.nextFloat() < 0.05f) {
            spawnFoodItems(1);
        }
    }

    private static void updateChasing(float deltaTime) {
        // Move targets slightly
        for (GameObject obj : gameObjects) {
            if (obj.active) {
                obj.x += (random.nextFloat() - 0.5f) * 50f * deltaTime;
                obj.y += (random.nextFloat() - 0.5f) * 50f * deltaTime;

                // Keep in bounds
                obj.x = Math.max(50, Math.min(screenWidth - 50, obj.x));
                obj.y = Math.max(100, Math.min(screenHeight - 100, obj.y));
            }
        }

        gameObjects.removeIf(obj -> !obj.active);

        if (gameObjects.isEmpty()) {
            spawnChaseTarget();
        }
    }

    private static void updateCatching(float deltaTime) {
        // Move falling objects
        for (GameObject obj : gameObjects) {
            obj.y += obj.velocityY * deltaTime;

            // Remove if off screen
            if (obj.y > screenHeight + 50) {
                obj.active = false;
            }
        }

        gameObjects.removeIf(obj -> !obj.active);

        // Spawn new objects
        if (random.nextFloat() < 0.03f) {
            spawnCatchableItem();
        }
    }

    private static void updateHideSeek(float deltaTime) {
        // Goose occasionally peeks
        if (gameObjects.isEmpty() && random.nextFloat() < 0.02f) {
            spawnHidingSpot();
        }
    }

    private static void updateHonkHero(float deltaTime) {
        if (showingPattern) {
            patternTimer += deltaTime;

            // Show each note for 0.5 seconds
            int noteToShow = (int)(patternTimer / 0.6f);
            if (noteToShow >= honkPattern.size()) {
                showingPattern = false;
                patternTimer = 0;
                currentPatternIndex = 0;
            } else {
                // Play honk for current note
                if ((int)((patternTimer - deltaTime) / 0.6f) != noteToShow) {
                    Sound.honk();
                }
            }
        }
    }

    private static void updateObstacleRun(float deltaTime) {
        // Move obstacles
        for (GameObject obj : gameObjects) {
            obj.x -= obstacleSpeed * deltaTime;

            // Remove if off screen
            if (obj.x < -50) {
                obj.active = false;
                addScore(1); // Survived an obstacle
            }
        }

        gameObjects.removeIf(obj -> !obj.active);

        // Spawn new obstacles
        if (random.nextFloat() < 0.02f) {
            spawnObstacle();
        }

        // Increase difficulty
        obstacleSpeed += deltaTime * 5f;
    }

    private static void updateMemoryHonk(float deltaTime) {
        if (showingPattern) {
            patternTimer += deltaTime;

            int noteToShow = (int)(patternTimer / 0.8f);
            if (noteToShow >= honkPattern.size()) {
                showingPattern = false;
                patternTimer = 0;
            } else if ((int)((patternTimer - deltaTime) / 0.8f) != noteToShow) {
                Sound.honk();
            }
        }
    }

    // ============== SPAWN FUNCTIONS ==============

    private static void spawnFoodItems(int count) {
        String[] foodEmojis = {"BREAD", "CORN", "LEAF", "FISH"};
        int[] foodPoints = {10, 15, 20, 30};
        int[] foodColors = {0xFFDEB887, 0xFFFFD700, 0xFF90EE90, 0xFF87CEEB};

        for (int i = 0; i < count; i++) {
            int type = random.nextInt(foodEmojis.length);
            float x = random.nextFloat() * (screenWidth - 100) + 50;
            float y = random.nextFloat() * (screenHeight - 200) + 100;

            GameObject food = new GameObject(x, y, 60, foodPoints[type],
                    foodColors[type], foodEmojis[type]);
            food.lifetime = 10f;
            gameObjects.add(food);
        }
    }

    private static void spawnChaseTarget() {
        float x = random.nextFloat() * (screenWidth - 100) + 50;
        float y = random.nextFloat() * (screenHeight - 200) + 100;

        GameObject target = new GameObject(x, y, 80, 1, 0xFFFF6B6B, "TARGET");
        gameObjects.add(target);

        // Move goose towards target
        if (callback != null) {
            callback.setGooseTarget(new Vec2(x, y));
        }
    }

    private static void spawnCatchableItem() {
        String[] items = {"STAR", "HEART", "COIN", "GEM"};
        int[] points = {10, 15, 20, 50};
        int[] colors = {0xFFFFD700, 0xFFFF69B4, 0xFFFFC125, 0xFF00CED1};

        int type = random.nextInt(items.length);
        float x = random.nextFloat() * (screenWidth - 100) + 50;

        GameObject item = new GameObject(x, -50, 50, points[type],
                colors[type], items[type]);
        item.velocityY = 150 + random.nextFloat() * 100;
        gameObjects.add(item);
    }

    private static void spawnHidingSpot() {
        float x = random.nextFloat() * (screenWidth - 150) + 75;
        float y = random.nextFloat() * (screenHeight - 300) + 150;

        GameObject spot = new GameObject(x, y, 100, 1, 0xFF8B4513, "PEEK");
        spot.lifetime = 3f;
        gameObjects.add(spot);
    }

    private static void spawnObstacle() {
        float y = random.nextFloat() * (screenHeight - 200) + 100;
        int type = random.nextInt(3);

        String[] obstacleTypes = {"ROCK", "FENCE", "BROOM"};
        int[] colors = {0xFF696969, 0xFF8B4513, 0xFFA0522D};

        GameObject obstacle = new GameObject(screenWidth + 50, y, 70, 0,
                colors[type], obstacleTypes[type]);
        gameObjects.add(obstacle);
    }

    private static void generateNewPattern() {
        honkPattern.clear();
        for (int i = 0; i < 2 + patternRound; i++) {
            honkPattern.add(random.nextInt(4)); // 4 possible positions
        }
        showingPattern = true;
        patternTimer = 0;
    }

    private static void generateMemoryPattern() {
        honkPattern.clear();
        playerPattern.clear();
        for (int i = 0; i < memoryLevel + 2; i++) {
            honkPattern.add(random.nextInt(4));
        }
        showingPattern = true;
        patternTimer = 0;
        currentPatternIndex = 0;
    }

    // ============== INPUT ==============

    public static void onTouch(float x, float y) {
        if (!isPlaying || currentGame == null) return;

        switch (currentGame) {
            case FEEDING:
            case CATCHING:
            case HIDE_SEEK:
                // Check if touched any game object
                for (GameObject obj : gameObjects) {
                    if (obj.active && obj.contains(x, y)) {
                        obj.active = false;
                        addScore(obj.points);
                        Sound.score();
                        break;
                    }
                }
                break;

            case CHASING:
                // Check if touched target
                for (GameObject obj : gameObjects) {
                    if (obj.active && obj.contains(x, y)) {
                        obj.active = false;
                        addScore(obj.points);
                        Sound.score();
                        break;
                    }
                }
                break;

            case HONK_HERO:
                if (!showingPattern) {
                    // Determine which quadrant was tapped
                    int quadrant = getQuadrant(x, y);
                    checkHonkHeroInput(quadrant);
                }
                break;

            case OBSTACLE_RUN:
                // Tap to jump (move player Y)
                playerY = y;
                // Check collision with obstacles
                for (GameObject obj : gameObjects) {
                    if (obj.active && Math.abs(obj.y - playerY) < 50 && obj.x < 200) {
                        // Hit obstacle!
                        endGame(false);
                        return;
                    }
                }
                break;

            case MEMORY_HONK:
                if (!showingPattern) {
                    int quadrant = getQuadrant(x, y);
                    checkMemoryInput(quadrant);
                }
                break;
        }
    }

    private static int getQuadrant(float x, float y) {
        int qx = x < screenWidth / 2f ? 0 : 1;
        int qy = y < screenHeight / 2f ? 0 : 1;
        return qy * 2 + qx;
    }

    private static void checkHonkHeroInput(int quadrant) {
        if (currentPatternIndex < honkPattern.size()) {
            if (honkPattern.get(currentPatternIndex) == quadrant) {
                // Correct!
                currentPatternIndex++;
                addScore(10);
                Sound.honk();

                if (currentPatternIndex >= honkPattern.size()) {
                    // Completed pattern
                    patternRound++;
                    generateNewPattern();
                }
            } else {
                // Wrong!
                Sound.sad();
                // Reset pattern
                currentPatternIndex = 0;
                playerPattern.clear();
            }
        }
    }

    private static void checkMemoryInput(int quadrant) {
        Sound.honk();
        playerPattern.add(quadrant);

        int idx = playerPattern.size() - 1;
        if (idx < honkPattern.size()) {
            if (!honkPattern.get(idx).equals(playerPattern.get(idx))) {
                // Wrong!
                Sound.sad();
                playerPattern.clear();
                currentPatternIndex = 0;
                // Show pattern again
                showingPattern = true;
                patternTimer = 0;
            } else if (playerPattern.size() == honkPattern.size()) {
                // Completed!
                addScore(1);
                memoryLevel++;
                generateMemoryPattern();
            }
        }
    }

    private static void addScore(int points) {
        score += points;
        if (callback != null) {
            callback.onScoreChanged(score);
        }
    }

    // ============== RENDER ==============

    public static void render(Canvas canvas) {
        if (!isPlaying || currentGame == null) return;

        Paint paint = new Paint();
        paint.setAntiAlias(true);

        // Draw game UI background
        paint.setColor(0x88000000);
        canvas.drawRect(0, 0, screenWidth, 80, paint);

        // Draw game info
        paint.setColor(0xFFFFFFFF);
        paint.setTextSize(24);
        paint.setTextAlign(Paint.Align.LEFT);
        canvas.drawText(currentGame.name, 20, 30, paint);

        paint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("Score: " + score + "/" + currentGame.targetScore,
                screenWidth / 2f, 30, paint);

        paint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText("Time: " + (int)timeRemaining + "s",
                screenWidth - 20, 30, paint);

        // Progress bar
        paint.setColor(0xFF333333);
        canvas.drawRect(20, 50, screenWidth - 20, 70, paint);
        float progress = (float)score / currentGame.targetScore;
        paint.setColor(0xFF4CAF50);
        canvas.drawRect(20, 50, 20 + (screenWidth - 40) * Math.min(1, progress), 70, paint);

        // Draw game objects
        for (GameObject obj : gameObjects) {
            if (obj.active) {
                paint.setColor(obj.color);
                canvas.drawCircle(obj.x, obj.y, obj.width / 2, paint);

                // Draw emoji/label
                paint.setColor(0xFFFFFFFF);
                paint.setTextSize(16);
                paint.setTextAlign(Paint.Align.CENTER);
                canvas.drawText(obj.emoji, obj.x, obj.y + 5, paint);
            }
        }

        // Game-specific rendering
        switch (currentGame) {
            case HONK_HERO:
            case MEMORY_HONK:
                renderHonkHeroUI(canvas, paint);
                break;
            case OBSTACLE_RUN:
                renderObstacleRunUI(canvas, paint);
                break;
        }
    }

    private static void renderHonkHeroUI(Canvas canvas, Paint paint) {
        // Draw 4 quadrants
        int[] quadrantColors = {0xFFFF6B6B, 0xFF4ECDC4, 0xFFFFE66D, 0xFF95E1D3};

        for (int i = 0; i < 4; i++) {
            int qx = i % 2;
            int qy = i / 2;

            float x1 = qx * screenWidth / 2f + 50;
            float y1 = qy * screenHeight / 2f + 100;
            float x2 = x1 + screenWidth / 2f - 100;
            float y2 = y1 + screenHeight / 2f - 150;

            // Highlight if showing pattern
            if (showingPattern) {
                int noteShowing = (int)(patternTimer / 0.6f);
                if (noteShowing < honkPattern.size() && honkPattern.get(noteShowing) == i) {
                    paint.setColor(0xFFFFFFFF);
                } else {
                    paint.setColor(quadrantColors[i] & 0x88FFFFFF);
                }
            } else {
                paint.setColor(quadrantColors[i]);
            }

            canvas.drawRoundRect(x1, y1, x2, y2, 20, 20, paint);
        }

        // Show pattern progress
        paint.setColor(0xFFFFFFFF);
        paint.setTextSize(20);
        paint.setTextAlign(Paint.Align.CENTER);
        if (showingPattern) {
            canvas.drawText("Watch the pattern!", screenWidth / 2f, screenHeight - 50, paint);
        } else {
            canvas.drawText("Repeat! " + currentPatternIndex + "/" + honkPattern.size(),
                    screenWidth / 2f, screenHeight - 50, paint);
        }
    }

    private static void renderObstacleRunUI(Canvas canvas, Paint paint) {
        // Draw player (goose position indicator)
        paint.setColor(0xFFFFFFFF);
        canvas.drawCircle(100, playerY, 30, paint);

        paint.setColor(0xFFFFA500);
        paint.setTextSize(20);
        paint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("TAP to move!", screenWidth / 2f, screenHeight - 50, paint);
    }
}

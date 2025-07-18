package net.gf.radio24

import android.content.Context
import android.content.Intent
import android.graphics.*
import android.os.Bundle
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.*
import kotlin.random.Random

class SnakeGame : AppCompatActivity() {
    private lateinit var gameView: OptimizedGameView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                )

        gameView = OptimizedGameView(this)
        setContentView(gameView)
    }

    fun goBackToMain() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
        overridePendingTransition(0, 0)
    }

    override fun onResume() {
        super.onResume()
        gameView.resume()
    }

    override fun onPause() {
        super.onPause()
        gameView.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        gameView.cleanup()
    }
}

class OptimizedGameView(context: Context) : SurfaceView(context), SurfaceHolder.Callback {
    private val activity = context as SnakeGame

    private var gameThread: GameThread? = null
    private var isPlaying = false

    private var blockSize = 0
    private var numBlocksWide = 0
    private var numBlocksHigh = 0

    private var snake = mutableListOf<Point>()
    private var food = Point()
    private var score = 0
    private var level = 1
    private var gameRunning = false
    private var gamePaused = false
    private var gameStarted = false
    private var gameOver = false
    private var gameSpeed = 200L
    private var lastUpdateTime = 0L

    private var direction = Direction.RIGHT
    private var nextDirection = Direction.RIGHT

    private var particles = mutableListOf<Particle>()
    private var animationFrame = 0f
    private var backgroundAlpha = 0f

    private var touchStartX = 0f
    private var touchStartY = 0f
    private val minSwipeDistance = 100f

    private var pauseButtonRect = RectF()
    private var backButtonRect = RectF()

    private val paintCache = PaintCache()

    private var foodGradient: RadialGradient? = null
    private var snakeHeadGradient: RadialGradient? = null

    private var needsFullRedraw = true

    init {
        holder.addCallback(this)
        isFocusable = true
    }

    enum class Direction {
        UP, DOWN, LEFT, RIGHT
    }

    data class Particle(
        var x: Float,
        var y: Float,
        var vx: Float,
        var vy: Float,
        var life: Int,
        val maxLife: Int,
        val color: Int
    )

    private class PaintCache {
        val snakeBodyPaint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.FILL
        }

        val snakeHeadPaint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.FILL
        }

        val foodPaint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.FILL
        }

        val textPaint = Paint().apply {
            isAntiAlias = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.LEFT
        }

        val gameOverTextPaint = Paint().apply {
            isAntiAlias = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            color = Color.WHITE
        }

        val instructionTextPaint = Paint().apply {
            isAntiAlias = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
            color = Color.LTGRAY
        }

        val gridPaint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeWidth = 1f
            color = Color.argb(30, 0, 255, 0)
        }

        val buttonPaint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.FILL
        }

        val buttonTextPaint = Paint().apply {
            isAntiAlias = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            color = Color.WHITE
        }

        val particlePaint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.FILL
        }

        val backgroundPaint = Paint().apply {
            style = Paint.Style.FILL
        }

        val hudPaint = Paint().apply {
            color = Color.argb(150, 0, 0, 0)
        }
    }

    private inner class GameThread : Thread() {
        private val targetFPS = 60
        private val targetTime = 1000 / targetFPS

        override fun run() {
            var startTime: Long
            var timeMillis: Long
            var waitTime: Long

            while (isPlaying) {
                startTime = System.nanoTime()

                if (!gamePaused) {
                    update()
                }
                draw()

                timeMillis = (System.nanoTime() - startTime) / 1000000
                waitTime = targetTime - timeMillis

                if (waitTime > 0) {
                    try {
                        sleep(waitTime)
                    } catch (e: InterruptedException) {
                        break
                    }
                }
            }
        }
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        blockSize = minOf(width, height) / 25
        numBlocksWide = width / blockSize
        numBlocksHigh = height / blockSize

        paintCache.textPaint.textSize = blockSize * 0.8f
        paintCache.gameOverTextPaint.textSize = blockSize * 1.5f
        paintCache.instructionTextPaint.textSize = blockSize * 0.7f
        paintCache.buttonTextPaint.textSize = blockSize * 0.6f

        val buttonSize = blockSize * 1.5f
        val margin = blockSize * 0.3f

        backButtonRect = RectF(
            width - buttonSize * 2 - margin * 2,
            margin,
            width - buttonSize - margin * 1.5f,
            margin + buttonSize
        )

        pauseButtonRect = RectF(
            width - buttonSize - margin,
            margin,
            width - margin,
            margin + buttonSize
        )

        createCachedGradients()

        initializeGame()
        needsFullRedraw = true
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        cleanup()
    }

    private fun createCachedGradients() {
        val centerX = blockSize / 2f
        val centerY = blockSize / 2f
        val radius = blockSize / 2f - 4f
        val glowRadius = radius + 4f

        foodGradient = RadialGradient(
            centerX, centerY, glowRadius,
            intArrayOf(
                Color.argb(100, 255, 100, 100),
                Color.argb(50, 255, 0, 0),
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, 0.7f, 1f),
            Shader.TileMode.CLAMP
        )

        snakeHeadGradient = RadialGradient(
            centerX, centerY, blockSize / 2f,
            intArrayOf(Color.YELLOW, Color.GREEN),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )
    }

    private fun initializeGame() {
        snake.clear()
        snake.add(Point(numBlocksWide / 2, numBlocksHigh / 2))
        spawnFood()
        score = 0
        level = 1
        gameSpeed = 200L
        direction = Direction.RIGHT
        nextDirection = Direction.RIGHT
        gameRunning = false
        gamePaused = false
        gameStarted = false
        gameOver = false
        particles.clear()
        animationFrame = 0f
        backgroundAlpha = 0f
        lastUpdateTime = System.currentTimeMillis()
        needsFullRedraw = true
    }

    private fun startGame() {
        gameRunning = true
        gameStarted = true
        gamePaused = false
        lastUpdateTime = System.currentTimeMillis()
    }

    private fun spawnFood() {
        var attempts = 0
        do {
            food.x = Random.nextInt(1, numBlocksWide - 1)
            food.y = Random.nextInt(3, numBlocksHigh - 1)
            attempts++
        } while (snake.contains(food) && attempts < 100)
    }

    private fun update() {
        val currentTime = System.currentTimeMillis()

        animationFrame += 0.2f
        backgroundAlpha = (sin(animationFrame * 0.5f) + 1f) * 0.05f

        if (gameRunning && gameStarted && currentTime - lastUpdateTime >= gameSpeed) {
            lastUpdateTime = currentTime

            if (isValidDirectionChange(direction, nextDirection)) {
                direction = nextDirection
            }

            val head = Point(snake[0])
            when (direction) {
                Direction.UP -> head.y--
                Direction.DOWN -> head.y++
                Direction.LEFT -> head.x--
                Direction.RIGHT -> head.x++
            }

            if (head.x < 0 || head.x >= numBlocksWide ||
                head.y < 2 || head.y >= numBlocksHigh) {
                gameOver()
                return
            }

            if (snake.contains(head)) {
                gameOver()
                return
            }

            snake.add(0, head)

            if (head == food) {
                score += 10 * level
                createFoodParticles()
                spawnFood()
                if (score % 50 == 0) {
                    level++
                    gameSpeed = maxOf(80L, gameSpeed - 15L)
                }
            } else {
                snake.removeAt(snake.size - 1)
            }

            needsFullRedraw = true
        }

        updateParticles()
    }

    private fun createFoodParticles() {
        repeat(12) {
            particles.add(
                Particle(
                    x = food.x * blockSize + blockSize / 2f,
                    y = food.y * blockSize + blockSize / 2f,
                    vx = (Random.nextFloat() - 0.5f) * blockSize / 3f,
                    vy = (Random.nextFloat() - 0.5f) * blockSize / 3f,
                    life = 30,
                    maxLife = 30,
                    color = when (Random.nextInt(4)) {
                        0 -> Color.RED
                        1 -> Color.YELLOW
                        2 -> Color.MAGENTA
                        else -> Color.CYAN
                    }
                )
            )
        }
    }

    private fun updateParticles() {
        particles.removeAll { particle ->
            particle.x += particle.vx
            particle.y += particle.vy
            particle.vx *= 0.95f
            particle.vy *= 0.95f
            particle.life--
            particle.life <= 0
        }
    }

    private fun isValidDirectionChange(current: Direction, new: Direction): Boolean {
        return when (current) {
            Direction.UP -> new != Direction.DOWN
            Direction.DOWN -> new != Direction.UP
            Direction.LEFT -> new != Direction.RIGHT
            Direction.RIGHT -> new != Direction.LEFT
        }
    }

    private fun gameOver() {
        gameRunning = false
        gameStarted = false
        gameOver = true
        needsFullRedraw = true
    }

    private fun draw() {
        if (holder.surface.isValid) {
            val canvas = holder.lockCanvas()
            canvas?.let {
                try {
                    drawGame(it)
                } finally {
                    holder.unlockCanvasAndPost(it)
                }
            }
        }
    }

    private fun drawGame(canvas: Canvas) {
        val bgColor = Color.argb(
            (255 * (0.05f + backgroundAlpha)).toInt(),
            0, 20, 40
        )
        canvas.drawColor(Color.BLACK)
        paintCache.backgroundPaint.color = bgColor
        canvas.drawRect(0f, 0f, canvas.width.toFloat(), canvas.height.toFloat(), paintCache.backgroundPaint)

        if (needsFullRedraw) {
            drawGrid(canvas)
            needsFullRedraw = false
        }

        when {
            gameOver -> {
                drawGameOverScreen(canvas)
                drawButtons(canvas)
            }
            !gameStarted && !gameRunning -> {
                drawStartScreen(canvas)
            }
            gameRunning || gamePaused -> {
                drawFood(canvas)
                drawSnake(canvas)
                drawParticles(canvas)
                drawHUD(canvas)
                drawButtons(canvas)

                if (gamePaused) {
                    drawPauseScreen(canvas)
                }
            }
        }
    }

    private fun drawGrid(canvas: Canvas) {
        for (x in 0..numBlocksWide) {
            canvas.drawLine(
                x * blockSize.toFloat(), blockSize * 2f,
                x * blockSize.toFloat(), canvas.height.toFloat(),
                paintCache.gridPaint
            )
        }
        for (y in 2..numBlocksHigh) {
            canvas.drawLine(
                0f, y * blockSize.toFloat(),
                canvas.width.toFloat(), y * blockSize.toFloat(),
                paintCache.gridPaint
            )
        }
    }

    private fun drawStartScreen(canvas: Canvas) {
        drawFood(canvas)
        drawSnake(canvas)

        paintCache.backgroundPaint.color = Color.argb(180, 0, 0, 0)
        canvas.drawRect(0f, 0f, canvas.width.toFloat(), canvas.height.toFloat(), paintCache.backgroundPaint)

        paintCache.gameOverTextPaint.color = Color.GREEN
        paintCache.gameOverTextPaint.textSize = blockSize * 2.5f
        canvas.drawText("SNAKE GAME", canvas.width / 2f, canvas.height / 2.5f, paintCache.gameOverTextPaint)

        paintCache.instructionTextPaint.textSize = blockSize * 0.8f
        paintCache.instructionTextPaint.color = Color.WHITE

        val startY = canvas.height / 2f - blockSize * 2f
        val lineSpacing = blockSize * 1.2f

        canvas.drawText("INSTRUKCJA OBSŁUGI:", canvas.width / 2f, startY, paintCache.instructionTextPaint)

        paintCache.instructionTextPaint.color = Color.LTGRAY
        paintCache.instructionTextPaint.textSize = blockSize * 0.7f

        canvas.drawText("• Przesuwaj palcem po ekranie aby sterować wężem",
            canvas.width / 2f, startY + lineSpacing, paintCache.instructionTextPaint)
        canvas.drawText("• Zbieraj czerwone jabłka aby rosnąć i zdobywać punkty",
            canvas.width / 2f, startY + lineSpacing * 2, paintCache.instructionTextPaint)
        canvas.drawText("• Unikaj ścian i własnego ogona",
            canvas.width / 2f, startY + lineSpacing * 3, paintCache.instructionTextPaint)
        canvas.drawText("• Gra kończy się po uderzeniu w przeszkodę",
            canvas.width / 2f, startY + lineSpacing * 4, paintCache.instructionTextPaint)

        val marginAfterInstructions = blockSize * 2f
        val instructionEndY = startY + lineSpacing * 4

        paintCache.gameOverTextPaint.color = Color.YELLOW
        paintCache.gameOverTextPaint.textSize = blockSize * 1.2f

        val pulse = (sin(animationFrame * 0.3f) + 1f) * 0.3f + 0.7f
        paintCache.gameOverTextPaint.alpha = (255 * pulse).toInt()

        canvas.drawText(
            "DOTKNIJ EKRAN ABY ROZPOCZĄĆ",
            canvas.width / 2f,
            instructionEndY + marginAfterInstructions,
            paintCache.gameOverTextPaint
        )

        paintCache.gameOverTextPaint.alpha = 255
        drawButtons(canvas)
    }

    private fun drawFood(canvas: Canvas) {
        val centerX = food.x * blockSize + blockSize / 2f
        val centerY = food.y * blockSize + blockSize / 2f
        val radius = blockSize / 2f - 4f
        val glowRadius = radius + sin(animationFrame * 2f) * 4f

        canvas.save()
        canvas.translate(centerX - blockSize / 2f, centerY - blockSize / 2f)

        paintCache.foodPaint.shader = foodGradient
        canvas.drawCircle(blockSize / 2f, blockSize / 2f, glowRadius, paintCache.foodPaint)

        paintCache.foodPaint.shader = null
        paintCache.foodPaint.color = Color.RED
        canvas.drawCircle(blockSize / 2f, blockSize / 2f, radius, paintCache.foodPaint)

        paintCache.foodPaint.color = Color.argb(150, 255, 200, 200)
        canvas.drawCircle(blockSize / 2f - radius/3, blockSize / 2f - radius/3, radius/3, paintCache.foodPaint)

        canvas.restore()
    }

    private fun drawSnake(canvas: Canvas) {
        snake.forEachIndexed { index, segment ->
            val x = segment.x * blockSize.toFloat()
            val y = segment.y * blockSize.toFloat()
            val margin = 2f

            if (index == 0) {
                canvas.save()
                canvas.translate(x, y)

                paintCache.snakeHeadPaint.shader = snakeHeadGradient
                val rect = RectF(margin, margin, blockSize - margin, blockSize - margin)
                canvas.drawRoundRect(rect, 8f, 8f, paintCache.snakeHeadPaint)

                paintCache.snakeHeadPaint.shader = null
                paintCache.snakeHeadPaint.color = Color.BLACK
                val eyeSize = blockSize / 8f

                when (direction) {
                    Direction.UP -> {
                        canvas.drawCircle(blockSize*0.3f, blockSize*0.3f, eyeSize, paintCache.snakeHeadPaint)
                        canvas.drawCircle(blockSize*0.7f, blockSize*0.3f, eyeSize, paintCache.snakeHeadPaint)
                    }
                    Direction.DOWN -> {
                        canvas.drawCircle(blockSize*0.3f, blockSize*0.7f, eyeSize, paintCache.snakeHeadPaint)
                        canvas.drawCircle(blockSize*0.7f, blockSize*0.7f, eyeSize, paintCache.snakeHeadPaint)
                    }
                    Direction.LEFT -> {
                        canvas.drawCircle(blockSize*0.3f, blockSize*0.3f, eyeSize, paintCache.snakeHeadPaint)
                        canvas.drawCircle(blockSize*0.3f, blockSize*0.7f, eyeSize, paintCache.snakeHeadPaint)
                    }
                    Direction.RIGHT -> {
                        canvas.drawCircle(blockSize*0.7f, blockSize*0.3f, eyeSize, paintCache.snakeHeadPaint)
                        canvas.drawCircle(blockSize*0.7f, blockSize*0.7f, eyeSize, paintCache.snakeHeadPaint)
                    }
                }

                canvas.restore()
            } else {
                val intensity = 1f - (index.toFloat() / snake.size) * 0.6f
                val alpha = (200 * intensity).toInt()
                paintCache.snakeBodyPaint.color = Color.argb(alpha, 0, (200 * intensity).toInt(), 0)

                val rect = RectF(x + margin, y + margin, x + blockSize - margin, y + blockSize - margin)
                canvas.drawRoundRect(rect, 4f, 4f, paintCache.snakeBodyPaint)
            }
        }
    }

    private fun drawParticles(canvas: Canvas) {
        particles.forEach { particle ->
            val alpha = (particle.life.toFloat() / particle.maxLife * 255).toInt()
            val size = particle.life.toFloat() / particle.maxLife * blockSize / 4f

            paintCache.particlePaint.color = Color.argb(alpha,
                Color.red(particle.color),
                Color.green(particle.color),
                Color.blue(particle.color))

            canvas.drawCircle(particle.x, particle.y, size, paintCache.particlePaint)
        }
    }

    private fun drawHUD(canvas: Canvas) {
        val hudY = blockSize * 1.5f

        canvas.drawRect(0f, 0f, canvas.width.toFloat(), blockSize * 2f, paintCache.hudPaint)

        paintCache.textPaint.color = Color.WHITE
        canvas.drawText("Wynik: $score", blockSize.toFloat(), hudY, paintCache.textPaint)

        val lengthText = "Długość: ${snake.size}"
        canvas.drawText(lengthText, blockSize.toFloat(), hudY + blockSize * 0.7f, paintCache.textPaint)

        val levelText = "Poziom: $level"
        val levelX = canvas.width / 2f - paintCache.textPaint.measureText(levelText) / 2f
        canvas.drawText(levelText, levelX, hudY, paintCache.textPaint)
    }

    private fun drawButtons(canvas: Canvas) {
        paintCache.buttonPaint.color = Color.argb(150, 255, 100, 100)
        canvas.drawRoundRect(backButtonRect, 10f, 10f, paintCache.buttonPaint)

        paintCache.buttonPaint.color = Color.argb(100, 0, 0, 0)
        canvas.drawRoundRect(
            RectF(backButtonRect.left + 2, backButtonRect.top + 2,
                backButtonRect.right + 2, backButtonRect.bottom + 2),
            10f, 10f, paintCache.buttonPaint
        )

        canvas.drawText("◀",
            backButtonRect.centerX(),
            backButtonRect.centerY() + paintCache.buttonTextPaint.textSize / 3,
            paintCache.buttonTextPaint)

        if (gameRunning || gamePaused) {
            if (gamePaused) {
                paintCache.buttonPaint.color = Color.argb(150, 100, 255, 100)
                canvas.drawRoundRect(pauseButtonRect, 10f, 10f, paintCache.buttonPaint)
                canvas.drawText("▶",
                    pauseButtonRect.centerX(),
                    pauseButtonRect.centerY() + paintCache.buttonTextPaint.textSize / 3,
                    paintCache.buttonTextPaint)
            } else {
                paintCache.buttonPaint.color = Color.argb(150, 255, 255, 100)
                canvas.drawRoundRect(pauseButtonRect, 10f, 10f, paintCache.buttonPaint)
                canvas.drawText("⏸",
                    pauseButtonRect.centerX(),
                    pauseButtonRect.centerY() + paintCache.buttonTextPaint.textSize / 3,
                    paintCache.buttonTextPaint)
            }

            paintCache.buttonPaint.color = Color.argb(100, 0, 0, 0)
            canvas.drawRoundRect(
                RectF(pauseButtonRect.left + 2, pauseButtonRect.top + 2,
                    pauseButtonRect.right + 2, pauseButtonRect.bottom + 2),
                10f, 10f, paintCache.buttonPaint
            )
        }
    }

    private fun drawPauseScreen(canvas: Canvas) {
        paintCache.backgroundPaint.color = Color.argb(150, 0, 0, 0)
        canvas.drawRect(0f, 0f, canvas.width.toFloat(), canvas.height.toFloat(), paintCache.backgroundPaint)

        paintCache.gameOverTextPaint.color = Color.YELLOW
        paintCache.gameOverTextPaint.textSize = blockSize * 2f
        canvas.drawText("PAUZA", canvas.width / 2f, canvas.height / 2f, paintCache.gameOverTextPaint)

        paintCache.gameOverTextPaint.textSize = blockSize.toFloat()
        canvas.drawText("Naciśnij ▶ aby kontynuować", canvas.width / 2f,
            canvas.height / 2f + blockSize * 3f, paintCache.gameOverTextPaint)
    }

    private fun drawGameOverScreen(canvas: Canvas) {
        paintCache.backgroundPaint.color = Color.argb(200, 0, 0, 0)
        canvas.drawRect(0f, 0f, canvas.width.toFloat(), canvas.height.toFloat(), paintCache.backgroundPaint)

        paintCache.gameOverTextPaint.color = Color.RED
        paintCache.gameOverTextPaint.textSize = blockSize * 2f
        canvas.drawText("GAME OVER", canvas.width / 2f, canvas.height / 2f - blockSize * 2f, paintCache.gameOverTextPaint)

        paintCache.gameOverTextPaint.color = Color.WHITE
        paintCache.gameOverTextPaint.textSize = blockSize * 1.2f
        canvas.drawText("Końcowy wynik: $score", canvas.width / 2f,
            canvas.height / 2f, paintCache.gameOverTextPaint)
        canvas.drawText("Długość węża: ${snake.size}", canvas.width / 2f,
            canvas.height / 2f + blockSize * 1.5f, paintCache.gameOverTextPaint)
        canvas.drawText("Osiągnięty poziom: $level", canvas.width / 2f,
            canvas.height / 2f + blockSize * 3f, paintCache.gameOverTextPaint)

        paintCache.gameOverTextPaint.color = Color.YELLOW
        paintCache.gameOverTextPaint.textSize = blockSize.toFloat()
        canvas.drawText("Dotknij aby zagrać ponownie", canvas.width / 2f,
            canvas.height / 2f + blockSize * 5f, paintCache.gameOverTextPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                touchStartX = event.x
                touchStartY = event.y
                return true
            }

            MotionEvent.ACTION_UP -> {
                val x = event.x
                val y = event.y

                if (backButtonRect.contains(x, y)) {
                    activity.goBackToMain()
                    return true
                }

                if (pauseButtonRect.contains(x, y) && (gameRunning || gamePaused)) {
                    gamePaused = !gamePaused
                    return true
                }

                if (gameOver) {
                    if (!backButtonRect.contains(x, y)) {
                        initializeGame()
                        startGame()
                    }
                    return true
                }

                if (!gameStarted && !gameRunning) {
                    if (!backButtonRect.contains(x, y)) {
                        startGame()
                    }
                    return true
                }

                if (gamePaused) {
                    gamePaused = false
                    return true
                }

                if (gameRunning && gameStarted && !gamePaused) {
                    val deltaX = x - touchStartX
                    val deltaY = y - touchStartY
                    val distance = sqrt(deltaX * deltaX + deltaY * deltaY)

                    if (distance >= minSwipeDistance) {
                        if (abs(deltaX) > abs(deltaY)) {
                            nextDirection = if (deltaX > 0) Direction.RIGHT else Direction.LEFT
                        } else {
                            nextDirection = if (deltaY > 0) Direction.DOWN else Direction.UP
                        }
                    }
                }

                return true
            }
        }
        return super.onTouchEvent(event)
    }

    fun resume() {
        isPlaying = true
        gameThread = GameThread()
        gameThread?.start()
    }

    fun pause() {
        isPlaying = false
        gameThread?.let { thread ->
            var retry = true
            while (retry) {
                try {
                    thread.join()
                    retry = false
                } catch (e: InterruptedException) {
                }
            }
        }
        gameThread = null
    }

    fun cleanup() {
        pause()
    }
}

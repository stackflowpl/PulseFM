package net.gf.radio24

import android.content.Context
import android.content.Intent
import android.graphics.*
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.*
import kotlin.random.Random

class SnakeGame : AppCompatActivity() {
    private lateinit var gameView: GameView

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

        gameView = GameView(this)
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
}

class GameView(context: Context) : View(context) {
    private val activity = context as SnakeGame

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
    private var pauseButtonVisible = true
    private var backButtonVisible = true

    private val snakeBodyPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }

    private val snakeHeadPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }

    private val foodPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }

    private val textPaint = Paint().apply {
        isAntiAlias = true
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.LEFT
    }

    private val gameOverTextPaint = Paint().apply {
        isAntiAlias = true
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
        color = Color.WHITE
    }

    private val instructionTextPaint = Paint().apply {
        isAntiAlias = true
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        textAlign = Paint.Align.CENTER
        color = Color.LTGRAY
    }

    private val gridPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 1f
        color = Color.argb(30, 0, 255, 0)
    }

    private val buttonPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }

    private val buttonTextPaint = Paint().apply {
        isAntiAlias = true
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
        color = Color.WHITE
    }

    private val handler = Handler(Looper.getMainLooper())
    private val gameRunnable = object : Runnable {
        override fun run() {
            if (gameRunning && !gamePaused && gameStarted) {
                update()
                invalidate()
                handler.postDelayed(this, gameSpeed)
            }
        }
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

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        blockSize = minOf(w, h) / 25
        numBlocksWide = w / blockSize
        numBlocksHigh = h / blockSize

        textPaint.textSize = blockSize * 0.8f
        gameOverTextPaint.textSize = blockSize * 1.5f
        instructionTextPaint.textSize = blockSize * 0.7f
        buttonTextPaint.textSize = blockSize * 0.6f

        val buttonSize = blockSize * 1.5f
        val margin = blockSize * 0.3f

        backButtonRect = RectF(
            w - buttonSize * 2 - margin * 2,
            margin,
            w - buttonSize - margin * 1.5f,
            margin + buttonSize
        )

        pauseButtonRect = RectF(
            w - buttonSize - margin,
            margin,
            w - margin,
            margin + buttonSize
        )

        initializeGame()
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
    }

    private fun startGame() {
        gameRunning = true
        gameStarted = true
        gamePaused = false
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
        animationFrame += 0.2f
        backgroundAlpha = (sin(animationFrame * 0.5f) + 1f) * 0.05f

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
        handler.removeCallbacks(gameRunnable)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val bgColor = Color.argb(
            (255 * (0.05f + backgroundAlpha)).toInt(),
            0, 20, 40
        )
        canvas.drawColor(Color.BLACK)
        canvas.drawColor(bgColor)

        drawGrid(canvas)

        if (gameOver) {
            drawGameOverScreen(canvas)
            drawButtons(canvas)
        } else if (!gameStarted && !gameRunning) {
            drawStartScreen(canvas)
        } else if (gameRunning || gamePaused) {
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

    private fun drawGrid(canvas: Canvas) {
        for (x in 0..numBlocksWide) {
            canvas.drawLine(
                x * blockSize.toFloat(), blockSize * 2f,
                x * blockSize.toFloat(), height.toFloat(),
                gridPaint
            )
        }
        for (y in 2..numBlocksHigh) {
            canvas.drawLine(
                0f, y * blockSize.toFloat(),
                width.toFloat(), y * blockSize.toFloat(),
                gridPaint
            )
        }
    }

    private fun drawStartScreen(canvas: Canvas) {
        drawFood(canvas)
        drawSnake(canvas)

        canvas.drawColor(Color.argb(180, 0, 0, 0))

        gameOverTextPaint.color = Color.GREEN
        gameOverTextPaint.textSize = blockSize * 2.5f
        canvas.drawText("SNAKE GAME", width / 2f, height / 2.5f, gameOverTextPaint)

        instructionTextPaint.textSize = blockSize * 0.8f
        instructionTextPaint.color = Color.WHITE

        val startY = height / 2f - blockSize * 2f
        val lineSpacing = blockSize * 1.2f

        canvas.drawText("INSTRUKCJA OBSŁUGI:", width / 2f, startY, instructionTextPaint)

        instructionTextPaint.color = Color.LTGRAY
        instructionTextPaint.textSize = blockSize * 0.7f

        canvas.drawText("• Przesuwaj palcem po ekranie aby sterować wężem",
            width / 2f, startY + lineSpacing, instructionTextPaint)
        canvas.drawText("• Zbieraj czerwone jabłka aby rosnąć i zdobywać punkty",
            width / 2f, startY + lineSpacing * 2, instructionTextPaint)
        canvas.drawText("• Unikaj ścian i własnego ogona",
            width / 2f, startY + lineSpacing * 3, instructionTextPaint)
        canvas.drawText("• Gra kończy się po uderzeniu w przeszkodę",
            width / 2f, startY + lineSpacing * 4, instructionTextPaint)

        val marginAfterInstructions = blockSize * 2f
        val instructionEndY = startY + lineSpacing * 4

        gameOverTextPaint.color = Color.YELLOW
        gameOverTextPaint.textSize = blockSize * 1.2f

        val pulse = (sin(animationFrame * 0.3f) + 1f) * 0.3f + 0.7f
        gameOverTextPaint.alpha = (255 * pulse).toInt()

        canvas.drawText(
            "DOTKNIJ EKRAN ABY ROZPOCZĄĆ",
            width / 2f,
            instructionEndY + marginAfterInstructions,
            gameOverTextPaint
        )

        gameOverTextPaint.alpha = 255

        drawButtons(canvas)

        animationFrame += 0.2f
        invalidate()
    }


    private fun drawFood(canvas: Canvas) {
        val centerX = food.x * blockSize + blockSize / 2f
        val centerY = food.y * blockSize + blockSize / 2f
        val radius = blockSize / 2f - 4f
        val glowRadius = radius + sin(animationFrame * 2f) * 4f

        val gradient = RadialGradient(
            centerX, centerY, glowRadius,
            intArrayOf(
                Color.argb(100, 255, 100, 100),
                Color.argb(50, 255, 0, 0),
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, 0.7f, 1f),
            Shader.TileMode.CLAMP
        )
        foodPaint.shader = gradient
        canvas.drawCircle(centerX, centerY, glowRadius, foodPaint)

        foodPaint.shader = null
        foodPaint.color = Color.RED
        canvas.drawCircle(centerX, centerY, radius, foodPaint)

        foodPaint.color = Color.argb(150, 255, 200, 200)
        canvas.drawCircle(centerX - radius/3, centerY - radius/3, radius/3, foodPaint)
    }

    private fun drawSnake(canvas: Canvas) {
        snake.forEachIndexed { index, segment ->
            val x = segment.x * blockSize.toFloat()
            val y = segment.y * blockSize.toFloat()
            val margin = 2f

            if (index == 0) {
                val gradient = RadialGradient(
                    x + blockSize/2f, y + blockSize/2f, blockSize/2f,
                    intArrayOf(Color.YELLOW, Color.GREEN),
                    floatArrayOf(0f, 1f),
                    Shader.TileMode.CLAMP
                )
                snakeHeadPaint.shader = gradient

                val rect = RectF(x + margin, y + margin,
                    x + blockSize - margin, y + blockSize - margin)
                canvas.drawRoundRect(rect, 8f, 8f, snakeHeadPaint)

                snakeHeadPaint.shader = null
                snakeHeadPaint.color = Color.BLACK
                val eyeSize = blockSize / 8f
                when (direction) {
                    Direction.UP -> {
                        canvas.drawCircle(x + blockSize*0.3f, y + blockSize*0.3f, eyeSize, snakeHeadPaint)
                        canvas.drawCircle(x + blockSize*0.7f, y + blockSize*0.3f, eyeSize, snakeHeadPaint)
                    }
                    Direction.DOWN -> {
                        canvas.drawCircle(x + blockSize*0.3f, y + blockSize*0.7f, eyeSize, snakeHeadPaint)
                        canvas.drawCircle(x + blockSize*0.7f, y + blockSize*0.7f, eyeSize, snakeHeadPaint)
                    }
                    Direction.LEFT -> {
                        canvas.drawCircle(x + blockSize*0.3f, y + blockSize*0.3f, eyeSize, snakeHeadPaint)
                        canvas.drawCircle(x + blockSize*0.3f, y + blockSize*0.7f, eyeSize, snakeHeadPaint)
                    }
                    Direction.RIGHT -> {
                        canvas.drawCircle(x + blockSize*0.7f, y + blockSize*0.3f, eyeSize, snakeHeadPaint)
                        canvas.drawCircle(x + blockSize*0.7f, y + blockSize*0.7f, eyeSize, snakeHeadPaint)
                    }
                }
            } else {
                val intensity = 1f - (index.toFloat() / snake.size) * 0.6f
                val alpha = (200 * intensity).toInt()
                snakeBodyPaint.color = Color.argb(alpha, 0, (200 * intensity).toInt(), 0)

                val rect = RectF(x + margin, y + margin,
                    x + blockSize - margin, y + blockSize - margin)
                canvas.drawRoundRect(rect, 4f, 4f, snakeBodyPaint)
            }
        }
    }

    private fun drawParticles(canvas: Canvas) {
        particles.forEach { particle ->
            val alpha = (particle.life.toFloat() / particle.maxLife * 255).toInt()
            val size = particle.life.toFloat() / particle.maxLife * blockSize / 4f

            val paint = Paint().apply {
                color = Color.argb(alpha,
                    Color.red(particle.color),
                    Color.green(particle.color),
                    Color.blue(particle.color))
                isAntiAlias = true
            }

            canvas.drawCircle(particle.x, particle.y, size, paint)
        }
    }

    private fun drawHUD(canvas: Canvas) {
        val hudY = blockSize * 1.5f

        val hudPaint = Paint().apply {
            color = Color.argb(150, 0, 0, 0)
        }
        canvas.drawRect(0f, 0f, width.toFloat(), blockSize * 2f, hudPaint)

        textPaint.color = Color.WHITE
        canvas.drawText("Wynik: $score", blockSize.toFloat(), hudY, textPaint)

        val lengthText = "Długość: ${snake.size}"
        canvas.drawText(lengthText, blockSize.toFloat(), hudY + blockSize * 0.7f, textPaint)

        val levelText = "Poziom: $level"
        val levelX = width / 2f - textPaint.measureText(levelText) / 2f
        canvas.drawText(levelText, levelX, hudY, textPaint)
    }

    private fun drawButtons(canvas: Canvas) {
        if (backButtonVisible) {
            buttonPaint.color = Color.argb(150, 255, 100, 100)
            canvas.drawRoundRect(backButtonRect, 10f, 10f, buttonPaint)

            buttonPaint.color = Color.argb(100, 0, 0, 0)
            canvas.drawRoundRect(
                RectF(backButtonRect.left + 2, backButtonRect.top + 2,
                    backButtonRect.right + 2, backButtonRect.bottom + 2),
                10f, 10f, buttonPaint
            )

            canvas.drawText("◀",
                backButtonRect.centerX(),
                backButtonRect.centerY() + buttonTextPaint.textSize / 3,
                buttonTextPaint)
        }

        if (pauseButtonVisible && (gameRunning || gamePaused)) {
            if (gamePaused) {
                buttonPaint.color = Color.argb(150, 100, 255, 100)
                canvas.drawRoundRect(pauseButtonRect, 10f, 10f, buttonPaint)
                canvas.drawText("▶",
                    pauseButtonRect.centerX(),
                    pauseButtonRect.centerY() + buttonTextPaint.textSize / 3,
                    buttonTextPaint)
            } else {
                buttonPaint.color = Color.argb(150, 255, 255, 100)
                canvas.drawRoundRect(pauseButtonRect, 10f, 10f, buttonPaint)
                canvas.drawText("⏸",
                    pauseButtonRect.centerX(),
                    pauseButtonRect.centerY() + buttonTextPaint.textSize / 3,
                    buttonTextPaint)
            }

            buttonPaint.color = Color.argb(100, 0, 0, 0)
            canvas.drawRoundRect(
                RectF(pauseButtonRect.left + 2, pauseButtonRect.top + 2,
                    pauseButtonRect.right + 2, pauseButtonRect.bottom + 2),
                10f, 10f, buttonPaint
            )
        }
    }

    private fun drawPauseScreen(canvas: Canvas) {
        canvas.drawColor(Color.argb(150, 0, 0, 0))

        gameOverTextPaint.color = Color.YELLOW
        gameOverTextPaint.textSize = blockSize * 2f
        canvas.drawText("PAUZA", width / 2f, height / 2f, gameOverTextPaint)

        gameOverTextPaint.textSize = blockSize.toFloat()
        canvas.drawText("Naciśnij ▶ aby kontynuować", width / 2f,
            height / 2f + blockSize * 3f, gameOverTextPaint)
    }

    private fun drawGameOverScreen(canvas: Canvas) {
        canvas.drawColor(Color.argb(200, 0, 0, 0))

        gameOverTextPaint.color = Color.RED
        gameOverTextPaint.textSize = blockSize * 2f
        canvas.drawText("GAME OVER", width / 2f, height / 2f - blockSize * 2f, gameOverTextPaint)

        gameOverTextPaint.color = Color.WHITE
        gameOverTextPaint.textSize = blockSize * 1.2f
        canvas.drawText("Końcowy wynik: $score", width / 2f,
            height / 2f, gameOverTextPaint)
        canvas.drawText("Długość węża: ${snake.size}", width / 2f,
            height / 2f + blockSize * 1.5f, gameOverTextPaint)
        canvas.drawText("Osiągnięty poziom: $level", width / 2f,
            height / 2f + blockSize * 3f, gameOverTextPaint)

        gameOverTextPaint.color = Color.YELLOW
        gameOverTextPaint.textSize = blockSize.toFloat()
        canvas.drawText("Dotknij aby zagrać ponownie", width / 2f,
            height / 2f + blockSize * 5f, gameOverTextPaint)
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
                    if (gamePaused) {
                        gamePaused = false
                        resume()
                    } else {
                        gamePaused = true
                        handler.removeCallbacks(gameRunnable)
                    }
                    invalidate()
                    return true
                }

                if (gameOver) {
                    if (!backButtonRect.contains(x, y)) {
                        initializeGame()
                        startGame()
                        resume()
                    }
                    return true
                }

                if (!gameStarted && !gameRunning) {
                    if (!backButtonRect.contains(x, y)) {
                        startGame()
                        resume()
                    }
                    return true
                }

                if (gamePaused) {
                    gamePaused = false
                    resume()
                    invalidate()
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
        if (gameRunning && !gamePaused && gameStarted) {
            handler.post(gameRunnable)
        }
    }

    fun pause() {
        handler.removeCallbacks(gameRunnable)
    }
}
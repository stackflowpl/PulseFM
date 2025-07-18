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

class FlappyBirdGame : AppCompatActivity() {
    private lateinit var gameView: FlappyBirdGameView

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
        gameView = FlappyBirdGameView(this)
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

class FlappyBirdGameView(context: Context) : SurfaceView(context), SurfaceHolder.Callback {
    private val activity = context as FlappyBirdGame
    private var gameThread: GameThread? = null
    private var isPlaying = false

    private var screenWidth = 0
    private var screenHeight = 0

    private var bird = Bird()
    private var pipes = mutableListOf<Pipe>()
    private var particles = mutableListOf<Particle>()

    private var score = 0
    private var highScore = 0
    private var gameRunning = false
    private var gamePaused = false
    private var gameStarted = false
    private var gameOver = false

    private val gravity = 0.8f
    private val jumpForce = -15f
    private val pipeSpeed = 4f
    private val pipeWidth = 120f
    private val pipeGap = 300f
    private val birdSize = 40f

    private var animationFrame = 0f
    private var lastUpdateTime = 0L
    private val targetFPS = 60
    private val frameTime = 1000L / targetFPS

    private var backButtonRect = RectF()
    private var pauseButtonRect = RectF()

    private val paintCache = PaintCache()

    init {
        holder.addCallback(this)
        isFocusable = true
        loadHighScore()
    }

    data class Bird(
        var x: Float = 0f,
        var y: Float = 0f,
        var velocity: Float = 0f,
        var rotation: Float = 0f
    )

    data class Pipe(
        var x: Float,
        var topHeight: Float,
        var bottomY: Float,
        var passed: Boolean = false
    )

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
        val birdPaint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.FILL
        }

        val pipePaint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.FILL
            color = Color.rgb(34, 139, 34)
        }

        val pipeStrokePaint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeWidth = 4f
            color = Color.rgb(0, 100, 0)
        }

        val backgroundPaint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.FILL
        }

        val textPaint = Paint().apply {
            isAntiAlias = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            color = Color.WHITE
        }

        val scorePaint = Paint().apply {
            isAntiAlias = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            color = Color.WHITE
            setShadowLayer(4f, 2f, 2f, Color.BLACK)
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

        val groundPaint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.FILL
            color = Color.rgb(139, 69, 19)
        }

        val grassPaint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.FILL
            color = Color.rgb(34, 139, 34)
        }
    }

    private inner class GameThread : Thread() {
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
                waitTime = frameTime - timeMillis

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

    override fun surfaceCreated(holder: SurfaceHolder) {}

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        screenWidth = width
        screenHeight = height

        val buttonSize = 80f
        val margin = 20f

        backButtonRect = RectF(
            margin,
            margin,
            margin + buttonSize,
            margin + buttonSize
        )

        pauseButtonRect = RectF(
            width - buttonSize - margin,
            margin,
            width - margin,
            margin + buttonSize
        )

        paintCache.textPaint.textSize = 48f
        paintCache.scorePaint.textSize = 72f
        paintCache.buttonTextPaint.textSize = 32f

        initializeGame()
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        cleanup()
    }

    private fun initializeGame() {
        bird = Bird(
            x = screenWidth * 0.2f,
            y = screenHeight * 0.5f,
            velocity = 0f,
            rotation = 0f
        )

        pipes.clear()
        particles.clear()
        score = 0
        gameRunning = false
        gamePaused = false
        gameStarted = false
        gameOver = false
        animationFrame = 0f
        lastUpdateTime = System.currentTimeMillis()

        addPipe()
    }

    private fun startGame() {
        gameRunning = true
        gameStarted = true
        gamePaused = false
        lastUpdateTime = System.currentTimeMillis()
    }

    private fun addPipe() {
        val minHeight = 100f
        val maxHeight = screenHeight - pipeGap - minHeight - 100f
        val topHeight = Random.nextFloat() * (maxHeight - minHeight) + minHeight

        pipes.add(
            Pipe(
                x = screenWidth.toFloat(),
                topHeight = topHeight,
                bottomY = topHeight + pipeGap
            )
        )
    }

    private fun jump() {
        if (gameRunning && !gamePaused) {
            bird.velocity = jumpForce
            createJumpParticles()
        }
    }

    private fun createJumpParticles() {
        repeat(8) {
            particles.add(
                Particle(
                    x = bird.x,
                    y = bird.y + birdSize / 2,
                    vx = (Random.nextFloat() - 0.5f) * 10f,
                    vy = (Random.nextFloat() - 0.5f) * 10f,
                    life = 20,
                    maxLife = 20,
                    color = Color.rgb(135, 206, 235)
                )
            )
        }
    }

    private fun createScoreParticles(x: Float, y: Float) {
        repeat(12) {
            particles.add(
                Particle(
                    x = x,
                    y = y,
                    vx = (Random.nextFloat() - 0.5f) * 15f,
                    vy = (Random.nextFloat() - 0.5f) * 15f,
                    life = 30,
                    maxLife = 30,
                    color = when (Random.nextInt(4)) {
                        0 -> Color.YELLOW
                        1 -> Color.GREEN
                        2 -> Color.CYAN
                        else -> Color.MAGENTA
                    }
                )
            )
        }
    }

    private fun update() {
        if (!gameRunning || gamePaused) return

        val currentTime = System.currentTimeMillis()
        animationFrame += 0.2f

        bird.velocity += gravity
        bird.y += bird.velocity
        bird.rotation = bird.velocity * 3f
        bird.rotation = bird.rotation.coerceIn(-30f, 90f)

        if (bird.y <= 0 || bird.y >= screenHeight - 100f - birdSize) {
            gameOver()
            return
        }

        pipes.removeAll { pipe ->
            pipe.x -= pipeSpeed

            if (!pipe.passed && bird.x > pipe.x + pipeWidth) {
                pipe.passed = true
                score++
                if (score > highScore) {
                    highScore = score
                    saveHighScore()
                }
                createScoreParticles(pipe.x + pipeWidth, pipe.topHeight + pipeGap / 2)
            }

            if (bird.x + birdSize > pipe.x && bird.x < pipe.x + pipeWidth) {
                if (bird.y < pipe.topHeight || bird.y + birdSize > pipe.bottomY) {
                    gameOver()
                    return@removeAll false
                }
            }

            pipe.x + pipeWidth < 0
        }

        if (pipes.isEmpty() || pipes.last().x < screenWidth - 400f) {
            addPipe()
        }

        particles.removeAll { particle ->
            particle.x += particle.vx
            particle.y += particle.vy
            particle.vx *= 0.98f
            particle.vy *= 0.98f
            particle.life--
            particle.life <= 0
        }
    }

    private fun gameOver() {
        gameRunning = false
        gameStarted = false
        gameOver = true

        repeat(20) {
            particles.add(
                Particle(
                    x = bird.x + birdSize / 2,
                    y = bird.y + birdSize / 2,
                    vx = (Random.nextFloat() - 0.5f) * 20f,
                    vy = (Random.nextFloat() - 0.5f) * 20f,
                    life = 40,
                    maxLife = 40,
                    color = Color.RED
                )
            )
        }
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
        val skyGradient = LinearGradient(
            0f, 0f, 0f, screenHeight.toFloat(),
            Color.rgb(135, 206, 235),
            Color.rgb(152, 251, 152),
            Shader.TileMode.CLAMP
        )
        paintCache.backgroundPaint.shader = skyGradient
        canvas.drawRect(0f, 0f, screenWidth.toFloat(), screenHeight.toFloat(), paintCache.backgroundPaint)
        paintCache.backgroundPaint.shader = null

        drawClouds(canvas)

        drawPipes(canvas)

        canvas.drawRect(0f, screenHeight - 100f, screenWidth.toFloat(), screenHeight.toFloat(), paintCache.groundPaint)
        canvas.drawRect(0f, screenHeight - 105f, screenWidth.toFloat(), screenHeight - 100f, paintCache.grassPaint)

        drawBird(canvas)

        drawParticles(canvas)

        when {
            gameOver -> drawGameOverScreen(canvas)
            !gameStarted && !gameRunning -> drawStartScreen(canvas)
            gameRunning || gamePaused -> {
                drawScore(canvas)
                drawButtons(canvas)
                if (gamePaused) drawPauseScreen(canvas)
            }
        }
    }

    private fun drawClouds(canvas: Canvas) {
        paintCache.backgroundPaint.color = Color.argb(200, 255, 255, 255)

        for (i in 0..4) {
            val x = (i * 200f + animationFrame * 2f) % (screenWidth + 100f)
            val y = 100f + sin(animationFrame * 0.01f + i) * 30f

            canvas.drawCircle(x, y, 40f, paintCache.backgroundPaint)
            canvas.drawCircle(x + 30f, y, 50f, paintCache.backgroundPaint)
            canvas.drawCircle(x + 60f, y, 40f, paintCache.backgroundPaint)
        }
    }

    private fun drawPipes(canvas: Canvas) {
        pipes.forEach { pipe ->
            canvas.drawRect(pipe.x, 0f, pipe.x + pipeWidth, pipe.topHeight, paintCache.pipePaint)
            canvas.drawRect(pipe.x, 0f, pipe.x + pipeWidth, pipe.topHeight, paintCache.pipeStrokePaint)

            canvas.drawRect(pipe.x, pipe.bottomY, pipe.x + pipeWidth, screenHeight - 100f, paintCache.pipePaint)
            canvas.drawRect(pipe.x, pipe.bottomY, pipe.x + pipeWidth, screenHeight - 100f, paintCache.pipeStrokePaint)

            paintCache.pipePaint.color = Color.rgb(50, 205, 50)
            canvas.drawRect(pipe.x - 10f, pipe.topHeight - 30f, pipe.x + pipeWidth + 10f, pipe.topHeight, paintCache.pipePaint)
            canvas.drawRect(pipe.x - 10f, pipe.bottomY, pipe.x + pipeWidth + 10f, pipe.bottomY + 30f, paintCache.pipePaint)
            paintCache.pipePaint.color = Color.rgb(34, 139, 34)
        }
    }

    private fun drawBird(canvas: Canvas) {
        canvas.save()
        canvas.translate(bird.x + birdSize / 2, bird.y + birdSize / 2)
        canvas.rotate(bird.rotation)

        paintCache.birdPaint.color = Color.rgb(255, 215, 0)
        canvas.drawCircle(0f, 0f, birdSize / 2, paintCache.birdPaint)

        paintCache.birdPaint.color = Color.rgb(255, 140, 0)
        val wingOffset = sin(animationFrame * 0.5f) * 5f
        canvas.drawOval(-15f, -10f + wingOffset, 5f, 10f + wingOffset, paintCache.birdPaint)

        paintCache.birdPaint.color = Color.WHITE
        canvas.drawCircle(8f, -8f, 6f, paintCache.birdPaint)
        paintCache.birdPaint.color = Color.BLACK
        canvas.drawCircle(10f, -6f, 3f, paintCache.birdPaint)

        paintCache.birdPaint.color = Color.rgb(255, 69, 0)
        val beakPath = Path().apply {
            moveTo(birdSize / 2, 0f)
            lineTo(birdSize / 2 + 15f, 3f)
            lineTo(birdSize / 2, 6f)
            close()
        }
        canvas.drawPath(beakPath, paintCache.birdPaint)

        canvas.restore()
    }

    private fun drawParticles(canvas: Canvas) {
        particles.forEach { particle ->
            val alpha = (particle.life.toFloat() / particle.maxLife * 255).toInt()
            val size = particle.life.toFloat() / particle.maxLife * 8f

            paintCache.particlePaint.color = Color.argb(
                alpha,
                Color.red(particle.color),
                Color.green(particle.color),
                Color.blue(particle.color)
            )
            canvas.drawCircle(particle.x, particle.y, size, paintCache.particlePaint)
        }
    }

    private fun drawScore(canvas: Canvas) {
        canvas.drawText(
            score.toString(),
            screenWidth / 2f,
            150f,
            paintCache.scorePaint
        )
    }

    private fun drawButtons(canvas: Canvas) {
        paintCache.buttonPaint.color = Color.argb(150, 255, 100, 100)
        canvas.drawRoundRect(backButtonRect, 15f, 15f, paintCache.buttonPaint)
        canvas.drawText("◀", backButtonRect.centerX(), backButtonRect.centerY() + 10f, paintCache.buttonTextPaint)

        if (gameRunning || gamePaused) {
            paintCache.buttonPaint.color = if (gamePaused) Color.argb(150, 100, 255, 100) else Color.argb(150, 255, 255, 100)
            canvas.drawRoundRect(pauseButtonRect, 15f, 15f, paintCache.buttonPaint)
            val symbol = if (gamePaused) "▶" else "⏸"
            canvas.drawText(symbol, pauseButtonRect.centerX(), pauseButtonRect.centerY() + 10f, paintCache.buttonTextPaint)
        }
    }

    private fun drawStartScreen(canvas: Canvas) {
        paintCache.backgroundPaint.color = Color.argb(180, 0, 0, 0)
        canvas.drawRect(0f, 0f, screenWidth.toFloat(), screenHeight.toFloat(), paintCache.backgroundPaint)

        paintCache.textPaint.color = Color.rgb(255, 215, 0)
        paintCache.textPaint.textSize = 80f
        canvas.drawText("FLAPPY BIRD", screenWidth / 2f, screenHeight / 3f, paintCache.textPaint)

        paintCache.textPaint.color = Color.WHITE
        paintCache.textPaint.textSize = 36f
        val startY = screenHeight / 2f
        val lineSpacing = 60f

        canvas.drawText("🐦 Dotknij ekran aby latać", screenWidth / 2f, startY, paintCache.textPaint)
        canvas.drawText("🚫 Unikaj zielonych rur", screenWidth / 2f, startY + lineSpacing, paintCache.textPaint)
        canvas.drawText("🏆 Zdobywaj punkty", screenWidth / 2f, startY + lineSpacing * 2, paintCache.textPaint)

        paintCache.textPaint.color = Color.YELLOW
        paintCache.textPaint.textSize = 48f
        val pulse = (sin(animationFrame * 0.1f) + 1f) * 0.3f + 0.7f
        paintCache.textPaint.alpha = (255 * pulse).toInt()
        canvas.drawText("DOTKNIJ ABY ROZPOCZĄĆ", screenWidth / 2f, screenHeight * 0.8f, paintCache.textPaint)
        paintCache.textPaint.alpha = 255

        drawButtons(canvas)
    }

    private fun drawPauseScreen(canvas: Canvas) {
        paintCache.backgroundPaint.color = Color.argb(150, 0, 0, 0)
        canvas.drawRect(0f, 0f, screenWidth.toFloat(), screenHeight.toFloat(), paintCache.backgroundPaint)

        paintCache.textPaint.color = Color.YELLOW
        paintCache.textPaint.textSize = 72f
        canvas.drawText("PAUZA", screenWidth / 2f, screenHeight / 2f, paintCache.textPaint)

        paintCache.textPaint.textSize = 36f
        canvas.drawText("Naciśnij ▶ aby kontynuować", screenWidth / 2f, screenHeight / 2f + 100f, paintCache.textPaint)
    }

    private fun drawGameOverScreen(canvas: Canvas) {
        paintCache.backgroundPaint.color = Color.argb(200, 0, 0, 0)
        canvas.drawRect(0f, 0f, screenWidth.toFloat(), screenHeight.toFloat(), paintCache.backgroundPaint)

        paintCache.textPaint.color = Color.RED
        paintCache.textPaint.textSize = 72f
        canvas.drawText("GAME OVER", screenWidth / 2f, screenHeight / 2f - 150f, paintCache.textPaint)

        paintCache.textPaint.color = Color.WHITE
        paintCache.textPaint.textSize = 48f
        canvas.drawText("Końcowy wynik: $score", screenWidth / 2f, screenHeight / 2f - 50f, paintCache.textPaint)
        canvas.drawText("Najlepszy wynik: $highScore", screenWidth / 2f, screenHeight / 2f + 20f, paintCache.textPaint)

        if (score == highScore && score > 0) {
            paintCache.textPaint.color = Color.YELLOW
            paintCache.textPaint.textSize = 36f
            val pulse = (sin(animationFrame * 0.2f) + 1f) * 0.3f + 0.7f
            paintCache.textPaint.alpha = (255 * pulse).toInt()
            canvas.drawText("🎉 NOWY REKORD! 🎉", screenWidth / 2f, screenHeight / 2f + 100f, paintCache.textPaint)
            paintCache.textPaint.alpha = 255
        }

        paintCache.textPaint.color = Color.YELLOW
        paintCache.textPaint.textSize = 36f
        canvas.drawText("Dotknij aby zagrać ponownie", screenWidth / 2f, screenHeight / 2f + 200f, paintCache.textPaint)

        drawButtons(canvas)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
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

                when {
                    gameOver -> {
                        if (!backButtonRect.contains(x, y)) {
                            initializeGame()
                            startGame()
                        }
                    }
                    !gameStarted && !gameRunning -> {
                        if (!backButtonRect.contains(x, y)) {
                            startGame()
                        }
                    }
                    gamePaused -> {
                        gamePaused = false
                    }
                    gameRunning -> {
                        jump()
                    }
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun loadHighScore() {
        val sharedPref = activity.getSharedPreferences("FlappyBirdPrefs", Context.MODE_PRIVATE)
        highScore = sharedPref.getInt("highScore", 0)
    }

    private fun saveHighScore() {
        val sharedPref = activity.getSharedPreferences("FlappyBirdPrefs", Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            putInt("highScore", highScore)
            apply()
        }
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
        saveHighScore()
    }
}

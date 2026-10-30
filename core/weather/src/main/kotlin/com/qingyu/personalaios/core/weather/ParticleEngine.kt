package com.qingyu.personalaios.core.weather

import com.qingyu.personalaios.domain.ParticleConfig
import kotlin.math.sin
import kotlin.random.Random

data class ParticleSeed(val x: Float, val y: Float, val depth: Int, val size: Float, val speed: Float, val phase: Float)

/** Allocate once per configuration, evaluate positions in draw phase. No random or paths per frame. */
class ParticleEngine(val config: ParticleConfig, seed: Int = 20261004) {
    init {
        require(config.count in 0..500)
        require(config.minSize > 0 && config.maxSize >= config.minSize)
        require(config.minSpeed >= 0 && config.maxSpeed >= config.minSpeed)
    }
    val particles: List<ParticleSeed> = Random(seed).let { random ->
        List(config.count) { index ->
            val depth = index % 3
            val factor = .32f + depth * .34f
            ParticleSeed(random.nextFloat(), random.nextFloat(), depth,
                config.minSize + (config.maxSize - config.minSize) * factor * random.nextFloat(),
                config.minSpeed + (config.maxSpeed - config.minSpeed) * factor * random.nextFloat(),
                random.nextFloat() * 6.283185f)
        }
    }

    fun x(particle: ParticleSeed, seconds: Float): Float = wrap(
        particle.x + config.windX * seconds * (.4f + particle.depth * .3f) +
            sin(seconds * .32f + particle.phase) * .018f)

    fun y(particle: ParticleSeed, seconds: Float): Float = wrap(particle.y + particle.speed * seconds)

    private fun wrap(value: Float): Float = ((value + .08f) % 1.16f + 1.16f) % 1.16f - .08f
}
